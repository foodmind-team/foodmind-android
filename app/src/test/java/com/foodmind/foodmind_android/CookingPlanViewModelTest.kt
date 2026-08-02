package com.foodmind.foodmind_android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CookingPlanViewModelTest {
    @Test
    fun togglingTaskUpdatesProgressAndIsReversible() = runTest {
        val viewModel = CookingPlanViewModel()
        viewModel.load("姜味味噌三文鱼饭")

        assertEquals(0, viewModel.state.value.progressPercent)
        viewModel.toggleTask("t1")
        assertEquals(25, viewModel.state.value.progressPercent)
        viewModel.toggleTask("t1")
        assertEquals(0, viewModel.state.value.progressPercent)
    }

    @Test
    fun loadingSameRecipeDoesNotResetCompletedTasks() = runTest {
        val viewModel = CookingPlanViewModel()
        viewModel.load("姜味味噌三文鱼饭")
        viewModel.toggleTask("t1")
        viewModel.load("姜味味噌三文鱼饭")

        assertEquals(setOf("t1"), viewModel.state.value.completedTaskIds)
    }

    @Test
    fun nonReadyStateDoesNotExposeExecutableTasks() = runTest {
        val viewModel = CookingPlanViewModel()
        viewModel.load("姜味味噌三文鱼饭", CookingPlanStatus.INFEASIBLE)

        assertEquals(CookingPlanStatus.INFEASIBLE, viewModel.state.value.status)
        assertEquals(0, viewModel.state.value.tasks.size)
        assertEquals(0, viewModel.state.value.progressPercent)
    }
}
