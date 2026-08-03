package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.core.network.CookingPlanAsyncAcceptedResponse
import com.foodmind.foodmind_android.core.network.CookingPlanResponse
import com.foodmind.foodmind_android.core.network.CookingPlanTaskProgressResponse
import com.foodmind.foodmind_android.core.network.CookingPlanTaskResponse
import com.foodmind.foodmind_android.core.network.CookingStepResponse
import com.foodmind.foodmind_android.core.network.GenerateCookingPlanRequest
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class CookingPlanTaskRepositoryTest {
    @Test
    fun generateAsyncReturnsAcceptedHandleOn202() = runTest {
        val repo = CookingPlanTaskRepository(
            submitAsync = {
                Response.success(
                    CookingPlanAsyncAcceptedResponse(
                        planId = "plan-1",
                        status = "PROCESSING",
                        taskId = "task-1",
                        location = "/api/v1/cooking-plans/plan-1/task",
                    ),
                )
            },
            getTask = { error("not used") },
            readPlan = { error("not used") },
            cancelTask = { error("not used") },
        )

        val result = repo.generateAsync(GenerateCookingPlanRequest())

        assertTrue(result.isSuccess)
        val accepted = result.getOrThrow() as AsyncSubmitResult.Accepted
        assertEquals("plan-1", accepted.planId)
        assertEquals("task-1", accepted.taskId)
        assertEquals("/api/v1/cooking-plans/plan-1/task", accepted.location)
    }

    @Test
    fun generateAsyncSurfacesTerminalFailedWhenBackendAnswers200() = runTest {
        val repo = CookingPlanTaskRepository(
            submitAsync = {
                Response.success(CookingPlanAsyncAcceptedResponse(planId = "plan-1", status = "FAILED"))
            },
            getTask = { error("not used") },
            readPlan = { error("not used") },
            cancelTask = { error("not used") },
        )

        val result = repo.generateAsync(GenerateCookingPlanRequest())

        assertTrue(result.isSuccess)
        val failed = result.getOrThrow() as AsyncSubmitResult.TerminalFailed
        assertEquals("plan-1", failed.planId)
        assertEquals("FAILED", failed.status)
    }

    @Test
    fun generateAsyncFailsWhenSubmissionIsRejected() = runTest {
        val repo = CookingPlanTaskRepository(
            submitAsync = { Response.error(400, "{}".toResponseBody(null)) },
            getTask = { error("not used") },
            readPlan = { error("not used") },
            cancelTask = { error("not used") },
        )

        val result = repo.generateAsync(GenerateCookingPlanRequest())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AsyncSubmitFailureException)
    }

    @Test
    fun pollsProgressUntil404ThenReadsTerminalPlan() = runTest {
        var taskCalls = 0
        val progressNodes = mutableListOf<String>()
        val repo = CookingPlanTaskRepository(
            submitAsync = { error("not used") },
            getTask = {
                taskCalls++
                when (taskCalls) {
                    1 -> Response.success(
                        CookingPlanTaskResponse(
                            planId = "plan-1",
                            taskId = "task-1",
                            status = "PROCESSING",
                            syncState = "PENDING",
                            progress = CookingPlanTaskProgressResponse(node = "collect_inputs", completedSteps = 3),
                        ),
                    )
                    2 -> Response.success(
                        CookingPlanTaskResponse(
                            planId = "plan-1",
                            taskId = "task-1",
                            status = "PROCESSING",
                            syncState = "POLLING",
                            progress = CookingPlanTaskProgressResponse(node = "solve_schedule", completedSteps = 7, message = "Solving schedule"),
                        ),
                    )
                    else -> Response.error(404, "{}".toResponseBody(null))
                }
            },
            readPlan = { planId ->
                CookingPlanResponse(
                    status = "READY",
                    planId = planId,
                    steps = listOf(CookingStepResponse(stepNo = 1, instruction = "步骤一")),
                )
            },
            cancelTask = { error("not used") },
        )

        val result = repo.pollUntilTerminal("plan-1", pollIntervalMillis = 1) { progressNodes.add(it.progress?.node.orEmpty()) }

        assertTrue(result.isSuccess)
        assertEquals("READY", result.getOrThrow().status)
        assertEquals(1, result.getOrThrow().steps.size)
        assertEquals(listOf("collect_inputs", "solve_schedule"), progressNodes)
    }

    @Test
    fun pollsFailOnUnexpectedTaskStatus() = runTest {
        val repo = CookingPlanTaskRepository(
            submitAsync = { error("not used") },
            getTask = { Response.error(500, "{}".toResponseBody(null)) },
            readPlan = { error("not used") },
            cancelTask = { error("not used") },
        )

        val result = repo.pollUntilTerminal("plan-1", pollIntervalMillis = 1)

        assertTrue(result.isFailure)
        assertEquals("Unexpected task status HTTP 500", result.exceptionOrNull()?.message)
    }

    @Test
    fun cancelReturnsUpdatedPlanOn200() = runTest {
        val repo = CookingPlanTaskRepository(
            submitAsync = { error("not used") },
            getTask = { error("not used") },
            readPlan = { error("not used") },
            cancelTask = {
                Response.success(CookingPlanResponse(status = "FAILED", planId = "plan-1", failureCode = "TASK_CANCELLED"))
            },
        )

        val result = repo.cancel("plan-1")

        assertTrue(result.isSuccess)
        assertEquals("FAILED", result.getOrThrow().status)
        assertEquals("TASK_CANCELLED", result.getOrThrow().failureCode)
    }

    @Test
    fun cancelSurfacesConflictOn409() = runTest {
        val repo = CookingPlanTaskRepository(
            submitAsync = { error("not used") },
            getTask = { error("not used") },
            readPlan = { error("not used") },
            cancelTask = { Response.error(409, "{}".toResponseBody(null)) },
        )

        val result = repo.cancel("plan-1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CookingPlanCancelConflictException)
    }
}
