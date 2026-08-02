package com.foodmind.foodmind_android.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query

interface FoodMindApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthTokenResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): AuthTokenResponse

    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshRequest? = null)

    @GET("users/me")
    suspend fun currentUser(): CurrentUserResponse

    @GET("recipes")
    suspend fun recipes(@Query("page") page: Int = 0, @Query("size") size: Int = 100): UserRecipePageResponse

    @GET("recipes/{id}")
    suspend fun recipe(@Path("id") id: String): UserRecipeResponse

    @POST("recipes")
    suspend fun createRecipe(@Body request: UserRecipeRequest): UserRecipeResponse

    @PUT("recipes/{id}")
    suspend fun updateRecipe(@Path("id") id: String, @Header("If-Match") ifMatch: String, @Body request: UserRecipeRequest): UserRecipeResponse

    @DELETE("recipes/{id}")
    suspend fun deleteRecipe(@Path("id") id: String)

    @GET("history")
    suspend fun history(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("period") period: String = "DAY",
        @Query("size") size: Int = 20,
    ): HistoryResponse

    @GET("explore")
    suspend fun explore(
        @Query("types") types: String? = null,
        @Query("topics") topics: String? = null,
        @Query("after") after: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ExplorePageResponse

    @GET("groups")
    suspend fun groups(): List<GroupResponse>

    @GET("groups/{groupId}/feed")
    suspend fun groupFeed(
        @retrofit2.http.Path("groupId") groupId: String,
        @Query("after") after: String? = null,
        @Query("limit") limit: Int = 20,
    ): GroupFeedResponse

    @GET("dashboard")
    suspend fun dashboard(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("groupBy") groupBy: String = "DAY",
        @Query("timeZone") timeZone: String? = null,
    ): DashboardResponse

    @POST("chat/sessions")
    suspend fun createChatSession(@Body request: CreateChatSessionRequest): ChatSessionResponse

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

    @POST("cooking-plans/generate")
    suspend fun generateCookingPlan(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Header("X-Correlation-ID") correlationId: String,
        @Body request: GenerateCookingPlanRequest,
    ): CookingPlanResponse
}
