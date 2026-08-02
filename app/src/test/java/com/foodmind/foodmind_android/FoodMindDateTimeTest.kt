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

        assertTrue(formatted.contains("Aug"))
        assertTrue(formatted.contains("2026"))
        assertFalse(formatted.contains("T"))
        assertFalse(formatted.endsWith("Z"))
    }

    @Test
    fun editorUsesReadableLocalInputAndConvertsItBackToIso() {
        assertEquals("2026-08-02 09:05", formatFoodMindTimestampForEditor("2026-08-02T09:05:00Z", ZoneOffset.UTC))
        assertEquals("2026-08-02T09:05:00Z", normaliseFoodMindTimestamp("2026-08-02 09:05", ZoneOffset.UTC))
    }
}
