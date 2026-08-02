package com.foodmind.foodmind_android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.domain.repository.RecipeDraftStore
import com.foodmind.foodmind_android.domain.repository.UserRecipeRepository

data class RecipeOption(
    val id: String,
    val name: String,
    val meta: String,
)

data class RecipeSelectionUiState(
    val recipes: List<RecipeOption> = RecipeDraftStore.list().map { it.toOption() },
    val selectedIds: Set<String> = emptySet(),
    val status: CookingPlanStatus = CookingPlanStatus.READY,
    val remoteLoading: Boolean = false,
    val remoteError: String? = null,
) {
    val canGenerate: Boolean get() = selectedIds.isNotEmpty()
    val selectedNames: String
        get() = recipes.filter { it.id in selectedIds }.joinToString("、") { it.name }
}

class RecipeSelectionViewModel : ViewModel() {
    private val _state = MutableStateFlow(RecipeSelectionUiState())
    val state: StateFlow<RecipeSelectionUiState> = _state.asStateFlow()

    fun refresh() {
        val available = RecipeDraftStore.list().map { it.toOption() }
        _state.update { current ->
            current.copy(recipes = available, selectedIds = current.selectedIds intersect available.map { it.id }.toSet())
        }
    }

    fun loadRemote(client: FoodMindApiClient) {
        val repository = UserRecipeRepository(client)
        _state.update { it.copy(remoteLoading = true, remoteError = null) }
        viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { remote ->
                    RecipeDraftStore.replaceFromRemote(remote)
                    refresh()
                    _state.update { it.copy(remoteLoading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(remoteLoading = false, remoteError = error.message ?: "菜谱服务暂不可用") }
                }
        }
    }

    fun toggleRecipe(id: String) {
        _state.update { current ->
            val next = current.selectedIds.toMutableSet()
            if (!next.add(id)) next.remove(id)
            current.copy(selectedIds = next)
        }
    }

    fun setStatus(status: CookingPlanStatus) {
        _state.update { it.copy(status = status) }
    }

    fun deleteRecipe(id: String) {
        RecipeDraftStore.delete(id)
        refresh()
    }

    fun deleteRecipeRemote(client: FoodMindApiClient, id: String) {
        viewModelScope.launch {
            runCatching { UserRecipeRepository(client).delete(id) }
                .onSuccess { deleteRecipe(id) }
                .onFailure { _state.update { it.copy(remoteError = it.remoteError ?: "删除菜谱失败，服务端未确认") } }
        }
    }
}

private fun com.foodmind.foodmind_android.domain.repository.RecipeDraft.toOption() =
    RecipeOption(id, name, "$servings 人份 · $minutes 分钟")
