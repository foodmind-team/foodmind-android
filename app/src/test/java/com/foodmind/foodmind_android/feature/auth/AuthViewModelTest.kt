package com.foodmind.foodmind_android.feature.auth

import com.foodmind.foodmind_android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthViewModelTest {
    @Test
    fun rejectsInvalidEmailBeforeNetworkCall() {
        val viewModel = AuthViewModel()

        viewModel.updateEmail("not-an-email")
        viewModel.updatePassword("password123")
        viewModel.login()

        assertEquals(R.string.error_valid_email, viewModel.state.value.errorMessageRes)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun rejectsShortPasswordBeforeNetworkCall() {
        val viewModel = AuthViewModel()

        viewModel.updateEmail("demo@example.com")
        viewModel.updatePassword("short")
        viewModel.login()

        assertEquals(R.string.error_password_min_length, viewModel.state.value.errorMessageRes)
        assertFalse(viewModel.state.value.isLoading)
    }
}
