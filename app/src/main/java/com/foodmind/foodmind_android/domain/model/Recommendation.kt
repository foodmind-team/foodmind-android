package com.foodmind.foodmind_android.domain.model

data class Recommendation(
    val sessionId: String?,
    val status: String?,
    val title: String,
    val meta: String,
    val reason: String,
)

sealed interface RecommendationFailure {
    data class Http(val statusCode: Int, val message: String?) : RecommendationFailure
    data class Transport(val message: String?) : RecommendationFailure
}
