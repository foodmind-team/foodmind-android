package com.foodmind.foodmind_android

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeLibraryTest {
    @Test
    fun scalesStructuredIngredientLineForTargetServings() {
        assertEquals("三文鱼 250 g", scaleIngredientLine("三文鱼 500 g", 0.5))
        assertEquals("鸡蛋 6 个", scaleIngredientLine("鸡蛋 4 个", 1.5))
    }

    @Test
    fun preservesUnstructuredIngredientLine() {
        assertEquals("盐适量", scaleIngredientLine("盐适量", 2.0))
    }
}
