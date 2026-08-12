package com.foodmind.foodmind_android.core.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun authenticatedCallRefreshesOnceAfterUnauthorizedResponse() = runTest {
        tokenStore.saveAccessToken("expired-access")
        tokenStore.saveRefreshToken("refresh-1")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            Gson().toJson(mapOf("userId" to "u-1", "accessToken" to "fresh-access", "refreshToken" to "refresh-2")),
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val groups = FoodMindApiClient(api, tokenStore).groups()
        val failed = server.takeRequest()
        val refresh = server.takeRequest()
        val retried = server.takeRequest()

        assertTrue(groups.isEmpty())
        assertEquals("Bearer expired-access", failed.getHeader("Authorization"))
        assertEquals("/api/v1/auth/refresh", refresh.path)
        assertEquals("Bearer fresh-access", retried.getHeader("Authorization"))
        assertEquals("fresh-access", tokenStore.accessToken())
        assertEquals("refresh-2", tokenStore.refreshToken())
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
    fun cookingSelectionLoadsBackendRecipesAndSubmitsRecipeIdsWithPreferences() = runTest {
        tokenStore.saveAccessToken("test-token")
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            Gson().toJson(mapOf(
                "items" to listOf(mapOf(
                    "id" to "recipe-1",
                    "name" to "Tomato eggs",
                    "servings" to 2,
                    "ingredients" to listOf("2 tomatoes", "3 eggs"),
                    "steps" to listOf("Cook the eggs"),
                )),
                "page" to 0,
                "size" to 100,
            )),
        ))
        server.enqueue(MockResponse().setResponseCode(202).setBody(
            Gson().toJson(mapOf("planId" to "plan-1", "status" to "PROCESSING")),
        ))

        val client = FoodMindApiClient(api, tokenStore)
        val recipe = client.userRecipes().items.single()
        client.generateCookingPlanAsync(GenerateCookingPlanRequest(
            recipeIds = listOf(recipe.id),
            servings = 3,
            region = "SG",
            requiredDietaryTagCodes = listOf("VEGAN"),
            avoidAllergenCodes = listOf("PEANUT"),
        ))
        val listRequest = server.takeRequest()
        val generateRequest = server.takeRequest()
        val body = JsonParser.parseString(generateRequest.body.readUtf8()).asJsonObject

        assertEquals("/api/v1/recipes?page=0&size=100", listRequest.path)
        assertEquals("Tomato eggs", recipe.name)
        assertEquals("recipe-1", body.getAsJsonArray("recipeIds").single().asString)
        assertEquals("SG", body.get("region").asString)
        assertEquals("VEGAN", body.getAsJsonArray("requiredDietaryTagCodes").single().asString)
        assertEquals("PEANUT", body.getAsJsonArray("avoidAllergenCodes").single().asString)
    }

    @Test
    fun cookingRecipeEditorUsesBackendDetailUpdateAndDeleteContracts() = runTest {
        val recipeJson = Gson().toJson(mapOf(
            "id" to "recipe-1", "name" to "Tomato eggs", "servings" to 2,
            "ingredients" to listOf("2 tomatoes", "3 eggs"), "steps" to listOf("Cook"), "version" to 7,
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(recipeJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(recipeJson))
        server.enqueue(MockResponse().setResponseCode(204))
        val client = FoodMindApiClient(api, tokenStore)

        val recipe = client.userRecipe("recipe-1")
        client.updateUserRecipe(recipe.id, recipe.version, UserRecipeRequest(
            name = recipe.name, servings = recipe.servings, ingredients = recipe.ingredients, steps = recipe.steps,
        ))
        client.deleteUserRecipe(recipe.id)

        assertEquals("/api/v1/recipes/recipe-1", server.takeRequest().path)
        val update = server.takeRequest()
        assertEquals("PUT", update.method)
        assertEquals("\"7\"", update.getHeader("If-Match"))
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/recipes/recipe-1", delete.path)
    }

    @Test
    fun cookingInventoryAndShoppingIndexesUseWebContracts() = runTest {
        val lotJson = Gson().toJson(mapOf(
            "lotId" to "lot-1", "ingredientName" to "tofu", "quantity" to 300,
            "available" to 300, "reserved" to 0, "unit" to "g", "version" to 4,
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Gson().toJson(mapOf("items" to listOf(JsonParser.parseString(lotJson))))))
        server.enqueue(MockResponse().setResponseCode(201).setBody(lotJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(lotJson))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Gson().toJson(mapOf("items" to emptyList<Any>()))))
        val client = FoodMindApiClient(api, tokenStore)

        val lot = client.inventoryLots().items.single()
        val body = InventoryLotRequest("tofu", 300.0, "g")
        client.createInventoryLot(body)
        client.updateInventoryLot(lot, body)
        client.archiveInventoryLot(lot)
        client.shoppingLists()

        assertEquals("/api/v1/inventory/lots?page=0&size=100", server.takeRequest().path)
        assertEquals("POST", server.takeRequest().method)
        val update = server.takeRequest()
        assertEquals("PUT", update.method)
        assertEquals("\"4\"", update.getHeader("If-Match"))
        val archive = server.takeRequest()
        assertEquals("DELETE", archive.method)
        assertEquals("\"4\"", archive.getHeader("If-Match"))
        assertEquals("/api/v1/shopping-lists?page=0&size=100", server.takeRequest().path)
    }

    @Test
    fun generateCookingPlanAsyncParses202AcceptedHandleAndSendsIdempotencyKey() = runTest {
        tokenStore.saveAccessToken("test-token")
        server.enqueue(MockResponse().setResponseCode(202).setBody(
            Gson().toJson(mapOf(
                "planId" to "plan-1",
                "status" to "PROCESSING",
                "taskId" to "task-1",
                "location" to "/api/v1/cooking-plans/plan-1/task",
            )),
        ))

        val response = FoodMindApiClient(api, tokenStore).generateCookingPlanAsync(
            GenerateCookingPlanRequest(ingredients = listOf(CookingIngredientRequest("番茄", 2.0, "个"))),
        )
        val request = server.takeRequest()

        assertEquals(202, response.code())
        assertEquals("plan-1", response.body()?.planId)
        assertEquals("task-1", response.body()?.taskId)
        assertEquals("/api/v1/cooking-plans/plan-1/task", response.body()?.location)
        assertEquals("/api/v1/cooking-plans/generate-async", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertFalse(request.getHeader("Idempotency-Key").isNullOrBlank())
        assertEquals("POST", request.method)
    }

    @Test
    fun generateCookingPlanAsyncSurfaces200TerminalFailureAsAcceptedBody() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            Gson().toJson(mapOf("planId" to "plan-1", "status" to "FAILED", "errorCode" to "TASK_SUBMIT_FAILED")),
        ))

        val response = FoodMindApiClient(api, tokenStore).generateCookingPlanAsync(GenerateCookingPlanRequest())

        assertEquals(200, response.code())
        assertEquals("plan-1", response.body()?.planId)
        assertEquals("FAILED", response.body()?.status)
        assertNull(response.body()?.taskId)
    }

    @Test
    fun cookingPlanTaskParsesProgressAndDistinguishesTerminal404() = runTest {
        tokenStore.saveAccessToken("test-token")
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            Gson().toJson(mapOf(
                "planId" to "plan-1",
                "taskId" to "task-1",
                "status" to "PROCESSING",
                "syncState" to "POLLING",
                "progress" to mapOf("node" to "solve_schedule", "completedSteps" to 7, "message" to "Solving schedule"),
            )),
        ))
        server.enqueue(MockResponse().setResponseCode(404))

        val client = FoodMindApiClient(api, tokenStore)
        val running = client.cookingPlanTask("plan-1")
        val missing = client.cookingPlanTask("plan-1")
        val taskRequest = server.takeRequest()

        assertEquals(200, running.code())
        assertEquals("solve_schedule", running.body()?.progress?.node)
        assertEquals(7, running.body()?.progress?.completedSteps)
        assertEquals("POLLING", running.body()?.syncState)
        assertEquals(404, missing.code())
        assertEquals("/api/v1/cooking-plans/plan-1/task", taskRequest.path)
        assertEquals("Bearer test-token", taskRequest.getHeader("Authorization"))
    }

    @Test
    fun cancelCookingPlanTaskReturnsUpdatedPlanOn200And409OnConflict() = runTest {
        tokenStore.saveAccessToken("test-token")
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            Gson().toJson(mapOf("planId" to "plan-1", "status" to "FAILED", "errorCode" to "TASK_CANCELLED")),
        ))
        server.enqueue(MockResponse().setResponseCode(409))

        val client = FoodMindApiClient(api, tokenStore)
        val cancelled = client.cancelCookingPlanTask("plan-1")
        val conflicted = client.cancelCookingPlanTask("plan-1")
        val cancelRequest = server.takeRequest()
        val conflictRequest = server.takeRequest()

        assertEquals(200, cancelled.code())
        assertEquals("TASK_CANCELLED", cancelled.body()?.errorCode)
        assertEquals("FAILED", cancelled.body()?.status)
        assertEquals(409, conflicted.code())
        assertEquals("/api/v1/cooking-plans/plan-1/cancel", cancelRequest.path)
        assertEquals("POST", cancelRequest.method)
        assertEquals("/api/v1/cooking-plans/plan-1/cancel", conflictRequest.path)
        assertEquals("Bearer test-token", conflictRequest.getHeader("Authorization"))
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
    fun referenceDataParsesObjectAndStringCollectionsFromContract() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Gson().toJson(mapOf(
            "cuisines" to listOf(mapOf("id" to "c-1", "code" to "THAI", "name" to "Thai")),
            "dietaryTags" to emptyList<Any>(),
            "allergens" to emptyList<Any>(),
            "mealTypes" to listOf("BREAKFAST", "DINNER"),
            "placeTypes" to listOf("CAFE", "HAWKER_STALL"),
        ))))

        val reference = FoodMindApiClient(api, tokenStore).referenceData()

        assertEquals("THAI", reference.cuisines.single().code)
        assertEquals(listOf("BREAKFAST", "DINNER"), reference.mealTypes)
        assertEquals(listOf("CAFE", "HAWKER_STALL"), reference.placeTypes)
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
    fun foodRecordCrudUsesContractPathsAndIfMatchVersion() = runTest {
        tokenStore.saveAccessToken("test-token")
        val recordJson = Gson().toJson(mapOf("id" to "record-1", "mealNameSnapshot" to "番茄意面", "occurredAt" to "2026-08-01T12:00:00Z", "visibility" to "PRIVATE", "version" to 0))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Gson().toJson(mapOf("items" to listOf(mapOf("id" to "record-1", "mealNameSnapshot" to "番茄意面", "occurredAt" to "2026-08-01T12:00:00Z", "visibility" to "PRIVATE", "version" to 0))),),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(201).setBody(recordJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(recordJson))
        server.enqueue(MockResponse().setResponseCode(204))

        val client = FoodMindApiClient(api, tokenStore)
        assertEquals("record-1", client.foodRecords().items.single().id)
        assertEquals("record-1", client.createFoodRecord(CreateFoodRecordRequest(mealNameSnapshot = "番茄意面", occurredAt = "2026-08-01T12:00:00Z")).id)
        assertEquals("record-1", client.updateFoodRecord("record-1", 0, UpdateFoodRecordRequest(comment = "好吃")).id)
        client.deleteFoodRecord("record-1")

        assertEquals("/api/v1/food-records?page=0&size=20&sort=occurredAt%2Cdesc", server.takeRequest().path)
        assertEquals("POST", server.takeRequest().method)
        val updateRequest = server.takeRequest()
        assertEquals("PATCH", updateRequest.method)
        assertEquals("\"0\"", updateRequest.getHeader("If-Match"))
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test(expected = IllegalArgumentException::class)
    fun baseUrlMustEndWithSlash() {
        FoodMindNetwork.createApi("http://localhost/api/v1", OkHttpClient())
    }
}
