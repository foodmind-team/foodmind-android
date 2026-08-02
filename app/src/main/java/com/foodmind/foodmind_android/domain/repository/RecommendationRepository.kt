package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.core.network.GenerateRecommendationRequest
import com.foodmind.foodmind_android.domain.model.Recommendation
import com.foodmind.foodmind_android.domain.model.RecommendationFailure

interface RecommendationRepository {
    suspend fun generate(request: GenerateRecommendationRequest): Result<Recommendation>
}

class RecommendationRepositoryImpl(
    private val apiClient: suspend (GenerateRecommendationRequest) -> com.foodmind.foodmind_android.core.network.RecommendationResponse,
) : RecommendationRepository {
    override suspend fun generate(request: GenerateRecommendationRequest): Result<Recommendation> = runCatching {
        val response = apiClient(request)
        val candidate = (response.items.ifEmpty { response.candidates }).firstOrNull()
        Recommendation(
            sessionId = response.sessionId,
            status = response.status,
            title = candidate?.mealName ?: "No matching recommendations yet",
            meta = listOfNotNull(candidate?.placeName, candidate?.area).joinToString(" · ").ifBlank { "Please try again later" },
            reason = candidate?.explanation
                ?: candidate?.reasons?.firstOrNull()
                ?: "FoodMind combines group taste, distance, budget, and tonight’s constraints.",
        )
    }.recoverCatching { throwable ->
        throw when (throwable) {
            is retrofit2.HttpException -> RecommendationFailureException(
                RecommendationFailure.Http(throwable.code(), throwable.message()),
            )
            else -> RecommendationFailureException(
                RecommendationFailure.Transport(throwable.message),
            )
        }
    }
}

class RecommendationFailureException(val failure: RecommendationFailure) : RuntimeException()
