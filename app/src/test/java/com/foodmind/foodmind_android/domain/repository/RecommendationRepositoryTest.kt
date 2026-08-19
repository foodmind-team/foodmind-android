package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.core.network.GenerateRecommendationRequest
import com.foodmind.foodmind_android.core.network.RecommendationCandidateResponse
import com.foodmind.foodmind_android.core.network.RecommendationResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationRepositoryTest {
    @Test
    fun mapsFirstCandidateToUiSafeDomainModel() = runTest {
        val repository = RecommendationRepositoryImpl {
            RecommendationResponse(
                sessionId = "session-1",
                status = "SUCCEEDED",
                items = listOf(
                    RecommendationCandidateResponse(
                        mealName = "海南鸡饭",
                        placeName = "FoodMind 厨房",
                        area = "武吉士",
                        explanation = "离你近，且符合预算。",
                    ),
                ),
            )
        }

        val result = repository.generate(GenerateRecommendationRequest())

        assertTrue(result.isSuccess)
        assertEquals("海南鸡饭", result.getOrThrow().title)
        assertEquals("FoodMind 厨房 · 武吉士", result.getOrThrow().meta)
        assertEquals("离你近，且符合预算。", result.getOrThrow().reason)
    }

    @Test
    fun failsWhenTheBackendDoesNotReturnARecommendationSession() = runTest {
        val repository = RecommendationRepositoryImpl { RecommendationResponse(status = "SUCCEEDED") }

        val result = repository.generate(GenerateRecommendationRequest())

        assertTrue(result.isFailure)
    }
}
