package com.foodmind.foodmind_android.domain.repository

import android.content.ContentResolver
import android.net.Uri
import com.foodmind.foodmind_android.core.network.CreateMediaUploadRequest
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.MediaAssetResponse
import com.foodmind.foodmind_android.core.network.MediaUploadInstructionResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

internal interface MediaUploadClient {
    suspend fun createMediaUpload(request: CreateMediaUploadRequest): MediaUploadInstructionResponse
    suspend fun finaliseMediaUpload(mediaAssetId: String): MediaAssetResponse
    suspend fun deleteMediaAsset(mediaAssetId: String)
}

private class FoodMindMediaUploadClient(
    private val client: FoodMindApiClient,
) : MediaUploadClient {
    override suspend fun createMediaUpload(request: CreateMediaUploadRequest) = client.createMediaUpload(request)
    override suspend fun finaliseMediaUpload(mediaAssetId: String) = client.finaliseMediaUpload(mediaAssetId)
    override suspend fun deleteMediaAsset(mediaAssetId: String) = client.deleteMediaAsset(mediaAssetId)
}

class MediaUploadRepository internal constructor(
    private val client: MediaUploadClient,
    private val http: OkHttpClient = OkHttpClient(),
) {
    constructor(
        client: FoodMindApiClient,
        http: OkHttpClient = OkHttpClient(),
    ) : this(FoodMindMediaUploadClient(client), http)

    suspend fun upload(contentResolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
        val contentType = contentResolver.getType(uri)?.lowercase()
            ?: throw MediaUploadException("Could not determine the selected image type.")
        val bytes = contentResolver.openInputStream(uri)?.use(::readImageBytes)
            ?: throw MediaUploadException("Could not read the selected image.")
        upload(bytes, contentType)
    }

    internal suspend fun upload(bytes: ByteArray, contentType: String): String = withContext(Dispatchers.IO) {
        validateImage(bytes, contentType)
        val checksum = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val instruction = try {
            client.createMediaUpload(
                CreateMediaUploadRequest(contentType, bytes.size.toLong(), checksum),
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            throw MediaUploadException("Could not prepare the image upload.", failure)
        }

        try {
            val request = Request.Builder()
                .url(instruction.uploadUrl)
                .put(bytes.toRequestBody(contentType.toMediaType()))
                .apply {
                    instruction.requiredHeaders
                        .filterKeys { name -> name.lowercase() !in FORBIDDEN_SIGNED_UPLOAD_HEADERS }
                        .forEach { (name, value) -> header(name, value) }
                }
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw MediaUploadException("Image upload failed with HTTP " + response.code + ".")
                }
            }
            val asset = client.finaliseMediaUpload(instruction.mediaAssetId)
            if (asset.status != "READY") {
                throw MediaUploadException("The uploaded image could not be verified.")
            }
            asset.mediaAssetId
        } catch (failure: Throwable) {
            cleanup(instruction.mediaAssetId)
            when (failure) {
                is CancellationException -> throw failure
                is MediaUploadException -> throw failure
                else -> throw MediaUploadException("The image upload could not be completed.", failure)
            }
        }
    }

    private fun readImageBytes(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BYTES) throw MediaUploadException("Images must be 5 MB or smaller.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun validateImage(bytes: ByteArray, contentType: String) {
        if (contentType !in ALLOWED_TYPES) {
            throw MediaUploadException("Only JPEG, PNG, or WebP images are supported.")
        }
        if (bytes.isEmpty()) throw MediaUploadException("The image cannot be empty.")
        if (bytes.size > MAX_BYTES) throw MediaUploadException("Images must be 5 MB or smaller.")
    }

    private suspend fun cleanup(mediaAssetId: String) = withContext(NonCancellable) {
        runCatching { client.deleteMediaAsset(mediaAssetId) }
    }

    private companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
        val ALLOWED_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        val FORBIDDEN_SIGNED_UPLOAD_HEADERS = setOf("authorization", "content-length")
    }
}

class MediaUploadException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
