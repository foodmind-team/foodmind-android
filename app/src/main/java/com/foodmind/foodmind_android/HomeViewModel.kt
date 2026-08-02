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
            listOf("海南鸡饭", "咖喱叻沙", "姜葱豆腐拌面")[resultIndex % 3]
        } else "今晚的姜味味噌三文鱼饭"

    val resultMeta: String
        get() = recommendation?.meta ?: if (mode == HomeMode.RECOMMEND) "本地 · 堂食/外卖 · $$ · 12 分钟" else "4 人份 · 28 分钟 · 2 项待采购"

    val resultReason: String
        get() = recommendation?.reason ?: if (mode == HomeMode.RECOMMEND) "结合群组口味、距离、预算和今晚的约束，只给出一个有理由的答案。" else "优先使用即将过期的食材，并按资源和安全条件排出执行顺序。"
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

    fun generateRecommendation() {
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
                            is com.foodmind.foodmind_android.domain.model.RecommendationFailure.Http -> "服务暂时不可用（${failure.statusCode}）"
                            is com.foodmind.foodmind_android.domain.model.RecommendationFailure.Transport -> "网络连接失败，请检查网络后重试"
                        }
                    } ?: "推荐生成失败，请稍后重试"
                    _state.update { it.copy(isGenerating = false, hasResult = false, errorMessage = message) }
                }
        }
    }

    fun tryAnother() {
        if (recommendationRepository == null) {
            _state.update { it.copy(resultIndex = it.resultIndex + 1, hasResult = true) }
        } else {
            generateRecommendation()
        }
    }
}
