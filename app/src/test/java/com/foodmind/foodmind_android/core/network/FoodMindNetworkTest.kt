package com.foodmind.foodmind_android.core.network

import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FoodMindNetworkTest {
    private lateinit var server: MockWebServer
    private lateinit var tokenStore: InMemorySessionTokenStore
    private lateinit var api: FoodMindApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokenStore = InMemorySessionTokenStore()
        api = FoodMindNetwork.createApi(
            server.url("api/v1/").toString(),
            tokenStore,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loginParsesResponseAndStoresBearerToken() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            Gson().toJson(mapOf("userId" to "u-1", "accessToken" to "access-token")),
        ))

        val response = FoodMindApiClient(api, tokenStore).login("demo@example.com", "password")

        assertEquals("u-1", response.userId)
        assertEquals("access-token", tokenStore.accessToken())
        assertEquals("/api/v1/auth/login", server.takeRequest().path)
    }

    @Test
    fun refreshSessionRotatesAccessAndRefreshTokens() = runTest {
        tokenStore.saveRefreshToken("refresh-1")
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            Gson().toJson(mapOf("userId" to "u-1", "accessToken" to "access-2", "refreshToken" to "refresh-2")),
        ))

        val response = FoodMindApiClient(api, tokenStore).refreshSession()
        val request = server.takeRequest()

        assertEquals("access-2", response.accessToken)
        assertEquals("access-2", tokenStore.accessToken())
        assertEquals("refresh-2", tokenStore.refreshToken())
        assertEquals("/api/v1/auth/refresh", request.path)
    }

    @Test
    fun cookingPlanSendsBearerCorrelationAndIdempotencyHeaders() = runTest {
        tokenStore.saveAccessToken("test-token")
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            Gson().toJson(mapOf("status" to "SUCCEEDED", "title" to "番茄炒蛋")),
        ))

        val response = FoodMindApiClient(api, tokenStore).generateCookingPlan(
            GenerateCookingPlanRequest(
                ingredients = listOf(CookingIngredientRequest("番茄", 2.0, "个")),
            ),
        )
        val request = server.takeRequest()

        assertEquals("SUCCEEDED", response.status)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertNotNull(request.getHeader("X-Correlation-ID"))
        assertFalse(request.getHeader("Idempotency-Key").isNullOrBlank())
        assertEquals("POST", request.method)
    }

    @Test
    fun recommendationParsesCandidatesAndUsesPublicApiPath() = runTest {
        tokenStore.saveAccessToken("test-token")
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            Gson().toJson(
                mapOf(
                    "sessionId" to "session-1",
                    "status" to "SUCCEEDED",
                    "items" to listOf(mapOf("mealName" to "海南鸡饭", "placeName" to "FoodMind 厨房")),
                ),
            ),
        ))

        val response = FoodMindApiClient(api, tokenStore).generateRecommendation(
            GenerateRecommendationRequest(mealType = "dinner"),
        )
        val request = server.takeRequest()

        assertEquals("session-1", response.sessionId)
        assertEquals("海南鸡饭", response.items.single().mealName)
        assertEquals("/api/v1/recommendations/generate", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertFalse(request.getHeader("Idempotency-Key").isNullOrBlank())
    }

    @Test
    fun historyUsesPublicQueryContractAndParsesEntries() = runTest {
        tokenStore.saveAccessToken("test-token")
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            Gson().toJson(
                mapOf(
                    "period" to "DAY",
                    "entries" to listOf(mapOf("sourceType" to "FOOD", "sourceId" to "entry-1", "title" to "海南鸡饭")),
                ),
            ),
        ))

        val response = FoodMindApiClient(api, tokenStore).history("2026-08-01", "2026-08-02")
        val request = server.takeRequest()

        assertEquals("DAY", response.period)
        assertEquals("海南鸡饭", response.entries.single().title)
        assertEquals("/api/v1/history?from=2026-08-01&to=2026-08-02&period=DAY&size=20", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
    }

    @Test
    fun exploreUsesCursorQueryAndParsesItems() = runTest {
        tokenStore.saveAccessToken("test-token")
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            Gson().toJson(mapOf(
                "items" to listOf(mapOf("sourceType" to "CURATED_PLACE", "sourceId" to "place-1", "title" to "附近的新店")),
                "nextCursor" to "cursor-2",
                "hasNext" to true,
            )),
        ))

        val response = FoodMindApiClient(api, tokenStore).explore(after = "cursor-1", topics = "晚餐")
        val request = server.takeRequest()

        assertEquals("附近的新店", response.items.single().title)
        assertEquals("cursor-2", response.nextCursor)
        assertEquals("/api/v1/explore?topics=%E6%99%9A%E9%A4%90&after=cursor-1&page=0&size=20", request.path)
    }

    @Test
    fun groupsParsesPublicListAndAddsBearerToken() = runTest {
        tokenStore.saveAccessToken("test-token")
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            Gson().toJson(listOf(mapOf("id" to "group-1", "name" to "周末厨房", "status" to "ACTIVE"))),
        ))

        val groups = FoodMindApiClient(api, tokenStore).groups()
        val request = server.takeRequest()

        assertEquals("group-1", groups.single().id)
        assertEquals("周末厨房", groups.single().name)
        assertEquals("/api/v1/groups", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
    }

    @Test
    fun groupFeedUsesCursorAndLimitContract() = runTest {
        tokenStore.saveAccessToken("test-token")
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            Gson().toJson(mapOf(
                "items" to listOf(mapOf("sourceId" to "feed-1", "actorDisplayName" to "小明", "mealNameSnapshot" to "豆腐饭")),
                "nextCursor" to "feed-cursor-2",
            )),
        ))

        val response = FoodMindApiClient(api, tokenStore).groupFeed("group-1", after = "feed-cursor-1")
        val request = server.takeRequest()

        assertEquals("feed-cursor-2", response.nextCursor)
        assertEquals("豆腐饭", response.items.single().mealNameSnapshot)
        assertEquals("/api/v1/groups/group-1/feed?after=feed-cursor-1&limit=20", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
    }

    @Test
    fun dashboardUsesBoundedDateWindowContract() = runTest {
        tokenStore.saveAccessToken("test-token")
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            Gson().toJson(mapOf(
                "empty" to false,
                "metrics" to listOf(mapOf("code" to "MEAL_COUNT", "label" to "饮食次数", "value" to 3, "unit" to "COUNT")),
            )),
        ))

        val response = FoodMindApiClient(api, tokenStore).dashboard("2026-08-01", "2026-08-02")
        val request = server.takeRequest()

        assertEquals(false, response.empty)
        assertEquals("MEAL_COUNT", response.metrics.single().code)
        assertEquals("/api/v1/dashboard?from=2026-08-01&to=2026-08-02&groupBy=DAY", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
    }

    @Test
    fun chatCreatesSessionAndPostsMessageOnPublicPaths() = runTest {
        tokenStore.saveAccessToken("test-token")
        server.enqueue(MockResponse().setResponseCode(201).setBody(Gson().toJson(mapOf("id" to "session-1", "title" to "助手"))))
        server.enqueue(MockResponse().setResponseCode(201).setBody(Gson().toJson(mapOf("id" to "message-1", "sessionId" to "session-1", "role" to "ASSISTANT", "content" to "你好"))))

        val client = FoodMindApiClient(api, tokenStore)
        assertEquals("session-1", client.createChatSession("助手").id)
        assertEquals("你好", client.postChatMessage("session-1", "推荐晚餐").content)
        val sessionRequest = server.takeRequest()
        val messageRequest = server.takeRequest()

        assertEquals("/api/v1/chat/sessions", sessionRequest.path)
        assertEquals("/api/v1/chat/sessions/session-1/messages", messageRequest.path)
        assertEquals("Bearer test-token", messageRequest.getHeader("Authorization"))
    }

    @Test
    fun logoutSendsRefreshTokenAndClearsLocalSession() = runTest {
        tokenStore.saveAccessToken("access-token")
        tokenStore.saveRefreshToken("refresh-token")
        server.enqueue(MockResponse().setResponseCode(204))

        FoodMindApiClient(api, tokenStore).logout()
        val request = server.takeRequest()

        assertEquals("/api/v1/auth/logout", request.path)
        assertNull(tokenStore.accessToken())
        assertNull(tokenStore.refreshToken())
    }

    @Test
    fun userRecipeCrudUsesOwnerScopedPathsAndIfMatchVersion() = runTest {
        tokenStore.saveAccessToken("test-token")
        val recipeJson = Gson().toJson(mapOf("id" to "recipe-1", "name" to "番茄意面", "servings" to 2, "version" to 0,
            "ingredients" to listOf("番茄"), "steps" to listOf("煮面")))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Gson().toJson(mapOf("items" to listOf(mapOf("id" to "recipe-1", "name" to "番茄意面", "version" to 0))),),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(201).setBody(recipeJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(recipeJson))
        server.enqueue(MockResponse().setResponseCode(204))

        val client = FoodMindApiClient(api, tokenStore)
        assertEquals("recipe-1", client.recipes().items.single().id)
        val draft = UserRecipeRequest("番茄意面", 2, ingredients = listOf("番茄"), steps = listOf("煮面"))
        assertEquals("recipe-1", client.createRecipe(draft).id)
        assertEquals("recipe-1", client.updateRecipe("recipe-1", 0, draft).id)
        client.deleteRecipe("recipe-1")

        assertEquals("/api/v1/recipes?page=0&size=100", server.takeRequest().path)
        assertEquals("POST", server.takeRequest().method)
        val updateRequest = server.takeRequest()
        assertEquals("PUT", updateRequest.method)
        assertEquals("\"0\"", updateRequest.getHeader("If-Match"))
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test(expected = IllegalArgumentException::class)
    fun baseUrlMustEndWithSlash() {
        FoodMindNetwork.createApi("http://localhost/api/v1", OkHttpClient())
    }
}
