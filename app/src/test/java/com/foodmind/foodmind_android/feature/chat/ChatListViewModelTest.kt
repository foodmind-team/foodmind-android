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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {
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
    fun refreshThenConfirmedArchiveRemovesSession() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val store = InMemoryChatDraftStore().apply { save("session-1", "Unsent draft") }
        val viewModel = ChatListViewModel(repository, store)

        viewModel.refresh()
        advanceUntilIdle()
        val session = viewModel.state.value.sessions.single()
        viewModel.requestArchive(session)
        assertEquals(session, viewModel.state.value.archiveCandidate)

        viewModel.confirmArchive()
        advanceUntilIdle()

        assertEquals("session-1", repository.archivedSessionId)
        assertTrue(viewModel.state.value.sessions.isEmpty())
        assertNull(viewModel.state.value.archiveCandidate)
        assertEquals("", store.load("session-1"))
    }

    @Test
    fun queryFiltersConversationTitlesLocally() = runTest(dispatcher) {
        val repository = FakeChatRepository().apply {
            sessionItems = listOf(
                com.foodmind.foodmind_android.core.network.ChatSessionResponse(id = "session-1", title = "Breakfast notes"),
                com.foodmind.foodmind_android.core.network.ChatSessionResponse(id = "session-2", title = "Dinner ideas"),
            )
        }
        val viewModel = ChatListViewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.updateQuery("dinner")

        assertEquals(listOf("session-2"), viewModel.state.value.visibleSessions().map { it.id })
    }
}
