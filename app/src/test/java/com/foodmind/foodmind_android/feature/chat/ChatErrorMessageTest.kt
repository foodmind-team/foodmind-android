package com.foodmind.foodmind_android.feature.chat

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ChatErrorMessageTest {

    @Test
    fun malformedJsonExplainsAppBackendVersionMismatchAndKeepsTraceReference() {
        val failure = httpFailure(
            400,
            """{"code":"MALFORMED_JSON","message":"Malformed JSON","traceId":"12345678-abcd"}""",
        )

        val message = failure.toChatMessage("Could not send the message.")

        assertTrue(message.contains("different versions"))
        assertTrue(message.contains("Reference: 12345678"))
    }

    @Test
    fun idempotencyConflictTellsUserToEditBeforeCreatingANewMessage() {
        val failure = httpFailure(
            409,
            """{"code":"IDEMPOTENCY_CONFLICT","message":"Conflict"}""",
        )

        assertEquals(
            "This retry no longer matches the original message. Choose Edit and send it as a new message.",
            failure.toChatMessage("fallback"),
        )
    }

    @Test
    fun inProgressConflictTellsUserToKeepTheSameRetry() {
        val failure = httpFailure(409, """{"code":"CONFLICT","message":"Still processing"}""")

        assertTrue(failure.toChatMessage("fallback").contains("same message"))
    }

    @Test
    fun serviceFailureConfirmsThatDraftIsPreserved() {
        val failure = httpFailure(503, """{"code":"SERVICE_UNAVAILABLE"}""")

        assertEquals(
            "FoodMind Chat is temporarily unavailable. Your message is preserved for retry.",
            failure.toChatMessage("fallback"),
        )
    }

    @Test
    fun ioFailureRetainsExistingConnectionGuidance() {
        assertEquals(
            "Check your connection and try again.",
            IOException("offline").toChatMessage("fallback"),
        )
    }

    private fun httpFailure(status: Int, json: String): HttpException = HttpException(
        Response.error<Any>(
            status,
            json.toResponseBody("application/json".toMediaType()),
        ),
    )
}
