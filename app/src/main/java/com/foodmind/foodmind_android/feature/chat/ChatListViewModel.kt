package com.foodmind.foodmind_android.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodmind.foodmind_android.core.network.ChatSessionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val repository: ChatRepository,
    private val draftStore: ChatDraftStore = NoOpChatDraftStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    fun refresh() {
        if (_state.value.isLoading && _state.value.sessions.isNotEmpty()) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.sessions().items }
                .onSuccess { sessions ->
                    _state.update { it.copy(isLoading = false, sessions = sessions, errorMessage = null) }
                }
                .onFailure {
                    _state.update { state ->
                        state.copy(isLoading = false, errorMessage = "Could not load conversations.")
                    }
                }
        }
    }

    fun requestArchive(session: ChatSessionResponse) {
        _state.update { it.copy(archiveCandidate = session) }
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query.take(120)) }
    }

    fun dismissArchive() {
        if (!_state.value.isArchiving) _state.update { it.copy(archiveCandidate = null) }
    }

    fun confirmArchive() {
        val session = _state.value.archiveCandidate ?: return
        val sessionId = session.id ?: return
        if (_state.value.isArchiving) return
        _state.update { it.copy(isArchiving = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.archiveSession(sessionId) }
                .onSuccess {
                    draftStore.clear(sessionId)
                    _state.update {
                        it.copy(
                            isArchiving = false,
                            archiveCandidate = null,
                            sessions = it.sessions.filterNot { item -> item.id == sessionId },
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            isArchiving = false,
                            archiveCandidate = null,
                            errorMessage = "Could not archive this conversation.",
                        )
                    }
                }
        }
    }

    class Factory(
        private val repository: ChatRepository,
        private val draftStore: ChatDraftStore = NoOpChatDraftStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChatListViewModel::class.java))
            return ChatListViewModel(repository, draftStore) as T
        }
    }
}
