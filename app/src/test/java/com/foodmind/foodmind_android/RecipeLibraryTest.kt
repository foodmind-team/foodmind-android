package com.foodmind.foodmind_android

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeLibraryTest {
    @Test
    fun scalesStructuredIngredientLineForTargetServings() {
        assertEquals("Salmon 250 g", scaleIngredientLine("Salmon 500 g", 0.5))
        assertEquals("Eggs 6", scaleIngredientLine("Eggs 4", 1.5))
    }

    @Test
    fun preservesUnstructuredIngredientLine() {
        assertEquals("Salt to taste", scaleIngredientLine("Salt to taste", 2.0))
    }
}
