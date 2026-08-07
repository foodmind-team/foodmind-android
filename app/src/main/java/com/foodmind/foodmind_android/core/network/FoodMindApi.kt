package com.foodmind.foodmind_android.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query

interface FoodMindApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthTokenResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthTokenResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): AuthTokenResponse

    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshRequest? = null)

    @POST("auth/logout-all")
    suspend fun logoutAll()

    @GET("users/me")
    suspend fun currentUser(): CurrentUserResponse

    @PATCH("users/me")
    suspend fun updateCurrentUser(@Body request: UpdateCurrentUserRequest): CurrentUserResponse

    @GET("users/me/preferences")
    suspend fun preferences(): UserPreferencesResponse

    @PUT("users/me/preferences")
    suspend fun replacePreferences(@Body request: ReplacePreferencesRequest): UserPreferencesResponse

    @GET("catalogue/reference-data")
    suspend fun referenceData(): CatalogueReferenceDataResponse

    @GET("catalogue/meals/{id}")
    suspend fun meal(@Path("id") id: String): CatalogueMealResponse

    @GET("catalogue/places/{id}")
    suspend fun place(@Path("id") id: String): CataloguePlaceResponse

    @GET("catalogue/products/{id}")
    suspend fun product(@Path("id") id: String): CatalogueProductResponse

    @GET("history")
    suspend fun history(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("period") period: String = "DAY",
        @Query("types") types: String? = null,
        @Query("timeZone") timeZone: String? = null,
        @Query("groupId") groupId: String? = null,
        @Query("cuisineId") cuisineId: String? = null,
        @Query("placeId") placeId: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("size") size: Int = 20,
    ): HistoryResponse

    @POST("food-records")
    suspend fun createFoodRecord(@Body request: CreateFoodRecordRequest): FoodRecordResponse

    @GET("food-records")
    suspend fun foodRecords(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("visibility") visibility: String? = null,
        @Query("groupId") groupId: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "occurredAt,desc",
    ): FoodRecordPageResponse

    @GET("food-records/{id}")
    suspend fun foodRecord(@Path("id") id: String): FoodRecordResponse

    @PATCH("food-records/{id}")
    suspend fun updateFoodRecord(@Path("id") id: String, @Header("If-Match") ifMatch: String, @Body request: UpdateFoodRecordRequest): FoodRecordResponse

    @DELETE("food-records/{id}")
    suspend fun deleteFoodRecord(@Path("id") id: String)

    @POST("drink-records")
    suspend fun createDrinkRecord(@Body request: CreateDrinkRecordRequest): DrinkRecordResponse

    @GET("drink-records")
    suspend fun drinkRecords(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("visibility") visibility: String? = null,
        @Query("groupId") groupId: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "occurredAt,desc",
    ): DrinkRecordPageResponse

    @GET("drink-records/{id}")
    suspend fun drinkRecord(@Path("id") id: String): DrinkRecordResponse

    @PATCH("drink-records/{id}")
    suspend fun updateDrinkRecord(@Path("id") id: String, @Header("If-Match") ifMatch: String, @Body request: UpdateDrinkRecordRequest): DrinkRecordResponse

    @DELETE("drink-records/{id}")
    suspend fun deleteDrinkRecord(@Path("id") id: String)

    @GET("explore")
    suspend fun explore(
        @Query("types") types: String? = null,
        @Query("topics") topics: String? = null,
        @Query("after") after: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ExplorePageResponse

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("types") types: String? = null,
        @Query("after") after: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): SearchPageResponse

    @POST("want-to-try")
    suspend fun saveWantToTry(@Body request: SaveWantToTryRequest): WantToTryResponse

    @GET("want-to-try")
    suspend fun wantToTry(@Query("page") page: Int = 0, @Query("size") size: Int = 20): WantToTryPageResponse

    @DELETE("want-to-try/{id}")
    suspend fun deleteWantToTry(@Path("id") id: String)

    @GET("groups")
    suspend fun groups(): List<GroupResponse>

    @POST("groups")
    suspend fun createGroup(@Body request: CreateGroupRequest): GroupResponse

    @GET("groups/{groupId}")
    suspend fun group(@Path("groupId") groupId: String): GroupResponse

    @PATCH("groups/{groupId}")
    suspend fun updateGroup(@Path("groupId") groupId: String, @Body request: UpdateGroupRequest): GroupResponse

    @POST("groups/{groupId}/invitations")
    suspend fun createGroupInvitation(@Path("groupId") groupId: String, @Body request: CreateInvitationRequest): GroupInvitationResponse

    @POST("group-invitations/join")
    suspend fun joinGroup(@Body request: JoinGroupRequest): GroupMemberResponse

    @POST("groups/join")
    suspend fun joinGroupLegacy(@Body request: JoinGroupRequest): GroupMemberResponse

    @GET("groups/{groupId}/members")
    suspend fun groupMembers(@Path("groupId") groupId: String): List<GroupMemberResponse>

    @DELETE("groups/{groupId}/members/{userId}")
    suspend fun removeGroupMember(@Path("groupId") groupId: String, @Path("userId") userId: String)

    @GET("groups/{groupId}/feed")
    suspend fun groupFeed(
        @retrofit2.http.Path("groupId") groupId: String,
        @Query("after") after: String? = null,
        @Query("limit") limit: Int = 20,
    ): GroupFeedResponse

    @POST("groups/{groupId}/recommendation-shares")
    suspend fun shareRecommendation(@Path("groupId") groupId: String, @Body request: ShareRecommendationRequest): GroupRecommendationShareResponse

    @GET("dashboard")
    suspend fun dashboard(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("groupBy") groupBy: String = "DAY",
        @Query("timeZone") timeZone: String? = null,
    ): DashboardResponse

    @GET("weekly-recaps/{weekStart}")
    suspend fun weeklyRecap(@Path("weekStart") weekStart: String): WeeklyRecapResponse

    @POST("chat/sessions")
    suspend fun createChatSession(@Body request: CreateChatSessionRequest): ChatSessionResponse

    @GET("chat/sessions")
    suspend fun chatSessions(@Query("page") page: Int = 0, @Query("size") size: Int = 30): ChatSessionPageResponse

    @GET("chat/sessions/{sessionId}")
    suspend fun chatSession(@Path("sessionId") sessionId: String): ChatSessionResponse

    @DELETE("chat/sessions/{sessionId}")
    suspend fun deleteChatSession(@Path("sessionId") sessionId: String)

    @POST("chat/sessions/{sessionId}/references")
    suspend fun shareChatReference(@Path("sessionId") sessionId: String, @Body request: ShareChatReferenceRequest): ChatReferenceResponse

    @POST("chat/sessions/{sessionId}/messages")
    suspend fun postChatMessage(
        @retrofit2.http.Path("sessionId") sessionId: String,
        @Body request: PostChatMessageRequest,
    ): ChatMessageResponse

    @GET("chat/sessions/{sessionId}/messages")
    suspend fun chatMessages(
        @retrofit2.http.Path("sessionId") sessionId: String,
        @Query("after") after: String? = null,
        @Query("size") size: Int = 20,
    ): ChatPageResponse<ChatMessageResponse>

    @POST("recommendations/generate")
    suspend fun generateRecommendation(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: GenerateRecommendationRequest,
    ): RecommendationResponse

    @GET("recommendations/{sessionId}")
    suspend fun recommendation(@Path("sessionId") sessionId: String): RecommendationResponse

    @POST("recommendations/{sessionId}/feedback")
    suspend fun submitRecommendationFeedback(
        @Path("sessionId") sessionId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: RecommendationFeedbackRequest,
    ): RecommendationFeedbackResponse

    @GET("recommendations/history")
    suspend fun recommendationHistory(@Query("page") page: Int = 0, @Query("size") size: Int = 20): RecommendationHistoryResponse

    @POST("cooking-plans/generate")
    suspend fun generateCookingPlan(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Header("X-Correlation-ID") correlationId: String,
        @Body request: GenerateCookingPlanRequest,
    ): CookingPlanResponse

    @GET("cooking-plans/{planId}")
    suspend fun cookingPlan(@Path("planId") planId: String): CookingPlanResponse

    @POST("cooking-plans/{planId}/decisions")
    suspend fun submitCookingPlanDecisions(
        @Path("planId") planId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body answers: List<CookingQuestionAnswer>,
    ): CookingPlanResponse

    @GET("cooking-plans/history")
    suspend fun cookingPlanHistory(@Query("page") page: Int = 0, @Query("size") size: Int = 20): CookingPlanHistoryResponse

    @POST("cooking-plans/generate-async")
    suspend fun generateCookingPlanAsync(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: GenerateCookingPlanRequest,
    ): retrofit2.Response<CookingPlanAsyncAcceptedResponse>

    @GET("cooking-plans/{planId}/task")
    suspend fun cookingPlanTask(@Path("planId") planId: String): retrofit2.Response<CookingPlanTaskResponse>

    @POST("cooking-plans/{planId}/cancel")
    suspend fun cancelCookingPlanTask(
        @Path("planId") planId: String,
        @Body body: okhttp3.RequestBody,
    ): retrofit2.Response<CookingPlanResponse>

    @POST("media/uploads")
    suspend fun createMediaUpload(@Body request: CreateMediaUploadRequest): MediaUploadInstructionResponse

    @POST("media/{mediaAssetId}/finalise")
    suspend fun finaliseMediaUpload(@Path("mediaAssetId") mediaAssetId: String): MediaAssetResponse

    @DELETE("media/{mediaAssetId}")
    suspend fun deleteMediaAsset(@Path("mediaAssetId") mediaAssetId: String)
}
