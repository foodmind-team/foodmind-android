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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRecoveryViewModelTest {
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
    fun storedDraftIsRestoredAndExplicitDraftWins() = runTest(dispatcher) {
        val store = InMemoryChatDraftStore().apply { save("session-1", "Saved draft") }
        val restored = ChatViewModel(FakeChatRepository(), store)

        restored.open("session-1")
        advanceUntilIdle()

        assertEquals("Saved draft", restored.state.value.draft)

        val explicit = ChatViewModel(FakeChatRepository(), store)
        explicit.open("session-1", initialDraft = "Shared prompt")
        advanceUntilIdle()

        assertEquals("Shared prompt", explicit.state.value.draft)
        assertEquals("Shared prompt", store.load("session-1"))
    }

    @Test
    fun failedSendKeepsDraftAndRetryClearsItAfterSuccess() = runTest(dispatcher) {
        val repository = FakeChatRepository().apply { postFailuresRemaining = 1 }
        val store = InMemoryChatDraftStore()
        val viewModel = ChatViewModel(repository, store)
        viewModel.open(null)
        advanceUntilIdle()
        viewModel.updateDraft("Try this again")

        viewModel.send()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSending)
        assertEquals(OutgoingMessageStatus.FAILED, viewModel.state.value.outgoingMessage?.status)
        assertEquals("Try this again", viewModel.state.value.draft)
        assertEquals("Try this again", store.load("session-1"))

        viewModel.retryFailedMessage()
        advanceUntilIdle()

        assertEquals(2, repository.postAttempts)
        assertEquals(1, repository.postedIdempotencyKeys.distinct().size)
        assertNull(viewModel.state.value.outgoingMessage)
        assertEquals("", viewModel.state.value.draft)
        assertEquals("", store.load("session-1"))
        assertEquals(listOf("USER", "ASSISTANT"), viewModel.state.value.messages.map { it.role })
    }

    @Test
    fun failedOutgoingRestoresAcrossProcessAndRetryUsesSameIdempotencyKey() = runTest(dispatcher) {
        val repository = FakeChatRepository().apply { postFailuresRemaining = 1 }
        val store = InMemoryChatDraftStore()
        val firstViewModel = ChatViewModel(repository, store)
        firstViewModel.open(null)
        advanceUntilIdle()
        firstViewModel.updateDraft("Retry after restart")

        firstViewModel.send()
        advanceUntilIdle()

        val failedKey = firstViewModel.state.value.outgoingMessage?.idempotencyKey
        assertEquals(failedKey, store.loadOutgoing("session-1")?.idempotencyKey)

        val restoredViewModel = ChatViewModel(repository, store)
        restoredViewModel.open("session-1")
        advanceUntilIdle()

        assertEquals(OutgoingMessageStatus.FAILED, restoredViewModel.state.value.outgoingMessage?.status)
        assertEquals(failedKey, restoredViewModel.state.value.outgoingMessage?.idempotencyKey)
        assertEquals("Retry after restart", restoredViewModel.state.value.draft)

        restoredViewModel.send()
        advanceUntilIdle()

        assertEquals(2, repository.postAttempts)
        assertEquals(1, repository.postedIdempotencyKeys.distinct().size)
        assertNull(restoredViewModel.state.value.outgoingMessage)
        assertNull(store.loadOutgoing("session-1"))
        assertEquals("", store.load("session-1"))
    }

    @Test
    fun removedSourcesSendAnExplicitEmptyScope() = runTest(dispatcher) {
        val repository = FakeChatRepository().apply {
            sharedReference = com.foodmind.foodmind_android.core.network.ChatReferenceResponse(
                id = "reference-9",
                sourceType = "PLACE",
                sourceId = "place-9",
                title = "Cafe",
            )
        }
        val viewModel = ChatViewModel(repository, InMemoryChatDraftStore())
        viewModel.open(null)
        advanceUntilIdle()
        viewModel.attach(
            com.foodmind.foodmind_android.core.network.ExploreItemResponse(
                sourceType = "PLACE",
                sourceId = "place-9",
            ),
        )
        advanceUntilIdle()
        assertTrue(viewModel.state.value.attachedReferences.isNotEmpty())

        viewModel.removeReference("reference-9")
        viewModel.updateDraft("Answer without sources")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.postedReferenceIds)
        assertEquals(false, repository.postedUseSessionReferences)
    }
}

internal class InMemoryChatDraftStore : ChatDraftStore {
    private val drafts = mutableMapOf<String, String>()
    private val outgoingMessages = mutableMapOf<String, OutgoingChatMessage>()

    override fun load(sessionId: String): String = drafts[sessionId].orEmpty()

    override fun save(sessionId: String, draft: String) {
        if (draft.isBlank()) clear(sessionId) else drafts[sessionId] = draft
    }

    override fun clear(sessionId: String) {
        drafts.remove(sessionId)
    }

    override fun loadOutgoing(sessionId: String): OutgoingChatMessage? = outgoingMessages[sessionId]

    override fun saveOutgoing(sessionId: String, outgoing: OutgoingChatMessage) {
        outgoingMessages[sessionId] = outgoing
    }

    override fun clearOutgoing(sessionId: String) {
        outgoingMessages.remove(sessionId)
    }
}
