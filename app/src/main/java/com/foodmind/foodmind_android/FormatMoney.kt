package com.foodmind.foodmind_android

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

fun formatMoney(amount: Double?, currency: String?): String {
    if (amount == null || amount == 0.0) return "Price not provided"
    val code = currency?.takeIf(String::isNotBlank) ?: "SGD"
    return try {
        val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-SG"))
        format.currency = Currency.getInstance(code)
        format.format(amount)
    } catch (_: Exception) {
        "$code ${"%.2f".format(amount)}"
    }
}
