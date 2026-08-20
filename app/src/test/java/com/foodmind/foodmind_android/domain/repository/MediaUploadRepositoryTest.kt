package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.core.network.CreateMediaUploadRequest
import com.foodmind.foodmind_android.core.network.MediaAssetResponse
import com.foodmind.foodmind_android.core.network.MediaUploadInstructionResponse
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaUploadRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var client: FakeMediaUploadClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = FakeMediaUploadClient(server.url("signed-upload").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun successfulUploadSendsChecksumWithoutBearerOrManualContentLength() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val bytes = "image".toByteArray()

        val assetId = MediaUploadRepository(client, OkHttpClient()).upload(bytes, "image/jpeg")

        val request = server.takeRequest()
        assertEquals("asset-1", assetId)
        assertEquals("PUT", request.method)
        assertEquals("signed", request.getHeader("X-Test-Signed"))
        assertNull(request.getHeader("Authorization"))
        assertEquals(bytes.size.toString(), request.getHeader("Content-Length"))
        assertEquals("image/jpeg", client.createRequest?.contentType)
        assertEquals(bytes.size.toLong(), client.createRequest?.byteSize)
        assertEquals("6105d6cc76af400325e94d588ce511be5bfdbb73b437dc51eca43917d7a43e3d", client.createRequest?.checksumSha256)
        assertEquals(1, client.finaliseCalls)
        assertTrue(client.deleted.isEmpty())
    }

    @Test
    fun failedPutDeletesPendingAsset() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val failure = runCatching {
            MediaUploadRepository(client).upload("image".toByteArray(), "image/png")
        }.exceptionOrNull()

        assertTrue(failure is MediaUploadException)
        assertTrue(failure?.message.orEmpty().contains("HTTP 503"))
        assertEquals(listOf("asset-1"), client.deleted)
        assertEquals(0, client.finaliseCalls)
    }

    @Test
    fun failedFinaliseDeletesUploadedAsset() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.finaliseFailure = IllegalStateException("checksum mismatch")

        val failure = runCatching {
            MediaUploadRepository(client).upload("image".toByteArray(), "image/webp")
        }.exceptionOrNull()

        assertTrue(failure is MediaUploadException)
        assertEquals(listOf("asset-1"), client.deleted)
        assertEquals(1, client.finaliseCalls)
    }

    @Test
    fun invalidImageIsRejectedBeforeCreatingAsset() = runTest {
        val failure = runCatching {
            MediaUploadRepository(client).upload(byteArrayOf(1), "image/gif")
        }.exceptionOrNull()

        assertTrue(failure is MediaUploadException)
        assertNull(client.createRequest)
        assertTrue(client.deleted.isEmpty())
    }

    @Test
    fun heicBytesAreTranscodedAndUploadedAsJpeg() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val jpegBytes = "normalised-jpeg".toByteArray()

        MediaUploadRepository(client, imageTranscoder = FakeImageTranscoder(jpegBytes)).upload("heic-bytes".toByteArray(), "image/heic")

        assertEquals("image/jpeg", client.createRequest?.contentType)
        assertEquals(jpegBytes.size.toLong(), client.createRequest?.byteSize)
        assertEquals("347f39a5ecc0e34bc2674efeb8e2d8281c6d1f1e9889db75faff8078b3971316", client.createRequest?.checksumSha256)
        assertEquals("normalised-jpeg", server.takeRequest().body.readUtf8())
    }

    @Test
    fun missingMimeTypeIsTranscodedToJpeg() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        MediaUploadRepository(client, imageTranscoder = FakeImageTranscoder("jpeg".toByteArray())).upload("provider-bytes".toByteArray(), null)

        assertEquals("image/jpeg", client.createRequest?.contentType)
    }

    @Test
    fun supportedFormatsPassThroughWithoutTranscoding() = runTest {
        val transcoder = FakeImageTranscoder("should-not-be-used".toByteArray())
        val repository = MediaUploadRepository(client, imageTranscoder = transcoder)

        listOf("image/jpeg", "image/png", "image/webp").forEach { contentType ->
            server.enqueue(MockResponse().setResponseCode(200))
            repository.upload("$contentType-bytes".toByteArray(), contentType)
            assertEquals(contentType, client.createRequest?.contentType)
        }

        assertEquals(0, transcoder.calls)
    }

    @Test
    fun undecodableUnsupportedImageFailsBeforeCreatingAsset() = runTest {
        val failure = runCatching {
            MediaUploadRepository(client, imageTranscoder = FakeImageTranscoder(null)).upload("not-an-image".toByteArray(), "image/heif")
        }.exceptionOrNull()

        assertTrue(failure is MediaUploadException)
        assertTrue(failure?.message.orEmpty().contains("converted to JPEG"))
        assertNull(client.createRequest)
    }

    private class FakeMediaUploadClient(
        private val uploadUrl: String,
    ) : MediaUploadClient {
        var createRequest: CreateMediaUploadRequest? = null
        var finaliseCalls = 0
        var finaliseFailure: Throwable? = null
        val deleted = mutableListOf<String>()

        override suspend fun createMediaUpload(request: CreateMediaUploadRequest): MediaUploadInstructionResponse {
            createRequest = request
            return MediaUploadInstructionResponse(
                mediaAssetId = "asset-1",
                status = "PENDING",
                uploadUrl = uploadUrl,
                requiredHeaders = mapOf(
                    "Content-Type" to request.contentType,
                    "Content-Length" to "999",
                    "Authorization" to "Bearer must-not-leak",
                    "X-Test-Signed" to "signed",
                ),
            )
        }

        override suspend fun finaliseMediaUpload(mediaAssetId: String): MediaAssetResponse {
            finaliseCalls++
            finaliseFailure?.let { throw it }
            return MediaAssetResponse(mediaAssetId = mediaAssetId, status = "READY")
        }

        override suspend fun deleteMediaAsset(mediaAssetId: String) {
            deleted += mediaAssetId
        }
    }

    private class FakeImageTranscoder(
        private val result: ByteArray?,
    ) : ImageTranscoder {
        var calls = 0

        override fun transcodeToJpeg(bytes: ByteArray): ByteArray? {
            calls++
            return result
        }
    }
}
