package com.foodmind.foodmind_android

import com.foodmind.foodmind_android.core.network.RecommendationCandidateResponse
import com.foodmind.foodmind_android.core.network.RecommendationMoneyResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodRecordPrefillTest {
    @Test
    fun `selected recommendation pre-fills the food record fields and identifiers`() {
        val prefill = foodRecordPrefillFrom(
            RecommendationCandidateResponse(
                candidateId = "candidate-1",
                mealId = "meal-1",
                mealName = "Chicken rice",
                placeId = "place-1",
                placeName = "FoodMind Hawker",
                price = RecommendationMoneyResponse(5.5, "SGD"),
            ),
            sessionId = "session-1",
        )

        assertEquals("session-1", prefill.recommendationSessionId)
        assertEquals("candidate-1", prefill.recommendationCandidateId)
        assertEquals("meal-1", prefill.mealId)
        assertEquals("Chicken rice", prefill.mealName)
        assertEquals("place-1", prefill.placeId)
        assertEquals("FoodMind Hawker", prefill.placeName)
        assertEquals("5.5", prefill.price)
        assertEquals("SGD", prefill.currency)
    }

    @Test
    fun `missing recommendation price leaves price blank and defaults currency`() {
        val prefill = foodRecordPrefillFrom(RecommendationCandidateResponse(mealName = "Laksa"))

        assertNull(prefill.mealId)
        assertEquals("Laksa", prefill.mealName)
        assertEquals("", prefill.price)
        assertEquals("SGD", prefill.currency)
    }
}
