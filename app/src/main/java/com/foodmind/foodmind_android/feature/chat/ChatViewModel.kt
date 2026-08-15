package com.foodmind.foodmind_android.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodmind.foodmind_android.core.network.ChatMessageResponse
import com.foodmind.foodmind_android.core.network.ChatMessageSourceResponse
import com.foodmind.foodmind_android.core.network.ChatReferenceResponse
import com.foodmind.foodmind_android.core.network.ExploreItemResponse
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val draftStore: ChatDraftStore = NoOpChatDraftStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var started = false
    private var searchJob: Job? = null

    fun open(
        sessionId: String?,
        initialDraft: String = "",
        pendingSource: PendingChatSource? = null,
    ) {
        if (started) return
        started = true
        _state.update { it.copy(isLoading = true, draft = initialDraft.take(CHAT_MESSAGE_LIMIT)) }
        viewModelScope.launch {
            runCatching {
                val session = sessionId?.let { repository.session(it) }
                    ?: repository.createSession("FoodMind Chat")
                val id = session.id ?: error("Chat session has no id")
                val page = repository.messages(id)
                val storedOutgoing = draftStore.loadOutgoing(id)
                val outgoingAlreadyCompleted = storedOutgoing != null &&
                    hasCompletedOutgoing(page.items, storedOutgoing)
                val restoredOutgoing = storedOutgoing?.takeUnless { outgoingAlreadyCompleted }
                if (outgoingAlreadyCompleted) {
                    draftStore.clearOutgoing(id)
                    draftStore.clear(id)
                }
                val restoredDraft = initialDraft.takeIf(String::isNotBlank)
                    ?: if (outgoingAlreadyCompleted) "" else draftStore.load(id)
                val reference = pendingSource?.let { source ->
                    val supportedType = normaliseChatSourceType(source.sourceType)
                        ?: error("Unsupported chat source")
                    repository.shareReference(id, supportedType, source.sourceId)
                }
                OpenedChat(
                    id = id,
                    title = session.title ?: "FoodMind Chat",
                    messages = page.items,
                    nextCursor = page.nextCursor,
                    hasMore = page.hasNext,
                    reference = reference,
                    draft = restoredDraft.take(CHAT_MESSAGE_LIMIT),
                    outgoing = restoredOutgoing?.copy(status = OutgoingMessageStatus.FAILED),
                )
            }.onSuccess { opened ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        sessionId = opened.id,
                        title = opened.title,
                        messages = opened.messages,
                        nextCursor = opened.nextCursor,
                        hasMore = opened.hasMore,
                        draft = opened.draft,
                        attachedReferences = listOfNotNull(opened.reference),
                        outgoingMessage = opened.outgoing,
                        errorMessage = null,
                        requiresLogin = false,
                    )
                }
                if (opened.draft.isNotBlank()) draftStore.save(opened.id, opened.draft)
            }.onFailure { failure ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = failure.toChatMessage("Could not load this conversation."),
                        requiresLogin = failure.requiresLogin(),
                    )
                }
            }
        }
    }

    fun retry(sessionId: String?, initialDraft: String = "", pendingSource: PendingChatSource? = null) {
        started = false
        open(sessionId, initialDraft, pendingSource)
    }

    fun updateDraft(value: String) {
        val bounded = value.take(CHAT_MESSAGE_LIMIT)
        _state.update { it.copy(draft = bounded) }
        _state.value.sessionId?.let { draftStore.save(it, bounded) }
    }

    fun send() {
        val snapshot = _state.value
        val sessionId = snapshot.sessionId ?: return
        val content = snapshot.draft.trim()
        if (content.isBlank() || snapshot.isSending || content.length > CHAT_MESSAGE_LIMIT) return
        val failedOutgoing = snapshot.outgoingMessage?.takeIf {
            it.status == OutgoingMessageStatus.FAILED && it.content == content
        }
        if (failedOutgoing != null) {
            return submit(sessionId, failedOutgoing)
        }
        val outgoing = OutgoingChatMessage(
            localId = "local-${UUID.randomUUID()}",
            idempotencyKey = UUID.randomUUID().toString(),
            content = content,
            referenceIds = snapshot.attachedReferences.mapNotNull(ChatReferenceResponse::id),
            referenceTitles = snapshot.attachedReferences.map {
                it.title ?: chatSourceTypeLabel(it.sourceType)
            },
            status = OutgoingMessageStatus.SENDING,
        )
        submit(sessionId, outgoing)
    }

    fun retryFailedMessage() {
        val snapshot = _state.value
        val sessionId = snapshot.sessionId ?: return
        val outgoing = snapshot.outgoingMessage
            ?.takeIf { it.status == OutgoingMessageStatus.FAILED }
            ?: return
        submit(sessionId, outgoing)
    }

    fun editFailedMessage() {
        val snapshot = _state.value
        val outgoing = snapshot.outgoingMessage ?: return
        _state.update { it.copy(draft = outgoing.content, outgoingMessage = null, errorMessage = null) }
        snapshot.sessionId?.let {
            draftStore.save(it, outgoing.content)
            draftStore.clearOutgoing(it)
        }
    }

    fun dismissFailedMessage() {
        val sessionId = _state.value.sessionId
        _state.update { it.copy(outgoingMessage = null, errorMessage = null) }
        sessionId?.let(draftStore::clearOutgoing)
    }

    private fun submit(sessionId: String, outgoing: OutgoingChatMessage) {
        if (_state.value.isSending) return
        _state.update {
            it.copy(
                isSending = true,
                outgoingMessage = outgoing.copy(status = OutgoingMessageStatus.SENDING),
                errorMessage = null,
                requiresLogin = false,
            )
        }
        draftStore.saveOutgoing(sessionId, outgoing.copy(status = OutgoingMessageStatus.FAILED))
        viewModelScope.launch {
            runCatching {
                repository.postMessage(
                    sessionId = sessionId,
                    idempotencyKey = outgoing.idempotencyKey,
                    content = outgoing.content,
                    referenceIds = outgoing.referenceIds,
                    useSessionReferences = false,
                )
            }.onSuccess { assistantMessage ->
                val optimisticUser = ChatMessageResponse(
                    id = outgoing.localId,
                    sessionId = sessionId,
                    role = "USER",
                    content = outgoing.content,
                )
                draftStore.clear(sessionId)
                draftStore.clearOutgoing(sessionId)
                _state.update { state ->
                    val assistantExists = assistantMessage.id != null &&
                        state.messages.any { it.id == assistantMessage.id }
                    val userExists = state.messages.lastOrNull()?.let {
                        it.role == "USER" && it.content == outgoing.content
                    } == true
                    val mergedMessages = buildList {
                        addAll(state.messages)
                        if (!userExists) add(optimisticUser)
                        if (!assistantExists) add(assistantMessage)
                    }
                    state.copy(
                        isSending = false,
                        draft = "",
                        messages = mergedMessages,
                        attachedReferences = emptyList(),
                        outgoingMessage = null,
                        errorMessage = null,
                        requiresLogin = false,
                    )
                }
            }.onFailure { failure ->
                val failedOutgoing = outgoing.copy(status = OutgoingMessageStatus.FAILED)
                draftStore.saveOutgoing(sessionId, failedOutgoing)
                _state.update {
                    it.copy(
                        isSending = false,
                        outgoingMessage = failedOutgoing,
                        errorMessage = failure.toChatMessage("Could not send the message."),
                        requiresLogin = failure.requiresLogin(),
                    )
                }
            }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        val sessionId = snapshot.sessionId ?: return
        val cursor = snapshot.nextCursor ?: return
        if (!snapshot.hasMore || snapshot.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.messages(sessionId, cursor) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            messages = (it.messages + page.items).distinctBy { message -> message.id },
                            nextCursor = page.nextCursor,
                            hasMore = page.hasNext,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            errorMessage = failure.toChatMessage("Could not load more messages."),
                            requiresLogin = failure.requiresLogin(),
                        )
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query.take(200)) }
        scheduleSearch()
    }

    fun setSourceFilter(filter: ChatSourceFilter) {
        _state.update { it.copy(sourceFilter = filter) }
        scheduleSearch(immediate = true)
    }

    private fun scheduleSearch(immediate: Boolean = false) {
        searchJob?.cancel()
        val query = _state.value.searchQuery.trim()
        if (query.length < 2) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        val filter = _state.value.sourceFilter
        searchJob = viewModelScope.launch {
            if (!immediate) delay(250)
            _state.update { it.copy(isSearching = true) }
            runCatching { repository.search(query, filter.apiValue).items }
                .onSuccess { results ->
                    _state.update { it.copy(isSearching = false, searchResults = results) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            isSearching = false,
                            errorMessage = failure.toChatMessage("Could not search FoodMind sources."),
                            requiresLogin = failure.requiresLogin(),
                        )
                    }
                }
        }
    }

    fun attach(item: ExploreItemResponse) {
        val sessionId = _state.value.sessionId ?: return
        val sourceId = item.sourceId ?: return
        val sourceType = item.sourceType?.let(::normaliseChatSourceType) ?: return
        if (_state.value.attachedReferences.any { it.sourceType == sourceType && it.sourceId == sourceId }) return
        viewModelScope.launch {
            runCatching { repository.shareReference(sessionId, sourceType, sourceId) }
                .onSuccess { reference ->
                    _state.update {
                        it.copy(
                            attachedReferences = (it.attachedReferences + reference)
                                .distinctBy { item -> item.id ?: "${item.sourceType}:${item.sourceId}" },
                            searchResults = emptyList(),
                            searchQuery = "",
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            errorMessage = failure.toChatMessage("This source cannot be attached right now."),
                            requiresLogin = failure.requiresLogin(),
                        )
                    }
                }
        }
    }

    fun reattach(source: ChatMessageSourceResponse) {
        attach(
            ExploreItemResponse(
                sourceType = source.sourceType,
                sourceId = source.sourceId,
                title = source.title,
                snippet = source.snippet,
            ),
        )
    }

    fun removeReference(id: String?) {
        _state.update { state ->
            state.copy(attachedReferences = state.attachedReferences.filterNot { it.id == id })
        }
    }

    fun clearCurrentReferences() {
        _state.update { it.copy(attachedReferences = emptyList()) }
    }

    private fun hasCompletedOutgoing(
        messages: List<ChatMessageResponse>,
        outgoing: OutgoingChatMessage,
    ): Boolean {
        if (messages.size < 2) return false
        val userMessage = messages[messages.lastIndex - 1]
        val assistantMessage = messages.last()
        return userMessage.role == "USER" &&
            userMessage.content == outgoing.content &&
            assistantMessage.role == "ASSISTANT"
    }

    private data class OpenedChat(
        val id: String,
        val title: String,
        val messages: List<ChatMessageResponse>,
        val nextCursor: String?,
        val hasMore: Boolean,
        val reference: ChatReferenceResponse?,
        val draft: String,
        val outgoing: OutgoingChatMessage?,
    )

    class Factory(
        private val repository: ChatRepository,
        private val draftStore: ChatDraftStore = NoOpChatDraftStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChatViewModel::class.java))
            return ChatViewModel(repository, draftStore) as T
        }
    }
}
