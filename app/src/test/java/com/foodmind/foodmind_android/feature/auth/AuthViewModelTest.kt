package com.foodmind.foodmind_android.feature.auth

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

        assertEquals("请输入有效邮箱", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun rejectsShortPasswordBeforeNetworkCall() {
        val viewModel = AuthViewModel()

        viewModel.updateEmail("demo@example.com")
        viewModel.updatePassword("short")
        viewModel.login()

        assertEquals("密码至少需要 8 位", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isLoading)
    }
}
