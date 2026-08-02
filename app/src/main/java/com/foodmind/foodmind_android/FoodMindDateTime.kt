package com.foodmind.foodmind_android

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val singaporeDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val foodMindLocale: Locale = Locale.forLanguageTag("en-SG")
private val foodMindZoneId: ZoneId = ZoneId.of("Asia/Singapore")

/** Formats backend ISO-8601 timestamps using FoodMind's Singapore locale and time zone. */
fun formatFoodMindTimestamp(
    value: String?,
    locale: Locale = foodMindLocale,
    zoneId: ZoneId = foodMindZoneId,
): String {
    val source = value?.trim().orEmpty()
    if (source.isEmpty()) return "Date unavailable"
    val instant = runCatching { Instant.parse(source) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(source).toInstant() }.getOrNull()
    if (instant != null) {
        return singaporeDateTimeFormat.withLocale(locale).withZone(zoneId).format(instant)
    }
    return runCatching {
        LocalDateTime.parse(source).format(singaporeDateTimeFormat.withLocale(locale))
    }.getOrElse { source }
}

/** Converts an API timestamp into a short, editable Singapore date/time. */
fun formatFoodMindTimestampForEditor(value: String, zoneId: ZoneId = foodMindZoneId): String {
    val instant = runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    return instant?.atZone(zoneId)?.format(singaporeDateTimeFormat) ?: value
}

/** Accepts a Singapore date/time or pasted ISO-8601 timestamp and returns an API value. */
fun normaliseFoodMindTimestamp(value: String, zoneId: ZoneId = foodMindZoneId): String? {
    val source = value.trim()
    return runCatching { Instant.parse(source).toString() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(source).toInstant().toString() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(source, singaporeDateTimeFormat).atZone(zoneId).toInstant().toString() }.getOrNull()
}
