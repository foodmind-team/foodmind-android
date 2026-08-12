package com.foodmind.foodmind_android.core.network

import okhttp3.Interceptor
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import kotlinx.coroutines.runBlocking
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

interface SessionTokenStore {
    fun accessToken(): String?
    fun saveAccessToken(token: String)
    fun refreshToken(): String? = null
    fun saveRefreshToken(token: String) = Unit
    fun userId(): String? = null
    fun saveUserId(userId: String) = Unit
    fun clear()
}

class InMemorySessionTokenStore : SessionTokenStore {
    @Volatile private var token: String? = null
    @Volatile private var refresh: String? = null
    @Volatile private var identity: String? = null

    override fun accessToken(): String? = token
    override fun saveAccessToken(token: String) { this.token = token }
    override fun refreshToken(): String? = refresh
    override fun saveRefreshToken(token: String) { refresh = token }
    override fun userId(): String? = identity
    override fun saveUserId(userId: String) { identity = userId }
    override fun clear() { token = null; refresh = null; identity = null }
}

/** Process-scoped session boundary; persistent refresh-token storage is a separate security milestone. */
object FoodMindSession {
    @Volatile var tokenStore: SessionTokenStore = InMemorySessionTokenStore()
        private set

    fun initialize(context: android.content.Context) {
        if (tokenStore !is com.foodmind.foodmind_android.core.security.KeystoreSessionTokenStore) {
            tokenStore = com.foodmind.foodmind_android.core.security.KeystoreSessionTokenStore(
                context.applicationContext,
            )
        }
    }
}

class BearerTokenInterceptor(private val tokenStore: SessionTokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.accessToken()
        val request = chain.request().newBuilder().apply {
            if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
        }.build()
        return chain.proceed(request)
    }
}

class CorrelationIdInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-Correlation-ID", UUID.randomUUID().toString())
            .build()
        return chain.proceed(request)
    }
}

object FoodMindNetwork {
    fun createApi(baseUrl: String, tokenStore: SessionTokenStore): FoodMindApi {
        require(baseUrl.endsWith('/')) { "FoodMind API base URL must end with /" }
        val client = OkHttpClient.Builder()
            .addInterceptor(BearerTokenInterceptor(tokenStore))
            .addInterceptor(CorrelationIdInterceptor())
            .authenticator(SessionAuthenticator(baseUrl, tokenStore))
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .callTimeout(5, TimeUnit.MINUTES)
            .build()
        return createApi(baseUrl, client)
    }

    fun createApi(baseUrl: String, client: OkHttpClient): FoodMindApi {
        require(baseUrl.endsWith('/')) { "FoodMind API base URL must end with /" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FoodMindApi::class.java)
    }
}

private class SessionAuthenticator(
    private val baseUrl: String,
    private val tokenStore: SessionTokenStore,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): okhttp3.Request? {
        if (response.priorResponseCount() >= 2) return null
        val refreshToken = tokenStore.refreshToken() ?: return null
        return synchronized(lock) {
            val currentAccess = tokenStore.accessToken()
            val failedHeader = response.request.header("Authorization")
            if (!currentAccess.isNullOrBlank() && failedHeader != "Bearer $currentAccess") {
                return@synchronized response.request.newBuilder().header("Authorization", "Bearer $currentAccess").build()
            }
            val refreshApi = FoodMindNetwork.createApi(
                baseUrl,
                OkHttpClient.Builder().addInterceptor(CorrelationIdInterceptor()).build(),
            )
            val tokens = runCatching {
                runBlocking { refreshApi.refresh(RefreshRequest(refreshToken, "ANDROID")) }
            }.getOrElse {
                tokenStore.clear()
                return@synchronized null
            }
            tokenStore.saveAccessToken(tokens.accessToken)
            tokens.refreshToken?.takeIf(String::isNotBlank)?.let(tokenStore::saveRefreshToken)
            tokenStore.saveUserId(tokens.userId)
            response.request.newBuilder().header("Authorization", "Bearer ${tokens.accessToken}").build()
        }
    }

    private fun Response.priorResponseCount(): Int {
        var count = 1
        var current = priorResponse
        while (current != null) { count++; current = current.priorResponse }
        return count
    }
}

