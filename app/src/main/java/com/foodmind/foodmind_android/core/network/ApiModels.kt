package com.foodmind.foodmind_android.core.network

data class LoginRequest(
    val email: String,
    val password: String,
    val clientType: String = "ANDROID",
    val deviceLabel: String? = null,
)

data class RefreshRequest(
    val refreshToken: String,
    val clientType: String = "ANDROID",
)

data class AuthTokenResponse(
    val userId: String,
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long? = null,
    val expiresAt: String? = null,
    val refreshToken: String? = null,
    val refreshTokenExpiresAt: String? = null,
    val csrfToken: String? = null,
)

data class CurrentUserResponse(
    val id: String? = null,
    val email: String? = null,
    val displayName: String? = null,
)

data class UserRecipePageResponse(
    val items: List<UserRecipeResponse> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
)

data class UserRecipeResponse(
    val id: String? = null,
    val name: String,
    val servings: Int = 2,
    val imageUrl: String? = null,
    val tags: List<String> = emptyList(),
    val allergenHints: List<String> = emptyList(),
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val version: Long = 0,
)

data class UserRecipeRequest(
    val name: String,
    val servings: Int,
    val imageUrl: String? = null,
    val tags: List<String> = emptyList(),
    val allergenHints: List<String> = emptyList(),
    val ingredients: List<String>,
    val steps: List<String>,
)

data class HistoryResponse(
    val fromUtcInclusive: String? = null,
    val toUtcExclusive: String? = null,
    val period: String? = null,
    val timeZone: String? = null,
    val buckets: List<HistoryBucketResponse> = emptyList(),
    val entries: List<HistoryEntryResponse> = emptyList(),
    val nextCursor: String? = null,
)

data class HistoryBucketResponse(
    val bucketStart: String? = null,
    val totalCount: Long = 0,
    val foodCount: Long = 0,
    val drinkCount: Long = 0,
)

data class HistoryEntryResponse(
    val sourceType: String? = null,
    val sourceId: String? = null,
    val occurredAt: String? = null,
    val localBucketStart: String? = null,
    val title: String? = null,
    val context: String? = null,
    val groupId: String? = null,
    val cuisineId: String? = null,
    val placeId: String? = null,
    val rating: Double? = null,
    val repeatIntent: Boolean? = null,
)

data class ExplorePageResponse(
    val items: List<ExploreItemResponse> = emptyList(),
    val nextCursor: String? = null,
    val hasNext: Boolean = false,
)

data class ExploreItemResponse(
    val sourceType: String? = null,
    val sourceId: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val snippet: String? = null,
    val imageReference: String? = null,
    val visibility: String? = null,
    val ownerUserId: String? = null,
    val groupId: String? = null,
    val occurredAt: String? = null,
)

data class GroupResponse(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val createdByUserId: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val version: Long = 0,
)

data class GroupFeedResponse(
    val items: List<GroupFeedItemResponse> = emptyList(),
    val nextCursor: String? = null,
)

data class GroupFeedItemResponse(
    val sourceType: String? = null,
    val sourceId: String? = null,
    val occurredAt: String? = null,
    val actorUserId: String? = null,
    val actorDisplayName: String? = null,
    val foodRecordId: String? = null,
    val mealNameSnapshot: String? = null,
    val recommendationShareId: String? = null,
    val recommendationCandidateId: String? = null,
    val message: String? = null,
)

data class DashboardResponse(
    val from: String? = null,
    val to: String? = null,
    val groupBy: String? = null,
    val timeZone: String? = null,
    val empty: Boolean = true,
    val metrics: List<DashboardMetricResponse> = emptyList(),
    val spendingTotals: List<DashboardMetricResponse> = emptyList(),
)

data class DashboardMetricResponse(
    val code: String? = null,
    val label: String? = null,
    val period: String? = null,
    val value: Double? = null,
    val unit: String? = null,
    val currency: String? = null,
    val samples: Long? = null,
    val denominator: Long? = null,
    val empty: Boolean = false,
    val dimension: String? = null,
    val dimensionLabel: String? = null,
)

data class CreateChatSessionRequest(val title: String? = null)

