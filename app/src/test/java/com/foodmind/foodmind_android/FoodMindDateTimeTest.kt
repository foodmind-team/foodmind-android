package com.foodmind.foodmind_android

import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodMindDateTimeTest {
    @Test
    fun formatsIsoTimestampForPeopleInsteadOfShowingWireFormat() {
        val formatted = formatFoodMindTimestamp("2026-08-02T09:05:00Z", Locale.US, ZoneOffset.UTC)

        assertEquals("02/08/2026 09:05", formatted)
        assertFalse(formatted.contains("T"))
        assertFalse(formatted.endsWith("Z"))
    }

    @Test
    fun editorUsesReadableLocalInputAndConvertsItBackToIso() {
        assertEquals("02/08/2026 09:05", formatFoodMindTimestampForEditor("2026-08-02T09:05:00Z", ZoneOffset.UTC))
        assertEquals("2026-08-02T09:05:00Z", normaliseFoodMindTimestamp("02/08/2026 09:05", ZoneOffset.UTC))
    }

    @Test
    fun defaultsToSingaporeTime() {
        assertEquals("02/08/2026 17:05", formatFoodMindTimestampForEditor("2026-08-02T09:05:00Z"))
    }
}