class FoodMindApiClient(
    private val api: FoodMindApi,
    private val tokenStore: SessionTokenStore,
) {
    suspend fun register(email: String, displayName: String, password: String, timeZone: String? = null): AuthTokenResponse {
        val response = api.register(RegisterRequest(email, displayName, password, timeZone))
        persist(response)
        return response
    }

    suspend fun login(email: String, password: String): AuthTokenResponse {
        val response = api.login(LoginRequest(email, password))
        persist(response)
        return response
    }

    suspend fun refreshSession(): AuthTokenResponse {
        val refreshToken = tokenStore.refreshToken()
            ?: error("No refresh token available")
        val response = api.refresh(RefreshRequest(refreshToken = refreshToken, clientType = "ANDROID"))
        persist(response)
        return response
    }

    suspend fun logout() {
        val refreshToken = tokenStore.refreshToken()
        try {
            api.logout(refreshToken?.let { RefreshRequest(it, "ANDROID") })
        } finally {
            tokenStore.clear()
        }
    }

    fun logoutLocal() = tokenStore.clear()

    suspend fun currentUser(): CurrentUserResponse = api.currentUser()
    suspend fun updateCurrentUser(request: UpdateCurrentUserRequest) = api.updateCurrentUser(request)
    suspend fun preferences() = api.preferences()
    suspend fun replacePreferences(request: ReplacePreferencesRequest) = api.replacePreferences(request)
    suspend fun referenceData() = api.referenceData()
    suspend fun meal(id: String) = api.meal(id)
    suspend fun place(id: String) = api.place(id)
    suspend fun product(id: String) = api.product(id)

    suspend fun logoutAll() {
        try {
            api.logoutAll()
        } finally {
            tokenStore.clear()
        }
    }

    suspend fun history(from: String, to: String, period: String = "DAY", types: String? = null, groupId: String? = null, cursor: String? = null): HistoryResponse =
        api.history(from = from, to = to, period = period, types = types, groupId = groupId, cursor = cursor)

    suspend fun createFoodRecord(request: CreateFoodRecordRequest) = api.createFoodRecord(request)
    suspend fun foodRecords(
        page: Int = 0,
        from: String? = null,
        to: String? = null,
        visibility: String? = null,
        groupId: String? = null,
        cuisineId: String? = null,
        mealId: String? = null,
        placeId: String? = null,
        minRating: Int? = null,
        maxRating: Int? = null,
    ) = api.foodRecords(
        from = from,
        to = to,
        visibility = visibility,
        groupId = groupId,
        cuisineId = cuisineId,
        mealId = mealId,
        placeId = placeId,
        minRating = minRating,
        maxRating = maxRating,
        page = page,
    )
    suspend fun foodRecord(id: String) = api.foodRecord(id)
    suspend fun updateFoodRecord(id: String, version: Long, request: UpdateFoodRecordRequest) =
        api.updateFoodRecord(id, "\"$version\"", request)
    suspend fun deleteFoodRecord(id: String) = api.deleteFoodRecord(id)
    suspend fun createDrinkRecord(request: CreateDrinkRecordRequest) = api.createDrinkRecord(request)
    suspend fun drinkRecords(
        page: Int = 0,
        from: String? = null,
        to: String? = null,
        visibility: String? = null,
        groupId: String? = null,
        placeId: String? = null,
        minRating: Int? = null,
        maxRating: Int? = null,
    ) = api.drinkRecords(
        from = from,
        to = to,
        visibility = visibility,
        groupId = groupId,
        placeId = placeId,
        minRating = minRating,
        maxRating = maxRating,
        page = page,
    )
    suspend fun drinkRecord(id: String) = api.drinkRecord(id)
    suspend fun updateDrinkRecord(id: String, version: Long, request: UpdateDrinkRecordRequest) =
        api.updateDrinkRecord(id, "\"$version\"", request)
    suspend fun deleteDrinkRecord(id: String) = api.deleteDrinkRecord(id)

    suspend fun explore(after: String? = null, topics: String? = null): ExplorePageResponse =
        api.explore(after = after, topics = topics)

    suspend fun search(query: String, types: String? = null, after: String? = null) = api.search(query, types, after)
    suspend fun saveWantToTry(sourceType: String, sourceId: String, note: String? = null) =
        api.saveWantToTry(SaveWantToTryRequest(sourceType, sourceId, note))
    suspend fun wantToTry(page: Int = 0) = api.wantToTry(page)
    suspend fun deleteWantToTry(id: String) = api.deleteWantToTry(id)

    suspend fun groups(): List<GroupResponse> = api.groups()
    suspend fun createGroup(name: String, description: String?) = api.createGroup(CreateGroupRequest(name, description))
    suspend fun group(groupId: String) = api.group(groupId)
    suspend fun updateGroup(groupId: String, request: UpdateGroupRequest) = api.updateGroup(groupId, request)
    suspend fun createGroupInvitation(groupId: String, request: CreateInvitationRequest) = api.createGroupInvitation(groupId, request)
    suspend fun joinGroup(token: String): GroupMemberResponse = try {
        api.joinGroup(JoinGroupRequest(token))
    } catch (failure: retrofit2.HttpException) {
        if (failure.code() == 404) api.joinGroupLegacy(JoinGroupRequest(token)) else throw failure
    }
    suspend fun groupMembers(groupId: String) = api.groupMembers(groupId)
    suspend fun removeGroupMember(groupId: String, userId: String) = api.removeGroupMember(groupId, userId)

    suspend fun groupFeed(groupId: String, after: String? = null): GroupFeedResponse =
        api.groupFeed(groupId = groupId, after = after)
    suspend fun shareRecommendation(groupId: String, candidateId: String, message: String?) =
        api.shareRecommendation(groupId, ShareRecommendationRequest(candidateId, message))

    suspend fun dashboard(from: String, to: String, groupBy: String = "DAY", timeZone: String? = null): DashboardResponse = api.dashboard(from = from, to = to, groupBy = groupBy, timeZone = timeZone)
    suspend fun weeklyRecap(weekStart: String) = api.weeklyRecap(weekStart)

    suspend fun createChatSession(title: String? = null): ChatSessionResponse =
        api.createChatSession(CreateChatSessionRequest(title))
    suspend fun chatSessions(page: Int = 0) = api.chatSessions(page)
    suspend fun chatSession(sessionId: String) = api.chatSession(sessionId)
    suspend fun deleteChatSession(sessionId: String) = api.deleteChatSession(sessionId)
    suspend fun shareChatReference(sessionId: String, sourceType: String, sourceId: String) =
        api.shareChatReference(sessionId, ShareChatReferenceRequest(sourceType, sourceId))

    suspend fun postChatMessage(sessionId: String, content: String, referenceIds: List<String>? = null): ChatMessageResponse =
        api.postChatMessage(sessionId, PostChatMessageRequest(content, referenceIds))

    suspend fun chatMessages(sessionId: String, after: String? = null): ChatPageResponse<ChatMessageResponse> =
        api.chatMessages(sessionId, after)

    suspend fun generateRecommendation(request: GenerateRecommendationRequest): RecommendationResponse =
        api.generateRecommendation(UUID.randomUUID().toString(), request)
    suspend fun recommendation(sessionId: String) = api.recommendation(sessionId)
    suspend fun submitRecommendationFeedback(sessionId: String, request: RecommendationFeedbackRequest) =
        api.submitRecommendationFeedback(sessionId, UUID.randomUUID().toString(), request)
    suspend fun recommendationHistory(page: Int = 0) = api.recommendationHistory(page)

    suspend fun generateCookingPlan(request: GenerateCookingPlanRequest): CookingPlanResponse =
        api.generateCookingPlan(UUID.randomUUID().toString(), UUID.randomUUID().toString(), request)

    suspend fun generateCookingPlanAsync(request: GenerateCookingPlanRequest): retrofit2.Response<CookingPlanAsyncAcceptedResponse> =
        api.generateCookingPlanAsync(UUID.randomUUID().toString(), request)

    suspend fun cookingPlanTask(planId: String): retrofit2.Response<CookingPlanTaskResponse> =
        api.cookingPlanTask(planId)

    suspend fun cancelCookingPlanTask(planId: String): retrofit2.Response<CookingPlanResponse> =
        api.cancelCookingPlanTask(planId)

    suspend fun submitCookingPlanDecisions(planId: String, answers: List<CookingQuestionAnswer>): CookingPlanResponse =
        api.submitDecisions(planId, UUID.randomUUID().toString(), answers)

    suspend fun submitCookingPlanDecisionsAsync(
        planId: String,
        answers: List<CookingQuestionAnswer>,
    ): retrofit2.Response<CookingPlanAsyncAcceptedResponse> =
        api.submitDecisionsAsync(planId, UUID.randomUUID().toString(), answers)

    suspend fun createCookingShoppingList(planId: String) = api.createCookingShoppingList(planId)

    suspend fun inventoryLots(page: Int = 0, size: Int = 100) = api.inventoryLots(page, size)
    suspend fun createInventoryLot(request: InventoryLotRequest) = api.createInventoryLot(request)
    suspend fun inventoryLot(lotId: String) = api.inventoryLot(lotId)
    suspend fun updateInventoryLot(lotId: String, version: Long, request: InventoryLotRequest) =
        api.updateInventoryLot(lotId, "\"$version\"", request)
    suspend fun archiveInventoryLot(lotId: String, version: Long) =
        api.archiveInventoryLot(lotId, "\"$version\"")

    suspend fun shoppingLists(status: String? = null, page: Int = 0, size: Int = 100) =
        api.shoppingLists(status, page, size)
    suspend fun shoppingList(shoppingListId: String) = api.shoppingList(shoppingListId)
    suspend fun updateShoppingListItem(
        shoppingListId: String,
        itemId: String,
        version: Long,
        request: UpdateShoppingListItemRequest,
    ) = api.updateShoppingListItem(shoppingListId, itemId, "\"$version\"", request)
    suspend fun completeShoppingList(shoppingListId: String) =
        api.completeShoppingList(shoppingListId, UUID.randomUUID().toString())

    suspend fun createRecipeImport(text: String) = api.createRecipeImport(CreateRecipeImportRequest(text))
    suspend fun recipeImport(importId: String) = api.recipeImport(importId)
    suspend fun answerRecipeImport(importId: String, version: Long, answers: List<RecipeImportAnswerRequest>) =
        api.answerRecipeImport(importId, "\"$version\"", RecipeImportAnswersRequest(answers))
    suspend fun confirmRecipeImport(importId: String, version: Long) =
        api.confirmRecipeImport(importId, "\"$version\"")

    suspend fun recipes(page: Int = 0, size: Int = 100) = api.recipes(page, size)
    suspend fun createRecipe(request: UserRecipeRequest) = api.createRecipe(request)
    suspend fun recipe(id: String) = api.recipe(id)
    suspend fun updateRecipe(id: String, version: Long, request: UserRecipeRequest) =
        api.updateRecipe(id, "\"$version\"", request)
    suspend fun deleteRecipe(id: String) = api.deleteRecipe(id)

    suspend fun cookingPlan(planId: String) = api.cookingPlan(planId)
    suspend fun cookingPlanHistory(page: Int = 0) = api.cookingPlanHistory(page)
    suspend fun createMediaUpload(request: CreateMediaUploadRequest) = api.createMediaUpload(request)
    suspend fun finaliseMediaUpload(mediaAssetId: String) = api.finaliseMediaUpload(mediaAssetId)
    suspend fun deleteMediaAsset(mediaAssetId: String) = api.deleteMediaAsset(mediaAssetId)

    private fun persist(response: AuthTokenResponse) {
        tokenStore.saveAccessToken(response.accessToken)
        response.refreshToken?.takeIf { it.isNotBlank() }?.let(tokenStore::saveRefreshToken)
        tokenStore.saveUserId(response.userId)
    }
}
