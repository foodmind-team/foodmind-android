package com.foodmind.foodmind_android.core.network

data class LoginRequest(
    val email: String,
    val password: String,
    val clientType: String = "ANDROID",
    val deviceLabel: String? = null,
)

data class RegisterRequest(
    val email: String,
    val displayName: String,
    val password: String,
    val timeZone: String? = null,
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
    val role: String? = null,
    val status: String? = null,
    val timeZone: String? = null,
    val version: Long = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class UpdateCurrentUserRequest(
    val displayName: String? = null,
    val timeZone: String? = null,
)

data class AllergenPreferenceRequest(val code: String, val severity: String)
data class AllergenPreference(val code: String, val severity: String)

data class ReplacePreferencesRequest(
    val budgetMin: Double? = null,
    val budgetMax: Double? = null,
    val currency: String? = null,
    val spiceTolerance: Int? = null,
    val preferredArea: String? = null,
    val preferredLatitude: Double? = null,
    val preferredLongitude: Double? = null,
    val maxDistanceKm: Double? = null,
    val cleanlinessPriority: Int? = null,
    val minimumCleanlinessEvidenceScore: Double? = null,
    val foodGoal: String? = null,
    val drinkSweetnessPreference: String? = null,
    val drinkIcePreference: String? = null,
    val likedCuisineCodes: List<String> = emptyList(),
    val dislikedCuisineCodes: List<String> = emptyList(),
    val dietaryTagCodes: List<String> = emptyList(),
    val allergens: List<AllergenPreferenceRequest> = emptyList(),
    val preferredMealTypes: List<String> = emptyList(),
)

data class UserPreferencesResponse(
    val budgetMin: Double? = null,
    val budgetMax: Double? = null,
    val currency: String? = null,
    val spiceTolerance: Int? = null,
    val preferredArea: String? = null,
    val preferredLatitude: Double? = null,
    val preferredLongitude: Double? = null,
    val maxDistanceKm: Double? = null,
    val cleanlinessPriority: Int? = null,
    val minimumCleanlinessEvidenceScore: Double? = null,
    val foodGoal: String? = null,
    val drinkSweetnessPreference: String? = null,
    val drinkIcePreference: String? = null,
    val likedCuisineCodes: List<String> = emptyList(),
    val dislikedCuisineCodes: List<String> = emptyList(),
    val dietaryTagCodes: List<String> = emptyList(),
    val allergens: List<AllergenPreference> = emptyList(),
    val preferredMealTypes: List<String> = emptyList(),
)

data class CatalogueReferenceItem(val id: String, val code: String, val name: String)
data class CatalogueReferenceDataResponse(
    val cuisines: List<CatalogueReferenceItem> = emptyList(),
    val dietaryTags: List<CatalogueReferenceItem> = emptyList(),
    val allergens: List<CatalogueReferenceItem> = emptyList(),
    val mealTypes: List<String> = emptyList(),
    val placeTypes: List<String> = emptyList(),
)
data class CatalogueMoney(val amount: Double = 0.0, val currency: String = "")
data class CataloguePlaceSummary(val id: String = "", val name: String = "", val area: String = "", val placeType: String = "")
data class CatalogueMealOffering(
    val id: String = "", val displayName: String = "", val price: CatalogueMoney = CatalogueMoney(),
    val spiceLevel: Int? = null, val availabilityNote: String? = null, val place: CataloguePlaceSummary = CataloguePlaceSummary(),
)
data class CatalogueMealResponse(
    val id: String = "", val name: String = "", val description: String? = null,
    val cuisine: CatalogueReferenceItem? = null, val mealType: String = "", val defaultSpiceLevel: Int? = null,
    val dietaryTagCodes: List<String> = emptyList(), val allergenCodes: List<String> = emptyList(),
    val offerings: List<CatalogueMealOffering> = emptyList(),
)
data class CatalogueCoordinates(val latitude: Double = 0.0, val longitude: Double = 0.0)
data class CataloguePlaceObservation(
    val id: String = "", val observationType: String = "", val score: Double = 0.0,
    val note: String? = null, val sourceKind: String = "", val observedAt: String = "",
)
data class CataloguePlaceOffering(
    val id: String = "", val displayName: String = "", val price: CatalogueMoney = CatalogueMoney(),
    val spiceLevel: Int? = null, val availabilityNote: String? = null, val mealId: String = "",
    val mealName: String = "", val mealType: String = "", val cuisineCode: String = "",
)
data class CataloguePlaceResponse(
    val id: String = "", val name: String = "", val placeType: String = "", val area: String = "",
    val addressText: String? = null, val coordinates: CatalogueCoordinates? = null, val priceBand: Int? = null,
    val observations: List<CataloguePlaceObservation> = emptyList(), val offerings: List<CataloguePlaceOffering> = emptyList(),
)
data class CatalogueProductResponse(
    val id: String = "", val name: String = "", val brand: String? = null, val description: String? = null,
    val price: CatalogueMoney? = null, val place: CataloguePlaceSummary? = null,
    val dietaryTagCodes: List<String> = emptyList(), val allergenCodes: List<String> = emptyList(),
)

data class HistoryResponse(
    val fromUtcInclusive: String? = null,
    val toUtcExclusive: String? = null,
    val period: String? = null,
    val types: List<String> = emptyList(),
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

data class FoodRecordMoney(val amount: Double = 0.0, val currency: String = "")

data class CreateFoodRecordRequest(
    val mealId: String? = null,
    val mealNameSnapshot: String,
    val placeId: String? = null,
    val placeNameSnapshot: String? = null,
    val cuisineId: String? = null,
    val occurredAt: String,
    val price: Double? = null,
    val currency: String? = null,
    val rating: Double? = null,
    val comment: String? = null,
    val wouldEatAgain: Boolean? = null,
    val visibility: String? = null,
    val groupId: String? = null,
    val mediaAssetId: String? = null,
)

data class UpdateFoodRecordRequest(
    val mealId: String? = null,
    val mealNameSnapshot: String? = null,
    val placeId: String? = null,
    val placeNameSnapshot: String? = null,
    val cuisineId: String? = null,
    val occurredAt: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val rating: Double? = null,
    val comment: String? = null,
    val wouldEatAgain: Boolean? = null,
    val visibility: String? = null,
    val groupId: String? = null,
    val mediaAssetId: String? = null,
)

data class FoodRecordResponse(
    val id: String = "",
    val mealId: String? = null,
    val mealNameSnapshot: String = "",
    val placeId: String? = null,
    val placeNameSnapshot: String? = null,
    val cuisineId: String? = null,
    val cuisineCode: String? = null,
    val cuisineName: String? = null,
    val occurredAt: String = "",
    val price: FoodRecordMoney? = null,
    val rating: Double? = null,
    val comment: String? = null,
    val wouldEatAgain: Boolean? = null,
    val visibility: String = "PRIVATE",
    val groupId: String? = null,
    val mediaAssetId: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val version: Long = 0,
)

data class FoodRecordPageResponse(
    val items: List<FoodRecordResponse> = emptyList(), val page: Int = 0, val size: Int = 20,
    val totalItems: Long = 0, val totalPages: Int = 0, val hasNext: Boolean = false,
)

data class CreateDrinkRecordRequest(
    val drinkName: String,
    val placeId: String? = null,
    val shopNameSnapshot: String,
    val occurredAt: String,
    val price: Double? = null,
    val currency: String? = null,
    val rating: Double? = null,
    val comment: String? = null,
    val sweetnessLevel: Int? = null,
    val iceLevel: Int? = null,
    val wouldBuyAgain: Boolean? = null,
    val visibility: String? = null,
    val groupId: String? = null,
    val mediaAssetId: String? = null,
)

data class UpdateDrinkRecordRequest(
    val drinkName: String? = null,
    val placeId: String? = null,
    val shopNameSnapshot: String? = null,
    val occurredAt: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val rating: Double? = null,
    val comment: String? = null,
    val sweetnessLevel: Int? = null,
    val iceLevel: Int? = null,
    val wouldBuyAgain: Boolean? = null,
    val visibility: String? = null,
    val groupId: String? = null,
    val mediaAssetId: String? = null,
)

data class DrinkRecordResponse(
    val id: String = "", val drinkName: String = "", val placeId: String? = null,
    val shopNameSnapshot: String = "", val occurredAt: String = "", val price: FoodRecordMoney? = null,
    val rating: Double? = null, val comment: String? = null, val sweetnessLevel: Int? = null,
    val iceLevel: Int? = null, val wouldBuyAgain: Boolean? = null, val visibility: String = "PRIVATE",
    val groupId: String? = null, val mediaAssetId: String? = null, val createdAt: String = "",
    val updatedAt: String = "", val version: Long = 0,
)

data class DrinkRecordPageResponse(
    val items: List<DrinkRecordResponse> = emptyList(), val page: Int = 0, val size: Int = 20,
    val totalItems: Long = 0, val totalPages: Int = 0, val hasNext: Boolean = false,
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

data class SearchPageResponse(
    val items: List<ExploreItemResponse> = emptyList(),
    val nextCursor: String? = null,
    val hasNext: Boolean = false,
)

data class SaveWantToTryRequest(val sourceType: String, val sourceId: String, val note: String? = null)
data class WantToTrySourceSummary(
    val title: String? = null, val subtitle: String? = null, val snippet: String? = null,
    val imageReference: String? = null, val visibility: String? = null, val ownerUserId: String? = null,
    val groupId: String? = null, val occurredAt: String? = null,
)
data class WantToTryResponse(
    val id: String = "", val sourceType: String = "", val sourceId: String = "", val note: String? = null,
    val createdAt: String = "", val sourceAvailable: Boolean = false, val source: WantToTrySourceSummary? = null,
)
data class WantToTryPageResponse(
    val items: List<WantToTryResponse> = emptyList(), val page: Int = 0, val size: Int = 20,
    val totalItems: Long = 0, val totalPages: Int = 0, val hasNext: Boolean = false,
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

data class CreateGroupRequest(val name: String, val description: String? = null)
data class UpdateGroupRequest(val name: String? = null, val description: String? = null, val status: String? = null)
data class CreateInvitationRequest(val expiresInMinutes: Int? = null, val expiresInHours: Int? = null, val maxUses: Int? = null)
data class GroupInvitationResponse(
    val id: String? = null, val groupId: String? = null, val token: String? = null,
    val expiresAt: String? = null, val maxUses: Int? = null, val useCount: Int? = null, val status: String? = null,
)
data class JoinGroupRequest(val token: String)
data class GroupMemberResponse(
    val userId: String? = null, val displayName: String? = null, val role: String? = null, val joinedAt: String? = null,
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

data class ShareRecommendationRequest(val recommendationCandidateId: String, val message: String? = null)
data class GroupRecommendationShareResponse(
    val id: String? = null, val groupId: String? = null, val sharedByUserId: String? = null,
    val recommendationCandidateId: String? = null, val message: String? = null, val createdAt: String? = null,
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

data class WeeklyRecapResponse(
    val weekStart: String? = null,
    val timeZone: String? = null,
    val empty: Boolean = true,
    val metrics: List<DashboardMetricResponse> = emptyList(),
    val spendingTotals: List<DashboardMetricResponse> = emptyList(),
)

data class CreateChatSessionRequest(val title: String? = null)

data class ChatSessionResponse(
    val id: String? = null,
    val title: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class ChatSessionPageResponse(
    val items: List<ChatSessionResponse> = emptyList(), val nextCursor: String? = null, val hasNext: Boolean = false,
)

data class ShareChatReferenceRequest(val sourceType: String, val sourceId: String)
data class ChatReferenceResponse(
    val id: String? = null, val origin: String? = null, val introducedByMessageId: String? = null,
    val sourceType: String? = null, val sourceId: String? = null, val available: Boolean = false,
    val title: String? = null, val snippet: String? = null, val createdAt: String? = null,
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
    val minimumCleanlinessEvidenceScore: Double? = null,
)

data class RecommendationResponse(
    val sessionId: String? = null,
    val traceId: String? = null,
    val status: String? = null,
    val modelStatus: String? = null,
    val fallbackStatus: String? = null,
    val items: List<RecommendationCandidateResponse> = emptyList(),
    val candidates: List<RecommendationCandidateResponse> = emptyList(),
    val modelVersion: String? = null,
    val fallbackVersion: String? = null,
    val createdAt: String? = null,
    val completedAt: String? = null,
)

data class RecommendationCandidateResponse(
    val candidateId: String? = null,
    val placeMealId: String? = null,
    val mealId: String? = null,
    val mealName: String? = null,
    val placeId: String? = null,
    val placeName: String? = null,
    val area: String? = null,
    val price: RecommendationMoneyResponse? = null,
    val recommendationType: String? = null,
    val rank: Int? = null,
    val reasonCodes: List<String> = emptyList(),
    val reasons: List<String> = emptyList(),
    val explanation: String? = null,
)

data class RecommendationMoneyResponse(val amount: Double = 0.0, val currency: String = "")

data class RecommendationFeedbackRequest(
    val candidateId: String? = null,
    val eventType: String,
    val reasonCode: String? = null,
    val rating: Double? = null,
    val booleanValue: Boolean? = null,
    val resultingFoodRecordId: String? = null,
    val effectiveUntil: String? = null,
)
data class RecommendationFeedbackResponse(
    val feedbackId: String? = null, val sessionId: String? = null, val candidateId: String? = null,
    val eventType: String? = null, val reasonCode: String? = null, val rating: Double? = null,
    val booleanValue: Boolean? = null, val resultingFoodRecordId: String? = null,
    val effectiveUntil: String? = null, val createdAt: String? = null, val supervisedLabel: Int? = null,
)
data class RecommendationSessionSummary(
    val sessionId: String? = null, val groupId: String? = null, val status: String? = null,
    val fallbackStatus: String? = null, val fallbackVersion: String? = null,
    val returnedCandidateCount: Int = 0, val createdAt: String? = null, val completedAt: String? = null,
)
data class RecommendationHistoryResponse(
    val items: List<RecommendationSessionSummary> = emptyList(), val page: Int = 0, val size: Int = 20,
    val totalItems: Long = 0, val totalPages: Int = 0, val hasNext: Boolean = false,
)

data class CookingIngredientRequest(
    val ingredientName: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val source: String? = null,
)

data class GenerateCookingPlanRequest(
    val ingredients: List<CookingIngredientRequest> = emptyList(),
    val servings: Int = 2,
    val maxMinutes: Int? = null,
    val maxBudget: Double? = null,
    val currency: String? = null,
    val requiredDietaryTagCodes: List<String> = emptyList(),
    val avoidAllergenCodes: List<String> = emptyList(),
    val recipeIds: List<String>? = null,
    val servingAt: String? = null,
    val region: String? = null,
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
    val createdAt: String? = null,
    val completedAt: String? = null,
    val inputs: List<CookingPlanInputResponse> = emptyList(),
    val ingredients: List<CookingIngredientResponse> = emptyList(),
    val steps: List<CookingStepResponse> = emptyList(),
    val warnings: List<CookingPlanWarningResponse> = emptyList(),
    val planRevision: String? = null,
    val region: String? = null,
    val solverStatus: String? = null,
    val makespanMinutes: Int? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val sources: List<CookingPlanSourceResponse> = emptyList(),
    val timeline: List<CookingPlanTimelineTaskResponse> = emptyList(),
    val miseEnPlace: List<CookingPlanMiseEnPlaceResponse> = emptyList(),
    val dishCompletions: List<CookingPlanDishCompletionResponse> = emptyList(),
    val completionChecklist: List<CookingPlanCompletionItemResponse> = emptyList(),
    val assumptions: List<CookingPlanAssumptionResponse> = emptyList(),
    val repairOptions: List<CookingPlanRepairOptionResponse> = emptyList(),
    val questions: List<String> = emptyList(),
    val confirmationQuestions: List<CookingPlanConfirmationQuestionResponse> = emptyList(),
    val decisions: List<CookingPlanDecisionResponse> = emptyList(),
    val reasons: List<String> = emptyList(),
    val safeAlternatives: List<String> = emptyList(),
    val explanation: String? = null,
    val explanationSource: String? = null,
)

data class CookingPlanSourceResponse(
    val sequenceNo: Int? = null,
    val sourceType: String? = null,
    val sourceId: String? = null,
    val targetServings: Double? = null,
    val dishName: String? = null,
)

data class CookingPlanTimelineTaskResponse(
    val taskId: String? = null,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val durationMinutes: Int? = null,
    val instruction: String? = null,
    val dishId: String? = null,
    val workMode: String? = null,
    val category: String? = null,
    val heatLevel: String? = null,
    val resources: List<String> = emptyList(),
)

data class CookingPlanMiseEnPlaceResponse(
    val sequenceNo: Int? = null,
    val instruction: String? = null,
    val ingredient: String? = null,
    val operation: String? = null,
    val durationMinutes: Int? = null,
    val whenNeeded: String? = null,
)

data class CookingPlanDishCompletionResponse(
    val dishId: String? = null,
    val completionMinute: Int? = null,
    val taskCount: Int? = null,
    val isShared: Boolean = false,
)

data class CookingPlanLotAllocationResponse(
    val inventoryLotId: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
)

data class CookingPlanCompletionItemResponse(
    val completionItemId: String? = null,
    val ingredientName: String? = null,
    val recipeIds: List<String> = emptyList(),
    val allocations: List<CookingPlanLotAllocationResponse> = emptyList(),
)

data class CookingPlanAssumptionResponse(
    val text: String? = null,
    val confidence: Double? = null,
    val sourceType: String? = null,
)

data class CookingPlanRepairOptionResponse(
    val optionId: String? = null,
    val optionType: String? = null,
    val description: String? = null,
    val changes: List<String> = emptyList(),
    val effects: List<String> = emptyList(),
)

data class CookingPlanQuestionOptionResponse(
    val value: String? = null,
    val label: String? = null,
    val suggested: Boolean = false,
)

data class CookingPlanConfirmationQuestionResponse(
    val questionId: String? = null,
    val fieldPath: String? = null,
    val prompt: String? = null,
    val responseType: String? = null,
    val options: List<CookingPlanQuestionOptionResponse> = emptyList(),
    val required: Boolean = true,
    val suggestedValue: String? = null,
)

data class CookingPlanDecisionResponse(
    val optionId: String? = null,
    val optionType: String? = null,
    val payload: Map<String, Any?> = emptyMap(),
    val planRevision: String? = null,
)

data class CookingQuestionAnswer(
    val questionId: String,
    val value: String,
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

data class CookingPlanSummary(
    val planId: String? = null, val status: String? = null, val sourceRecipeId: String? = null,
    val inputCount: Int = 0, val stepCount: Int = 0, val createdAt: String? = null, val completedAt: String? = null,
)
data class CookingPlanHistoryResponse(
    val items: List<CookingPlanSummary> = emptyList(), val page: Int = 0, val size: Int = 20,
    val totalItems: Long = 0, val totalPages: Int = 0, val hasNext: Boolean = false,
)

data class CookingPlanAsyncAcceptedResponse(
    val planId: String? = null,
    val status: String? = null,
    val taskId: String? = null,
    val location: String? = null,
)

data class CookingPlanTaskProgressResponse(
    val node: String? = null,
    val completedSteps: Int = 0,
    val message: String? = null,
)

data class CookingPlanTaskResponse(
    val planId: String? = null,
    val taskId: String? = null,
    val status: String? = null,
    val syncState: String? = null,
    val progress: CookingPlanTaskProgressResponse? = null,
)

data class CreateMediaUploadRequest(val contentType: String, val byteSize: Long, val checksumSha256: String)
data class MediaUploadInstructionResponse(
    val mediaAssetId: String = "", val status: String = "", val uploadUrl: String = "",
    val requiredHeaders: Map<String, String> = emptyMap(), val expiresAt: String = "",
)
data class MediaAssetResponse(
    val mediaAssetId: String = "", val status: String = "", val contentType: String = "",
    val byteSize: Long = 0, val createdAt: String = "", val finalisedAt: String? = null,
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