data class ChatSessionResponse(
    val id: String? = null,
    val title: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class ChatPageResponse<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
    val hasNext: Boolean = false,
)

data class PostChatMessageRequest(
    val content: String,
    val referenceIds: List<String>? = null,
    val route: String? = null,
)

data class ChatMessageResponse(
    val id: String? = null,
    val sessionId: String? = null,
    val role: String? = null,
    val content: String? = null,
    val route: String? = null,
    val responseStatus: String? = null,
    val correlationId: String? = null,
    val agentTraceId: String? = null,
    val createdAt: String? = null,
    val sources: List<ChatMessageSourceResponse> = emptyList(),
)

data class ChatMessageSourceResponse(
    val referenceId: String? = null,
    val sourceType: String? = null,
    val sourceId: String? = null,
    val sequenceNo: Int? = null,
    val title: String? = null,
    val snippet: String? = null,
)

data class GenerateRecommendationRequest(
    val parentSessionId: String? = null,
    val groupId: String? = null,
    val mealType: String? = "dinner",
    val maxBudget: Double? = null,
    val currency: String? = "CNY",
    val area: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val maxDistanceKm: Double? = null,
    val mood: String? = null,
    val requestedFor: String? = null,
    val constraints: RecommendationConstraintsRequest? = null,
)

data class RecommendationConstraintsRequest(
    val avoidAllergenCodes: List<String>? = null,
    val requiredDietaryTagCodes: List<String>? = null,
    val maxSpiceLevel: Int? = null,
    val minimumCleanlinessEvidenceScore: Int? = null,
)

data class RecommendationResponse(
    val sessionId: String? = null,
    val traceId: String? = null,
    val status: String? = null,
    val modelStatus: String? = null,
    val fallbackStatus: String? = null,
    val items: List<RecommendationCandidateResponse> = emptyList(),
    val candidates: List<RecommendationCandidateResponse> = emptyList(),
)

data class RecommendationCandidateResponse(
    val candidateId: String? = null,
    val mealName: String? = null,
    val placeName: String? = null,
    val area: String? = null,
    val recommendationType: String? = null,
    val rank: Int? = null,
    val reasonCodes: List<String> = emptyList(),
    val reasons: List<String> = emptyList(),
    val explanation: String? = null,
)

data class CookingIngredientRequest(
    val ingredientName: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val source: String? = null,
)

data class GenerateCookingPlanRequest(
    val ingredients: List<CookingIngredientRequest> = emptyList(),
    val recipeIds: List<String> = emptyList(),
    val servings: Int = 2,
    val maxMinutes: Int = 30,
    val maxBudget: Double? = null,
    val currency: String = "CNY",
    val requiredDietaryTagCodes: List<String> = emptyList(),
    val avoidAllergenCodes: List<String> = emptyList(),
)

data class CookingPlanResponse(
    val planId: String? = null,
    val traceId: String? = null,
    val status: String,
    val sourceRecipeId: String? = null,
    val agentContractVersion: String? = null,
    val fallbackStatus: String? = null,
    val fallbackVersion: String? = null,
    val failureCode: String? = null,
    val inputs: List<CookingPlanInputResponse> = emptyList(),
    val ingredients: List<CookingIngredientResponse> = emptyList(),
    val steps: List<CookingStepResponse> = emptyList(),
    val warnings: List<CookingPlanWarningResponse> = emptyList(),
)

data class CookingStepResponse(
    val stepNo: Int? = null,
    val instruction: String,
)

data class CookingIngredientResponse(
    val sequenceNo: Int? = null,
    val ingredientName: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val availability: String? = null,
)

data class CookingPlanInputResponse(
    val sequenceNo: Int? = null,
    val ingredientName: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val source: String? = null,
)

data class CookingPlanWarningResponse(
    val sequenceNo: Int? = null,
    val warningCode: String? = null,
    val message: String? = null,
)

data class ApiFieldError(
    val field: String? = null,
    val message: String? = null,
)

data class ApiErrorResponse(
    val timestamp: String? = null,
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
    val path: String? = null,
    val traceId: String? = null,
    val fieldErrors: List<ApiFieldError> = emptyList(),
)
