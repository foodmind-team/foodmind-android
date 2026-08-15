package com.foodmind.foodmind_android.feature.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatIdempotencyViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun retryReusesKeyButEditingCreatesANewOperationKey() = runTest(dispatcher) {
        val retryRepository = FakeChatRepository().apply { postFailuresRemaining = 1 }
        val retryViewModel = ChatViewModel(retryRepository, InMemoryChatDraftStore())
        retryViewModel.open(null)
        advanceUntilIdle()
        retryViewModel.updateDraft("Explain tofu")
        retryViewModel.send()
        advanceUntilIdle()
        retryViewModel.retryFailedMessage()
        advanceUntilIdle()

        assertEquals(2, retryRepository.postedIdempotencyKeys.size)
        assertEquals(
            retryRepository.postedIdempotencyKeys.first(),
            retryRepository.postedIdempotencyKeys.last(),
        )

        val editRepository = FakeChatRepository().apply { postFailuresRemaining = 1 }
        val editViewModel = ChatViewModel(editRepository, InMemoryChatDraftStore())
        editViewModel.open(null)
        advanceUntilIdle()
        editViewModel.updateDraft("Explain tofu")
        editViewModel.send()
        advanceUntilIdle()
        editViewModel.editFailedMessage()
        editViewModel.updateDraft("Explain tempeh")
        editViewModel.send()
        advanceUntilIdle()

        assertEquals(2, editRepository.postedIdempotencyKeys.size)
        assertNotEquals(
            editRepository.postedIdempotencyKeys.first(),
            editRepository.postedIdempotencyKeys.last(),
        )
    }
}
