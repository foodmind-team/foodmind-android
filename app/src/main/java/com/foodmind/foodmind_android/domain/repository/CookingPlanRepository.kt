package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.CookingPlanStatus
import com.foodmind.foodmind_android.CookingTask
import com.foodmind.foodmind_android.core.network.GenerateCookingPlanRequest
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import retrofit2.HttpException

data class CookingPlanPayload(
    val status: CookingPlanStatus,
    val recipeNames: String,
    val tasks: List<CookingTask>,
    val warning: String? = null,
)

interface CookingPlanRepository {
    suspend fun generate(request: GenerateCookingPlanRequest): Result<CookingPlanPayload>
}

class CookingPlanRepositoryImpl(
    private val apiClient: FoodMindApiClient,
) : CookingPlanRepository {
    override suspend fun generate(request: GenerateCookingPlanRequest): Result<CookingPlanPayload> = runCatching {
        val response = apiClient.generateCookingPlan(request)
        val status = when (response.status) {
            "SUCCEEDED", "FALLBACK_SUCCEEDED" -> CookingPlanStatus.READY
            "NO_VALID_RECIPE" -> CookingPlanStatus.INFEASIBLE
            "CREATED", "PROCESSING" -> CookingPlanStatus.NEEDS_CONFIRMATION
            else -> CookingPlanStatus.FAILED
        }
        val names = response.inputs.mapNotNull { it.ingredientName }
            .ifEmpty { response.ingredients.map { it.ingredientName } }
            .distinct()
            .joinToString("、")
            .ifBlank { "烹饪计划" }
        CookingPlanPayload(
            status = status,
            recipeNames = names,
            tasks = if (status == CookingPlanStatus.READY) response.steps.map { step ->
                CookingTask(
                    id = "step-${step.stepNo ?: 0}",
                    label = step.instruction,
                    window = "第 ${step.stepNo ?: 0} 步",
                )
            } else emptyList(),
            warning = response.warnings.mapNotNull { it.message }.firstOrNull()
                ?: response.failureCode,
        )
    }.recoverCatching { throwable ->
        throw CookingPlanFailureException(
            if (throwable is HttpException) "服务暂时不可用（${throwable.code()}）"
            else "网络连接失败，请检查网络后重试",
        )
    }
}

class CookingPlanFailureException(message: String) : RuntimeException(message)
