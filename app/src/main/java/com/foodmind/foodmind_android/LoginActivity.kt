package com.foodmind.foodmind_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindNetwork
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.feature.auth.AuthUiState
import com.foodmind.foodmind_android.feature.auth.AuthViewModel

class LoginActivity : ComponentActivity() {
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        val apiClient = FoodMindApiClient(
            FoodMindNetwork.createApi(BuildConfig.FOODMIND_API_BASE_URL, FoodMindSession.tokenStore),
            FoodMindSession.tokenStore,
        )
        viewModel.setApiClient(apiClient)
        setContent {
            FoodMindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.isAuthenticated) { if (state.isAuthenticated) { setResult(RESULT_OK); finish() } }
                LoginScreen(
                    state = state,
                    onEmailChange = viewModel::updateEmail,
                    onPasswordChange = viewModel::updatePassword,
                    onDisplayNameChange = viewModel::updateDisplayName,
                    onPrivacyConsentChange = viewModel::updatePrivacyConsent,
                    onOpenPrivacyPolicy = viewModel::openPrivacyPolicy,
                    onClosePrivacyPolicy = viewModel::closePrivacyPolicy,
                    onModeChange = viewModel::setRegistering,
                    onLogin = viewModel::login,
                    onBack = ::finish,
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPrivacyConsentChange: (Boolean) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onClosePrivacyPolicy: () -> Unit,
    onModeChange: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(if (state.registering) R.string.login_create_account else R.string.login_title), color = FoodMindGreenDark)
        Text(stringResource(R.string.login_support), color = FoodMindMuted)
        if (state.registering) OutlinedTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            label = { Text(stringResource(R.string.label_display_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.label_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.label_password)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.registering) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.privacyConsentAccepted,
                        role = Role.Checkbox,
                        onValueChange = onPrivacyConsentChange,
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = state.privacyConsentAccepted,
                    onCheckedChange = null,
                )
                Text(
                    text = stringResource(R.string.privacy_collection_consent),
                    color = FoodMindMuted,
                )
            }
            Text(
                text = stringResource(R.string.privacy_policy_link),
                color = FoodMindGreenDark,
                style = MaterialTheme.typography.labelLarge,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(start = 32.dp)
                    .clickable { onOpenPrivacyPolicy() },
            )
        }
        state.errorMessageRes?.let { Text(stringResource(it), color = FoodMindCoral) }
        Button(
            onClick = onLogin,
            enabled = !state.isLoading && (!state.registering || state.privacyConsentAccepted),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoading) CircularProgressIndicator()
            else Text(stringResource(if (state.registering) R.string.login_register_and_sign_in else R.string.login_sign_in))
        }
        OutlinedButton(onClick = { onModeChange(!state.registering) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.registering) R.string.login_existing_account else R.string.login_new_account))
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_back)) }

        if (state.showPrivacyPolicy) {
            AlertDialog(
                onDismissRequest = onClosePrivacyPolicy,
                title = { Text(stringResource(R.string.privacy_policy_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.privacy_policy_body), color = FoodMindMuted)
                    }
                },
                confirmButton = {
                    TextButton(onClick = onClosePrivacyPolicy) { Text(stringResource(R.string.privacy_policy_close)) }
                },
            )
        }
    }
}
