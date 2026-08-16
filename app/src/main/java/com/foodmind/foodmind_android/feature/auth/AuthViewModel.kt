package com.foodmind.foodmind_android.feature.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodmind.foodmind_android.R
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class AuthUiState(
    val registering: Boolean = false,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val privacyConsentAccepted: Boolean = false,
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    @param:StringRes val errorMessageRes: Int? = null,
)

class AuthViewModel : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()
    private var apiClient: FoodMindApiClient? = null

    fun setApiClient(apiClient: FoodMindApiClient) {
        this.apiClient = apiClient
    }

    fun updateEmail(email: String) = _state.update { it.copy(email = email, errorMessageRes = null) }
    fun updateDisplayName(displayName: String) = _state.update { it.copy(displayName = displayName, errorMessageRes = null) }
    fun setRegistering(registering: Boolean) = _state.update {
        it.copy(registering = registering, privacyConsentAccepted = false, errorMessageRes = null)
    }

    fun updatePassword(password: String) = _state.update { it.copy(password = password, errorMessageRes = null) }
    fun updatePrivacyConsent(accepted: Boolean) = _state.update {
        it.copy(privacyConsentAccepted = accepted, errorMessageRes = null)
    }

    fun login() {
        val current = _state.value
        if (current.email.isBlank() || !current.email.contains('@')) {
            _state.update { it.copy(errorMessageRes = R.string.error_valid_email) }
            return
        }
        if (current.password.length < 8) {
            _state.update { it.copy(errorMessageRes = R.string.error_password_min_length) }
            return
        }
        if (current.registering && current.displayName.trim().isEmpty()) {
            _state.update { it.copy(errorMessageRes = R.string.error_display_name_required) }
            return
        }
        if (current.registering && !current.privacyConsentAccepted) {
            _state.update { it.copy(errorMessageRes = R.string.error_privacy_consent_required) }
            return
        }
        val client = apiClient ?: run {
            _state.update { it.copy(errorMessageRes = R.string.error_auth_service_unavailable) }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessageRes = null) }
        viewModelScope.launch {
            runCatching {
                if (current.registering) {
                    client.register(current.email.trim(), current.displayName.trim(), current.password)
                } else client.login(current.email.trim(), current.password)
            }
                .onSuccess { _state.update { it.copy(isLoading = false, isAuthenticated = true) } }
                .onFailure { error ->
                    val messageRes = if (error is HttpException && error.code() == 401) {
                        R.string.error_invalid_credentials
                    } else {
                        R.string.error_sign_in_failed
                    }
                    _state.update { it.copy(isLoading = false, errorMessageRes = messageRes) }
                }
        }
    }
}
