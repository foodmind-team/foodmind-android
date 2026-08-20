package com.foodmind.foodmind_android

import com.foodmind.foodmind_android.core.network.ApiErrorResponse
import com.foodmind.foodmind_android.core.network.GroupResponse
import com.google.gson.Gson
import java.io.IOException
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import retrofit2.HttpException

internal data class RecordSubmissionFields(
    val price: Double?,
    val currency: String?,
    val rating: Double?,
    val groupId: String?,
)

internal fun selectableRecordGroups(groups: List<GroupResponse>): List<GroupResponse> = groups
    .filter { group ->
        !group.id.isNullOrBlank() &&
            (group.status == null || group.status.equals("ACTIVE", ignoreCase = true))
    }
    .sortedBy { it.name.orEmpty().lowercase(Locale.ROOT) }

internal fun prepareRecordSubmission(
    price: String,
    currency: String,
    rating: String,
    visibility: String,
    groupId: String,
): Result<RecordSubmissionFields> = runCatching {
    val priceValue = price.trim().takeIf(String::isNotEmpty)?.let { value ->
        val amount = value.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Enter a valid price.")
        if (amount < BigDecimal.ZERO || amount.scale() > 2 || amount.precision() - amount.scale() > 8) {
            throw IllegalArgumentException("Price must be positive with no more than two decimal places.")
        }
        amount.toDouble()
    }
    val currencyValue = priceValue?.let {
        val code = currency.trim().uppercase(Locale.ROOT)
        if (code.length != 3 || runCatching { Currency.getInstance(code) }.isFailure) {
            throw IllegalArgumentException("Enter a valid three-letter currency code.")
        }
        code
    }
    val ratingValue = rating.trim().takeIf(String::isNotEmpty)?.let { value ->
        val score = value.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Enter a valid rating.")
        if (score < BigDecimal.ONE || score > BigDecimal("5.0") || score.scale() > 1) {
            throw IllegalArgumentException("Rating must be between 1.0 and 5.0 with no more than one decimal place.")
        }
        score.toDouble()
    }
    val selectedGroupId = if (visibility == "GROUP") {
        groupId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Choose a group for this record.")
    } else {
        null
    }
    RecordSubmissionFields(priceValue, currencyValue, ratingValue, selectedGroupId)
}

internal fun Throwable.toRecordSaveMessage(): String {
    if (this is IOException) return "Check your connection and try again."
    if (this !is HttpException) return message ?: "Could not save. Check your input."

    val apiError = readRecordApiError()
    val fieldMessage = apiError?.fieldErrors
        ?.mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
        ?.distinct()
        ?.joinToString(" ")
    return when (code()) {
        400 -> fieldMessage
            ?: apiError?.message?.takeIf(String::isNotBlank)
            ?: "The record contains invalid values. Check each field and try again."
        401 -> "Your session has expired. Sign in again."
        403 -> "You do not have permission to save this record to the selected group."
        404 -> "The selected meal, place, group, or image is no longer available."
        409 -> "This record changed elsewhere. Reload it before saving again."
        413 -> "The selected image is too large. Choose an image under 5 MB."
        429 -> "Too many save attempts. Wait a moment and try again."
        500, 502, 503, 504 -> "FoodMind is temporarily unavailable. Your form is still here—try again shortly."
        else -> apiError?.message?.takeIf(String::isNotBlank)
            ?: "Could not save the record (HTTP ${code()})."
    }
}

private fun HttpException.readRecordApiError(): ApiErrorResponse? {
    val body = response()?.errorBody()?.string()?.takeIf(String::isNotBlank) ?: return null
    return runCatching { Gson().fromJson(body, ApiErrorResponse::class.java) }.getOrNull()
}
