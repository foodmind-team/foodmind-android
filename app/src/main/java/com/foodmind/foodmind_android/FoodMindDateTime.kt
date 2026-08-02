package com.foodmind.foodmind_android

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val editorDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** Formats backend ISO-8601 timestamps in the device's local time zone. */
fun formatFoodMindTimestamp(
    value: String?,
    locale: Locale = Locale.getDefault(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val source = value?.trim().orEmpty()
    if (source.isEmpty()) return "Date unavailable"
    val instant = runCatching { Instant.parse(source) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(source).toInstant() }.getOrNull()
    if (instant != null) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(zoneId)
            .format(instant)
    }
    return runCatching {
        LocalDateTime.parse(source).format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale),
        )
    }.getOrElse { source }
}

/** Converts an API timestamp into a short, editable local date/time. */
fun formatFoodMindTimestampForEditor(value: String, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val instant = runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    return instant?.atZone(zoneId)?.format(editorDateTimeFormat) ?: value
}

/** Accepts the editor's local date/time or a pasted ISO-8601 timestamp and returns an API value. */
fun normaliseFoodMindTimestamp(value: String, zoneId: ZoneId = ZoneId.systemDefault()): String? {
    val source = value.trim()
    return runCatching { Instant.parse(source).toString() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(source).toInstant().toString() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(source, editorDateTimeFormat).atZone(zoneId).toInstant().toString() }.getOrNull()
}
