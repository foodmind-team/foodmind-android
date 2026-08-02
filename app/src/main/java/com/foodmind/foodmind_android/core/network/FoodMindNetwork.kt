package com.foodmind.foodmind_android.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID

interface SessionTokenStore {
    fun accessToken(): String?
    fun saveAccessToken(token: String)
    fun refreshToken(): String? = null
    fun saveRefreshToken(token: String) = Unit
    fun clear()
}

class InMemorySessionTokenStore : SessionTokenStore {
    @Volatile private var token: String? = null
    @Volatile private var refresh: String? = null

    override fun accessToken(): String? = token
    override fun saveAccessToken(token: String) { this.token = token }
    override fun refreshToken(): String? = refresh
    override fun saveRefreshToken(token: String) { refresh = token }
    override fun clear() { token = null; refresh = null }
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

class FoodMindApiClient(
    private val api: FoodMindApi,
    private val tokenStore: SessionTokenStore,
) {
    suspend fun login(email: String, password: String): AuthTokenResponse {
        val response = api.login(LoginRequest(email, password))
        tokenStore.saveAccessToken(response.accessToken)
        response.refreshToken?.takeIf { it.isNotBlank() }?.let(tokenStore::saveRefreshToken)
        return response
    }

    suspend fun refreshSession(): AuthTokenResponse {
        val refreshToken = tokenStore.refreshToken()
            ?: error("No refresh token available")
        val response = api.refresh(RefreshRequest(refreshToken = refreshToken, clientType = "ANDROID"))
        tokenStore.saveAccessToken(response.accessToken)
        response.refreshToken?.takeIf { it.isNotBlank() }?.let(tokenStore::saveRefreshToken)
        return response
    }

    suspend fun logout() {
        val refreshToken = tokenStore.refreshToken()
        api.logout(refreshToken?.let { RefreshRequest(it, "ANDROID") })
        tokenStore.clear()
    }

    fun logoutLocal() = tokenStore.clear()

    suspend fun currentUser(): CurrentUserResponse = api.currentUser()

    suspend fun recipes(): UserRecipePageResponse = api.recipes()

    suspend fun recipe(id: String): UserRecipeResponse = api.recipe(id)

    suspend fun createRecipe(request: UserRecipeRequest): UserRecipeResponse = api.createRecipe(request)

    suspend fun updateRecipe(id: String, version: Long, request: UserRecipeRequest): UserRecipeResponse =
        api.updateRecipe(id, "\"$version\"", request)

    suspend fun deleteRecipe(id: String) { api.deleteRecipe(id) }

    suspend fun history(from: String, to: String): HistoryResponse = api.history(from = from, to = to)

    suspend fun explore(after: String? = null, topics: String? = null): ExplorePageResponse =
        api.explore(after = after, topics = topics)

    suspend fun groups(): List<GroupResponse> = api.groups()

    suspend fun groupFeed(groupId: String, after: String? = null): GroupFeedResponse =
        api.groupFeed(groupId = groupId, after = after)

    suspend fun dashboard(from: String, to: String): DashboardResponse = api.dashboard(from = from, to = to)

    suspend fun createChatSession(title: String? = null): ChatSessionResponse =
        api.createChatSession(CreateChatSessionRequest(title))

    suspend fun postChatMessage(sessionId: String, content: String): ChatMessageResponse =
        api.postChatMessage(sessionId, PostChatMessageRequest(content))

    suspend fun chatMessages(sessionId: String, after: String? = null): ChatPageResponse<ChatMessageResponse> =
        api.chatMessages(sessionId, after)

    suspend fun generateRecommendation(request: GenerateRecommendationRequest): RecommendationResponse =
        api.generateRecommendation(UUID.randomUUID().toString(), request)

    suspend fun generateCookingPlan(request: GenerateCookingPlanRequest): CookingPlanResponse =
        api.generateCookingPlan(UUID.randomUUID().toString(), UUID.randomUUID().toString(), request)
}
