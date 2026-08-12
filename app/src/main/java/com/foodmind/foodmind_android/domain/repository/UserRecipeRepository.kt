package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.UserRecipeRequest
import com.foodmind.foodmind_android.core.network.UserRecipeResponse

/** Backend-owned recipe library shared by every FoodMind client for the authenticated account. */
class UserRecipeRepository(private val client: FoodMindApiClient) {
    suspend fun list(): List<UserRecipeResponse> = client.recipes().items

    suspend fun get(id: String): UserRecipeResponse = client.recipe(id)

    suspend fun save(
        existing: UserRecipeResponse?,
        request: UserRecipeRequest,
    ): UserRecipeResponse = if (existing == null) {
        client.createRecipe(request)
    } else {
        client.updateRecipe(existing.id, existing.version, request)
    }

    suspend fun delete(id: String) = client.deleteRecipe(id)
}
