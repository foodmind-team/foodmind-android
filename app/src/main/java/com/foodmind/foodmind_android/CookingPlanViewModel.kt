package com.foodmind.foodmind_android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodmind.foodmind_android.core.network.CookingIngredientRequest
import com.foodmind.foodmind_android.core.network.GenerateCookingPlanRequest
import com.foodmind.foodmind_android.domain.repository.CookingPlanFailureException
import com.foodmind.foodmind_android.domain.repository.CookingPlanRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class CookingPlanStatus { READY, NEEDS_CONFIRMATION, INFEASIBLE, FAILED }

data class CookingTask(
    val id: String,
    val label: String,
    val window: String,
)

data class CookingPlanUiState(
    val status: CookingPlanStatus = CookingPlanStatus.READY,
    val recipeNames: String = "",
    val statusMessage: String = "",
    val tasks: List<CookingTask> = emptyList(),
    val completedTaskIds: Set<String> = emptySet(),
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
) {
    val completedCount: Int get() = completedTaskIds.size
    val progressPercent: Int
        get() = if (tasks.isEmpty()) 0 else completedCount * 100 / tasks.size
}

/**
 * Screen-level UDF state for the cooking timeline. The fixture repository can
 * later be replaced by the Spring Boot rich-plan repository without changing
 * the Activity or task controls (C-03).
 */
class CookingPlanViewModel : ViewModel() {
    private val _state = MutableStateFlow(CookingPlanUiState())
    val state: StateFlow<CookingPlanUiState> = _state.asStateFlow()
    private var repository: CookingPlanRepository? = null

    fun setRepository(repository: CookingPlanRepository) {
        this.repository = repository
    }

    fun load(recipeNames: String, status: CookingPlanStatus = CookingPlanStatus.READY) {
        if (_state.value.recipeNames == recipeNames && _state.value.status == status) return
        val tasks = if (status == CookingPlanStatus.READY) listOf(
            CookingTask("t1", "准备食材：洗净蔬菜，给三文鱼擦干并调味", "0–5 分钟"),
            CookingTask("t2", "烤箱预热至 200°C，同时淘洗并煮米饭", "5–15 分钟"),
            CookingTask("t3", "烤三文鱼与蔬菜，制作姜味味噌酱汁", "15–25 分钟"),
            CookingTask("t4", "装盘、淋酱并确认过敏原提示", "25–28 分钟"),
        ) else emptyList()
        _state.value = CookingPlanUiState(
            status = status,
            recipeNames = recipeNames,
            statusMessage = status.message(),
            tasks = tasks,
        )
    }

    fun generate(recipeNames: String, recipeIds: List<String> = emptyList()) {
        val activeRepository = repository ?: run {
            load(recipeNames, CookingPlanStatus.READY)
            return
        }
        _state.update { it.copy(isGenerating = true, errorMessage = null, recipeNames = recipeNames) }
        val ingredients = recipeNames.split("、", ",", "，")
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { CookingIngredientRequest(ingredientName = it, quantity = 1.0, unit = "份", source = "MANUAL") }
        viewModelScope.launch {
            activeRepository.generate(
                GenerateCookingPlanRequest(ingredients = ingredients.ifEmpty {
                    listOf(CookingIngredientRequest("用户选择的菜谱", 1.0, "份", "MANUAL"))
                }, recipeIds = recipeIds),
            ).onSuccess { payload ->
                _state.value = CookingPlanUiState(
                    status = payload.status,
                    recipeNames = recipeNames,
                    statusMessage = payload.warning ?: payload.status.message(),
                    tasks = payload.tasks,
                )
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = (error as? CookingPlanFailureException)?.message
                            ?: "烹饪计划生成失败，请稍后重试",
                    )
                }
            }
        }
    }

    fun toggleTask(taskId: String) {
        _state.update { current ->
            val next = current.completedTaskIds.toMutableSet()
            if (!next.add(taskId)) next.remove(taskId)
            current.copy(completedTaskIds = next)
        }
    }

    private fun CookingPlanStatus.message(): String = when (this) {
        CookingPlanStatus.READY -> "计划已通过资源与安全校验，可以开始执行。"
        CookingPlanStatus.NEEDS_CONFIRMATION -> "部分份数和工具条件需要确认，确认后才会生成可执行步骤。"
        CookingPlanStatus.INFEASIBLE -> "当前食材无法满足所选菜谱，请替换食材或移除一道菜。"
        CookingPlanStatus.FAILED -> "排程服务暂时不可用，请稍后重试；本次请求没有生成执行步骤。"
    }
}
