package com.foodmind.foodmind_android.feature.chat

import com.foodmind.foodmind_android.core.network.ChatMessageResponse
import com.foodmind.foodmind_android.core.network.ChatPageResponse
import com.foodmind.foodmind_android.core.network.ChatReferenceResponse
import com.foodmind.foodmind_android.core.network.ChatSessionPageResponse
import com.foodmind.foodmind_android.core.network.ChatSessionResponse
import com.foodmind.foodmind_android.core.network.ExploreItemResponse
import com.foodmind.foodmind_android.core.network.SearchPageResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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
class ChatViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun newConversationAttachesSharedSourceAndPreservesDraft() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository)

        viewModel.open(
            sessionId = null,
            initialDraft = "Summarise this source",
            pendingSource = PendingChatSource("CURATED_PLACE", "place-7"),
        )
        advanceUntilIdle()

        assertEquals("session-1", viewModel.state.value.sessionId)
        assertEquals("Summarise this source", viewModel.state.value.draft)
        assertEquals(listOf("PLACE" to "place-7"), repository.sharedSources)
        assertEquals("reference-1", viewModel.state.value.attachedReferences.single().id)
    }

    @Test
    fun sendIncludesReferencesAndClearsComposerAfterSuccess() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository)
        viewModel.open(null)
        advanceUntilIdle()
        repository.sharedReference = ChatReferenceResponse(id = "reference-9", title = "Cafe")
        viewModel.attach(ExploreItemResponse(sourceType = "PLACE", sourceId = "place-9"))
        advanceUntilIdle()

        viewModel.updateDraft("Compare this place")
        viewModel.send()
        advanceUntilIdle()

        assertEquals("Compare this place", repository.postedContent)
        assertEquals(listOf("reference-9"), repository.postedReferenceIds)
        assertEquals(listOf("USER", "ASSISTANT"), viewModel.state.value.messages.map { it.role })
        assertEquals("", viewModel.state.value.draft)
        assertTrue(viewModel.state.value.attachedReferences.isEmpty())
    }

    @Test
    fun loadMoreUsesCursorAndAppendsWithoutDuplicates() = runTest(dispatcher) {
        val first = ChatMessageResponse(id = "m1", role = "USER", content = "One")
        val second = ChatMessageResponse(id = "m2", role = "ASSISTANT", content = "Two")
        val repository = FakeChatRepository().apply {
            firstMessagePage = ChatPageResponse(listOf(first), nextCursor = "cursor-1", hasNext = true)
            nextMessagePage = ChatPageResponse(listOf(first, second), nextCursor = null, hasNext = false)
        }
        val viewModel = ChatViewModel(repository)
        viewModel.open("session-1")
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals("cursor-1", repository.requestedCursor)
        assertEquals(listOf("m1", "m2"), viewModel.state.value.messages.map { it.id })
        assertFalse(viewModel.state.value.hasMore)
        assertNull(viewModel.state.value.nextCursor)
    }

    @Test
    fun sourceFilterIsForwardedToAuthorisedSearch() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository)
        viewModel.open(null)
        advanceUntilIdle()

        viewModel.updateSearchQuery("noodles")
        viewModel.setSourceFilter(ChatSourceFilter.FOOD_RECORD)
        advanceUntilIdle()

        assertEquals("noodles", repository.searchQuery)
        assertEquals("FOOD_RECORD", repository.searchTypes)
    }
}

internal class FakeChatRepository : ChatRepository {
    var firstMessagePage = ChatPageResponse<ChatMessageResponse>()
    var nextMessagePage = ChatPageResponse<ChatMessageResponse>()
    var sharedReference = ChatReferenceResponse(id = "reference-1", title = "Source")
    val sharedSources = mutableListOf<Pair<String, String>>()
    var postedContent: String? = null
    var postedReferenceIds: List<String>? = null
    var postedUseSessionReferences: Boolean? = null
    var postFailuresRemaining = 0
    var postAttempts = 0
    var requestedCursor: String? = null
    var searchQuery: String? = null
    var searchTypes: String? = null
    var archivedSessionId: String? = null
    var sessionItems = listOf(ChatSessionResponse(id = "session-1", title = "Recent chat"))

    override suspend fun createSession(title: String?) = ChatSessionResponse(id = "session-1", title = title)
    override suspend fun sessions(page: Int) = ChatSessionPageResponse(items = sessionItems)
    override suspend fun session(sessionId: String) = ChatSessionResponse(id = sessionId, title = "Existing chat")
    override suspend fun archiveSession(sessionId: String) {
        archivedSessionId = sessionId
    }

    override suspend fun messages(sessionId: String, after: String?): ChatPageResponse<ChatMessageResponse> {
        requestedCursor = after
        return if (after == null) firstMessagePage else nextMessagePage
    }

    override suspend fun postMessage(
        sessionId: String,
        content: String,
        referenceIds: List<String>,
        useSessionReferences: Boolean,
    ): ChatMessageResponse {
        postAttempts += 1
        postedContent = content
        postedReferenceIds = referenceIds
        postedUseSessionReferences = useSessionReferences
        if (postFailuresRemaining > 0) {
            postFailuresRemaining -= 1
            throw java.io.IOException("offline")
        }
        return ChatMessageResponse(id = "assistant-1", role = "ASSISTANT", content = "Grounded answer")
    }

    override suspend fun search(query: String, types: String?): SearchPageResponse {
        searchQuery = query
        searchTypes = types
        return SearchPageResponse()
    }

    override suspend fun shareReference(
        sessionId: String,
        sourceType: String,
        sourceId: String,
    ): ChatReferenceResponse {
        sharedSources += sourceType to sourceId
        return sharedReference
    }
}
