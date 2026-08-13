package com.foodmind.foodmind_android

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class CookingRecipeImportTest {
    @Test
    fun `session heading describes each recoverable import state`() {
        assertEquals("Structuring your recipes.", recipeImportSessionHeading("PROCESSING"))
        assertEquals(
            "A few details will finish these recipes.",
            recipeImportSessionHeading("NEEDS_CLARIFICATION"),
        )
        assertEquals("Review recipes before saving.", recipeImportSessionHeading("READY"))
        assertEquals("This import needs another try.", recipeImportSessionHeading("FAILED"))
    }

    @Test
    fun `session heading has a safe loading fallback`() {
        assertEquals("Structuring your recipes.", recipeImportSessionHeading(null))
        assertEquals("Structuring your recipes.", recipeImportSessionHeading("UNKNOWN"))
    }

    @Test
    fun `composer resumes only the matching import text`() {
        assertEquals(true, canResumeRecipeImport("import-1", "Tomato toast", "  Tomato toast  "))
        assertEquals(false, canResumeRecipeImport("import-1", "Tomato toast", "Tomato soup"))
        assertEquals(false, canResumeRecipeImport(null, "Tomato toast", "Tomato toast"))
    }

    @Test
    fun `recipe import errors preserve a useful retry action`() {
        assertEquals(
            "FoodMind could not reach the recipe service. Your text is still here—check your connection and try again.",
            friendlyRecipeImportError(IOException("offline")),
        )
        assertEquals(
            "The recipe import stopped before it could finish. Your text is still here—please try again.",
            friendlyRecipeImportError(IllegalStateException("unexpected")),
        )
    }
}
