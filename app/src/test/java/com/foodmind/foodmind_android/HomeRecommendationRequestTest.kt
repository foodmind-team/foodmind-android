package com.foodmind.foodmind_android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRecommendationRequestTest {
    @Test
    fun `saved location is included with the distance constraint`() {
        val request = buildHomeRecommendationRequest(
            groupId = "",
            mealType = "DINNER",
            budget = "25",
            currency = "SGD",
            mood = "COMFORT",
            latitude = 1.284,
            longitude = 103.832,
            maxDistanceKm = 12.0,
            requestedFor = "2030-07-30T12:00:00Z",
        )

        assertEquals(1.284, request.latitude!!, 0.0)
        assertEquals(103.832, request.longitude!!, 0.0)
        assertEquals(12.0, request.maxDistanceKm!!, 0.0)
        assertNull(request.area)
        assertNull(request.constraints)
    }

    @Test
    fun `currency is omitted when no budget is provided`() {
        val request = buildHomeRecommendationRequest(
            groupId = "",
            mealType = "DINNER",
            budget = "",
            currency = "SGD",
            mood = "",
            latitude = null,
            longitude = null,
            maxDistanceKm = null,
            requestedFor = "2030-07-30T12:00:00Z",
        )

        assertNull(request.maxBudget)
        assertNull(request.currency)
    }
}
