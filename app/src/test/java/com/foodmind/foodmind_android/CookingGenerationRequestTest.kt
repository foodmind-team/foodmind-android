package com.foodmind.foodmind_android

import com.foodmind.foodmind_android.core.network.AllergenPreference
import com.foodmind.foodmind_android.core.network.UserPreferencesResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class CookingGenerationRequestTest {
    @Test
    fun `generation uses account safety preferences and account region`() {
        val request = buildCookingGenerationRequest(
            recipeIds = listOf("recipe-1"),
            servings = 4,
            maxMinutes = 45,
            accountPreferences = UserPreferencesResponse(
                cookingRegion = "US",
                dietaryTagCodes = listOf("VEGAN"),
                allergens = listOf(AllergenPreference("PEANUT", "SEVERE")),
            ),
        )

        assertEquals(listOf("recipe-1"), request.recipeIds)
        assertEquals("US", request.region)
        assertEquals(listOf("VEGAN"), request.requiredDietaryTagCodes)
        assertEquals(listOf("PEANUT"), request.avoidAllergenCodes)
    }
}
