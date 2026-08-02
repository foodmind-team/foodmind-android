package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.CookingPlanStatus
import com.foodmind.foodmind_android.core.network.CookingPlanInputResponse
import com.foodmind.foodmind_android.core.network.CookingPlanWarningResponse
import com.foodmind.foodmind_android.core.network.CookingStepResponse
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.CookingPlanResponse
import com.foodmind.foodmind_android.core.network.GenerateCookingPlanRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookingPlanRepositoryTest {
    @Test
    fun mapsSucceededPlanToExecutableTasks() = runTest {
        val apiClient = FoodMindApiClient(
            api = object : com.foodmind.foodmind_android.core.network.FoodMindApi {
                override suspend fun login(request: com.foodmind.foodmind_android.core.network.LoginRequest) = error("unused")
                override suspend fun refresh(request: com.foodmind.foodmind_android.core.network.RefreshRequest) = error("unused")
                override suspend fun currentUser() = error("unused")
                override suspend fun recipes(page: Int, size: Int) = error("unused")
                override suspend fun recipe(id: String) = error("unused")
                override suspend fun createRecipe(request: com.foodmind.foodmind_android.core.network.UserRecipeRequest) = error("unused")
                override suspend fun updateRecipe(id: String, ifMatch: String, request: com.foodmind.foodmind_android.core.network.UserRecipeRequest) = error("unused")
                override suspend fun deleteRecipe(id: String) = error("unused")
                override suspend fun history(from: String, to: String, period: String, size: Int) = error("unused")
                override suspend fun explore(types: String?, topics: String?, after: String?, page: Int, size: Int) = error("unused")
                override suspend fun groups(): List<com.foodmind.foodmind_android.core.network.GroupResponse> = error("unused")
                override suspend fun groupFeed(groupId: String, after: String?, limit: Int) = error("unused")
                override suspend fun dashboard(from: String, to: String, groupBy: String, timeZone: String?) = error("unused")
                override suspend fun createChatSession(request: com.foodmind.foodmind_android.core.network.CreateChatSessionRequest) = error("unused")
                override suspend fun postChatMessage(sessionId: String, request: com.foodmind.foodmind_android.core.network.PostChatMessageRequest) = error("unused")
                override suspend fun chatMessages(sessionId: String, after: String?, size: Int) = error("unused")
                override suspend fun logout(request: com.foodmind.foodmind_android.core.network.RefreshRequest?) = error("unused")
                override suspend fun generateRecommendation(
                    idempotencyKey: String,
                    request: com.foodmind.foodmind_android.core.network.GenerateRecommendationRequest,
                ) = error("unused")
                override suspend fun generateCookingPlan(
                    idempotencyKey: String,
                    correlationId: String,
                    request: GenerateCookingPlanRequest,
                ) = CookingPlanResponse(
                    status = "SUCCEEDED",
                    inputs = listOf(CookingPlanInputResponse(1, "豆腐", 1.0, "块", "USER")),
                    steps = listOf(CookingStepResponse(1, "切块并煎香"), CookingStepResponse(2, "调味出锅")),
                )
            },
            tokenStore = com.foodmind.foodmind_android.core.network.InMemorySessionTokenStore(),
        )

        val result = CookingPlanRepositoryImpl(apiClient).generate(
            GenerateCookingPlanRequest(emptyList()),
        )

        assertTrue(result.isSuccess)
        assertEquals(CookingPlanStatus.READY, result.getOrThrow().status)
        assertEquals("豆腐", result.getOrThrow().recipeNames)
        assertEquals(2, result.getOrThrow().tasks.size)
    }

    @Test
    fun mapsNoValidRecipeToNonExecutableState() = runTest {
        val apiClient = FoodMindApiClient(
            api = object : com.foodmind.foodmind_android.core.network.FoodMindApi {
                override suspend fun login(request: com.foodmind.foodmind_android.core.network.LoginRequest) = error("unused")
                override suspend fun refresh(request: com.foodmind.foodmind_android.core.network.RefreshRequest) = error("unused")
                override suspend fun currentUser() = error("unused")
                override suspend fun recipes(page: Int, size: Int) = error("unused")
                override suspend fun recipe(id: String) = error("unused")
                override suspend fun createRecipe(request: com.foodmind.foodmind_android.core.network.UserRecipeRequest) = error("unused")
                override suspend fun updateRecipe(id: String, ifMatch: String, request: com.foodmind.foodmind_android.core.network.UserRecipeRequest) = error("unused")
                override suspend fun deleteRecipe(id: String) = error("unused")
                override suspend fun history(from: String, to: String, period: String, size: Int) = error("unused")
                override suspend fun explore(types: String?, topics: String?, after: String?, page: Int, size: Int) = error("unused")
                override suspend fun groups(): List<com.foodmind.foodmind_android.core.network.GroupResponse> = error("unused")
                override suspend fun groupFeed(groupId: String, after: String?, limit: Int) = error("unused")
                override suspend fun dashboard(from: String, to: String, groupBy: String, timeZone: String?) = error("unused")
                override suspend fun createChatSession(request: com.foodmind.foodmind_android.core.network.CreateChatSessionRequest) = error("unused")
                override suspend fun postChatMessage(sessionId: String, request: com.foodmind.foodmind_android.core.network.PostChatMessageRequest) = error("unused")
                override suspend fun chatMessages(sessionId: String, after: String?, size: Int) = error("unused")
                override suspend fun logout(request: com.foodmind.foodmind_android.core.network.RefreshRequest?) = error("unused")
                override suspend fun generateRecommendation(
                    idempotencyKey: String,
                    request: com.foodmind.foodmind_android.core.network.GenerateRecommendationRequest,
                ) = error("unused")
                override suspend fun generateCookingPlan(
                    idempotencyKey: String,
                    correlationId: String,
                    request: GenerateCookingPlanRequest,
                ) = CookingPlanResponse(
                    status = "NO_VALID_RECIPE",
                    warnings = listOf(CookingPlanWarningResponse(1, "NO_RECIPE", "当前食材无法组合")),
                )
            },
            tokenStore = com.foodmind.foodmind_android.core.network.InMemorySessionTokenStore(),
        )

        val result = CookingPlanRepositoryImpl(apiClient).generate(GenerateCookingPlanRequest(emptyList()))

        assertEquals(CookingPlanStatus.INFEASIBLE, result.getOrThrow().status)
        assertTrue(result.getOrThrow().tasks.isEmpty())
        assertEquals("当前食材无法组合", result.getOrThrow().warning)
    }
}
