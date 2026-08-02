package com.foodmind.foodmind_android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeSelectionViewModelTest {
    @Test
    fun selectingRecipesBuildsOrderedNamesAndCanGenerate() = runTest {
        val viewModel = RecipeSelectionViewModel()
        viewModel.toggleRecipe("salmon")
        viewModel.toggleRecipe("noodle")

        assertTrue(viewModel.state.value.canGenerate)
        assertEquals("姜味味噌三文鱼饭、姜葱豆腐拌面", viewModel.state.value.selectedNames)
    }

    @Test
    fun togglingSameRecipeRemovesIt() = runTest {
        val viewModel = RecipeSelectionViewModel()
        viewModel.toggleRecipe("salmon")
        viewModel.toggleRecipe("salmon")

        assertFalse(viewModel.state.value.canGenerate)
    }
}
