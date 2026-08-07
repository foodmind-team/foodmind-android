package com.foodmind.foodmind_android

import com.foodmind.foodmind_android.core.network.UserPreferencesResponse
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
            area = "Tiong Bahru",
            mood = "COMFORT",
            preferences = UserPreferencesResponse(
                preferredLatitude = 1.284,
                preferredLongitude = 103.832,
                maxDistanceKm = 12.0,
            ),
            requestedFor = "2030-07-30T12:00:00Z",
        )

        assertEquals(1.284, request.latitude!!, 0.0)
        assertEquals(103.832, request.longitude!!, 0.0)
        assertEquals(12.0, request.maxDistanceKm!!, 0.0)
    }

    @Test
    fun `currency is omitted when no budget is provided`() {
        val request = buildHomeRecommendationRequest(
            groupId = "",
            mealType = "DINNER",
            budget = "",
            currency = "SGD",
            area = "",
            mood = "",
            preferences = null,
            requestedFor = "2030-07-30T12:00:00Z",
        )

        assertNull(request.maxBudget)
        assertNull(request.currency)
    }
}
