package com.foodmind.foodmind_android.feature.chat

import com.foodmind.foodmind_android.core.network.ApiErrorResponse
import com.google.gson.Gson
import java.io.IOException
import retrofit2.HttpException

private val errorJson = Gson()

internal fun Throwable.requiresLogin(): Boolean = this is HttpException && code() == 401

internal fun Throwable.toChatMessage(fallback: String): String {
    if (this is IOException) return "Check your connection and try again."
    if (this !is HttpException) return fallback

    val apiError = readApiError()
    val reference = apiError?.traceId
        ?: response()?.headers()?.get("X-Correlation-ID")
    val message = when (code()) {
        400 -> when {
            apiError?.code == "MALFORMED_JSON" ||
                apiError?.message.orEmpty().contains("JSON", ignoreCase = true) ->
                "The Backend rejected the chat request format. The app and Backend may be on different versions."
            apiError?.code == "VALIDATION_ERROR" && !apiError.message.isNullOrBlank() -> apiError.message
            else -> "The Backend could not accept this message. Check the text and selected sources."
        }
        401 -> "Your session has expired. Please sign in again."
        403 -> "You no longer have access to this FoodMind source."
        404 -> "This conversation or source is no longer available."
        409 -> when (apiError?.code) {
            "IDEMPOTENCY_CONFLICT" ->
                "This retry no longer matches the original message. Choose Edit and send it as a new message."
            else -> "This message is still being processed. Wait a moment, then retry with the same message."
        }
        422 -> apiError?.message
            ?.takeIf(String::isNotBlank)
            ?: "The message uses an unsupported chat option or source selection."
        429 -> "FoodMind Chat is receiving too many requests. Wait a moment and retry."
        500, 502, 503, 504 -> "FoodMind Chat is temporarily unavailable. Your message is preserved for retry."
        else -> fallback
    }
    return message.withReference(reference)
}

private fun HttpException.readApiError(): ApiErrorResponse? {
    val body = response()?.errorBody()?.string()?.takeIf(String::isNotBlank) ?: return null
    return runCatching { errorJson.fromJson(body, ApiErrorResponse::class.java) }.getOrNull()
}

private fun String.withReference(traceId: String?): String {
    val safeReference = traceId?.trim()?.takeIf(String::isNotBlank)?.take(8) ?: return this
    return "$this Reference: $safeReference."
}
