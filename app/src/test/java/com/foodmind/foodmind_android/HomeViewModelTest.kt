package com.foodmind.foodmind_android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun switchingToCookingResetsResultAndChangesCopy() = runTest {
        val viewModel = HomeViewModel()
        viewModel.generateRecommendation()
        viewModel.selectMode(HomeMode.COOKING)

        assertFalse(viewModel.state.value.hasResult)
        assertEquals(HomeMode.COOKING, viewModel.state.value.mode)
        assertTrue(viewModel.state.value.resultTitle.contains("salmon", ignoreCase = true))
    }

    @Test
    fun tryAnotherMovesToNextCandidateWithoutChangingMode() = runTest {
        val viewModel = HomeViewModel()
        viewModel.generateRecommendation()
        val first = viewModel.state.value.resultTitle
        viewModel.tryAnother()

        assertTrue(viewModel.state.value.hasResult)
        assertEquals(HomeMode.RECOMMEND, viewModel.state.value.mode)
        assertTrue(first != viewModel.state.value.resultTitle)
    }
}
