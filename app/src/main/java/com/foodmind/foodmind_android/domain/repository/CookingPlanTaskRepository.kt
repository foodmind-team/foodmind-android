package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.core.network.CookingPlanAsyncAcceptedResponse
import com.foodmind.foodmind_android.core.network.CookingPlanResponse
import com.foodmind.foodmind_android.core.network.CookingPlanTaskResponse
import com.foodmind.foodmind_android.core.network.GenerateCookingPlanRequest
import kotlinx.coroutines.delay
import retrofit2.Response

/** Terminal plans are read from the plan endpoint only when the task endpoint returns 404. */
const val COOKING_PLAN_TASK_NOT_FOUND = 404
/** Cancelling a plan that is no longer a PROCESSING async task is a conflict. */
const val COOKING_PLAN_CANCEL_CONFLICT = 409

/**
 * Async cooking-plan task tracking. The backend exposes three endpoints:
 * submit (`generate-async`, 202 + handle), poll (`GET /{planId}/task`, 200 progress / 404 terminal),
 * and cancel (`POST /{planId}/cancel`, 200 updated plan / 409 not cancellable).
 * Four-state mapping of the terminal plan is unchanged; PROCESSING is surfaced via poll progress.
 */
class CookingPlanTaskRepository(
    private val submitAsync: suspend (GenerateCookingPlanRequest) -> Response<CookingPlanAsyncAcceptedResponse>,
    private val getTask: suspend (String) -> Response<CookingPlanTaskResponse>,
    private val readPlan: suspend (String) -> CookingPlanResponse,
    private val cancelTask: suspend (String) -> Response<CookingPlanResponse>,
) {
    /**
     * Submits an async generation. A successful submission returns an accepted handle with
     * [CookingPlanAsyncAcceptedResponse.taskId]; when the backend answers 200 with a terminal
     * FAILED plan (submission itself failed), the returned handle has a null taskId and a
     * non-PROCESSING status, and [AsyncSubmitResult.TerminalFailed] is returned.
     * Any transport or HTTP rejection is surfaced as [AsyncSubmitFailureException].
     */
    suspend fun generateAsync(request: GenerateCookingPlanRequest): Result<AsyncSubmitResult> = runCatching {
        val response = submitAsync(request)
        if (response.isSuccessful && response.body() != null) {
            val body = response.body()!!
            if (body.taskId != null && body.status == "PROCESSING") {
                AsyncSubmitResult.Accepted(body.planId, body.taskId, body.location)
            } else {
                AsyncSubmitResult.TerminalFailed(body.planId, body.status)
            }
        } else {
            throw IllegalStateException("Async submission rejected with HTTP ${response.code()}")
        }
    }.recoverCatching { throwable ->
        if (throwable is AsyncSubmitFailureException) throw throwable
        throw AsyncSubmitFailureException(throwable.message ?: "Async submission failed", throwable)
    }

    /**
     * Polls the task endpoint every [pollIntervalMillis] until it returns 404, then reads the
     * terminal plan via [readPlan]. Each in-flight response is reported through [onProgress].
     */
    suspend fun pollUntilTerminal(
        planId: String,
        pollIntervalMillis: Long = 2000,
        onProgress: (CookingPlanTaskResponse) -> Unit = {},
    ): Result<CookingPlanResponse> = runCatching {
        while (true) {
            val response = getTask(planId)
            when {
                response.isSuccessful -> {
                    response.body()?.let(onProgress)
                    delay(pollIntervalMillis)
                }
                response.code() == COOKING_PLAN_TASK_NOT_FOUND ->
                    return@runCatching readPlan(planId)
                else ->
                    throw IllegalStateException("Unexpected task status HTTP ${response.code()}")
            }
        }
        error("Polling loop ended unexpectedly")
    }

    /** Cancels an in-flight task; 409 (not cancellable) is surfaced as [CookingPlanCancelConflictException]. */
    suspend fun cancel(planId: String): Result<CookingPlanResponse> {
        val response = cancelTask(planId)
        return when {
            response.isSuccessful -> {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(IllegalStateException("Cancel returned an empty body"))
            }
            response.code() == COOKING_PLAN_CANCEL_CONFLICT ->
                Result.failure(CookingPlanCancelConflictException())
            else -> Result.failure(IllegalStateException("Cancel failed with HTTP ${response.code()}"))
        }
    }
}

sealed interface AsyncSubmitResult {
    val planId: String?

    /** Accepted with an in-flight task handle; poll [CookingPlanTaskResponse] until 404 then read the plan. */
    data class Accepted(override val planId: String?, val taskId: String?, val location: String?) : AsyncSubmitResult

    /** Submission itself failed; the backend returned a terminal FAILED plan with 200. */
    data class TerminalFailed(override val planId: String?, val status: String?) : AsyncSubmitResult
}

class AsyncSubmitFailureException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class CookingPlanCancelConflictException : RuntimeException("The task is no longer cancellable (HTTP 409)")
