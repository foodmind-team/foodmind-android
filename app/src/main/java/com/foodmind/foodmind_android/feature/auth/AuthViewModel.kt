package com.foodmind.foodmind_android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val errorMessage: String? = null,
)

class AuthViewModel : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()
    private var apiClient: FoodMindApiClient? = null

    fun setApiClient(apiClient: FoodMindApiClient) {
        this.apiClient = apiClient
    }

    fun updateEmail(email: String) = _state.update { it.copy(email = email, errorMessage = null) }

    fun updatePassword(password: String) = _state.update { it.copy(password = password, errorMessage = null) }

    fun login() {
        val current = _state.value
        if (current.email.isBlank() || !current.email.contains('@')) {
            _state.update { it.copy(errorMessage = "请输入有效邮箱") }
            return
        }
        if (current.password.length < 8) {
            _state.update { it.copy(errorMessage = "密码至少需要 8 位") }
            return
        }
        val client = apiClient ?: run {
            _state.update { it.copy(errorMessage = "登录服务未配置") }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { client.login(current.email.trim(), current.password) }
                .onSuccess { _state.update { it.copy(isLoading = false, isAuthenticated = true) } }
                .onFailure { error ->
                    val message = if (error is HttpException && error.code() == 401) {
                        "邮箱或密码不正确"
                    } else {
                        "登录失败，请稍后重试"
                    }
                    _state.update { it.copy(isLoading = false, errorMessage = message) }
                }
        }
    }
}
