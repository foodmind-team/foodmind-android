package com.foodmind.foodmind_android.domain.repository

import android.content.ContentResolver
import android.net.Uri
import com.foodmind.foodmind_android.core.network.CreateMediaUploadRequest
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.io.ByteArrayOutputStream

class MediaUploadRepository(
    private val client: FoodMindApiClient,
    private val http: OkHttpClient = OkHttpClient(),
) {
    suspend fun upload(contentResolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
        val contentType = contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        require(contentType in ALLOWED_TYPES) { "仅支持 JPEG、PNG 或 WebP 图片" }
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_BYTES) { "图片必须小于 5 MB" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: error("无法读取所选图片")
        require(bytes.isNotEmpty()) { "图片不能为空" }
        val checksum = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val instruction = client.createMediaUpload(CreateMediaUploadRequest(contentType, bytes.size.toLong(), checksum))
        val request = Request.Builder().url(instruction.uploadUrl).put(bytes.toRequestBody(contentType.toMediaType())).apply {
            instruction.requiredHeaders.forEach { (name, value) -> header(name, value) }
        }.build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                runCatching { client.deleteMediaAsset(instruction.mediaAssetId) }
                error("图片上传失败（${response.code}）")
            }
        }
        client.finaliseMediaUpload(instruction.mediaAssetId).mediaAssetId
    }

    private companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
        val ALLOWED_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
