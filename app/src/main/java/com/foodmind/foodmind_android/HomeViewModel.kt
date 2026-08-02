package com.foodmind.foodmind_android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodmind.foodmind_android.core.network.GenerateRecommendationRequest
import com.foodmind.foodmind_android.domain.model.Recommendation
import com.foodmind.foodmind_android.domain.repository.RecommendationRepository
import com.foodmind.foodmind_android.domain.repository.RecommendationFailureException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class HomeMode { RECOMMEND, COOKING }

data class HomeUiState(
    val mode: HomeMode = HomeMode.RECOMMEND,
    val isGenerating: Boolean = false,
    val resultIndex: Int = 0,
    val hasResult: Boolean = false,
    val errorMessage: String? = null,
    val recommendation: Recommendation? = null,
) {
    val resultTitle: String
        get() = recommendation?.title ?: if (mode == HomeMode.RECOMMEND) {
            listOf("Hainanese chicken rice", "Curry laksa", "Ginger scallion tofu noodles")[resultIndex % 3]
        } else "Tonight’s ginger miso salmon bowl"

    val resultMeta: String
        get() = recommendation?.meta ?: if (mode == HomeMode.RECOMMEND) "Local · dine-in/delivery · $$ · 12 minutes" else "4 servings · 28 minutes · 2 items to buy"

    val resultReason: String
        get() = recommendation?.reason ?: if (mode == HomeMode.RECOMMEND) "Combines group taste, distance, budget, and tonight’s constraints into one well-reasoned answer." else "Prioritises ingredients that expire soon and orders the work around available resources and safety constraints."
}

class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var recommendationRepository: RecommendationRepository? = null
    private var lastRequest = GenerateRecommendationRequest()

    fun setRecommendationRepository(repository: RecommendationRepository) {
        recommendationRepository = repository
    }

    fun selectMode(mode: HomeMode) {
        _state.update { it.copy(mode = mode, hasResult = false, isGenerating = false, errorMessage = null, recommendation = null) }
    }

    fun generateRecommendation(request: GenerateRecommendationRequest = lastRequest) {
        lastRequest = request
        val repository = recommendationRepository
        if (repository == null) {
            _state.update { it.copy(isGenerating = false, hasResult = true, errorMessage = null) }
            return
        }
        _state.update { it.copy(isGenerating = true, hasResult = false, errorMessage = null) }
        viewModelScope.launch {
            repository.generate(lastRequest)
                .onSuccess { recommendation ->
                    _state.update {
                        it.copy(isGenerating = false, hasResult = true, recommendation = recommendation, errorMessage = null)
                    }
                }
                .onFailure { error ->
                    val message = (error as? RecommendationFailureException)?.failure?.let { failure ->
                        when (failure) {
                            is com.foodmind.foodmind_android.domain.model.RecommendationFailure.Http -> "Service is temporarily unavailable (${failure.statusCode}）"
                            is com.foodmind.foodmind_android.domain.model.RecommendationFailure.Transport -> "Network connection failed. Check your connection and try again."
                        }
                    } ?: "Could not generate recommendations. Please try again later."
                    _state.update { it.copy(isGenerating = false, hasResult = false, errorMessage = message) }
                }
        }
    }

    fun tryAnother() {
        if (recommendationRepository == null) {
            _state.update { it.copy(resultIndex = it.resultIndex + 1, hasResult = true) }
        } else {
            generateRecommendation(lastRequest.copy(parentSessionId = _state.value.recommendation?.sessionId))
        }
    }
}
