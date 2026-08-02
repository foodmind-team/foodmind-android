package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foodmind.foodmind_android.core.network.ChatMessageResponse
import com.foodmind.foodmind_android.core.network.ChatReferenceResponse
import com.foodmind.foodmind_android.core.network.ExploreItemResponse
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val sessionId: String? = null,
    val title: String = "FoodMind 助手",
    val messages: List<ChatMessageResponse> = emptyList(),
    val attachedReferences: List<ChatReferenceResponse> = emptyList(),
    val searchResults: List<ExploreItemResponse> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
)

class ChatViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var apiClient: FoodMindApiClient? = null
    private var started = false

    fun setApiClient(apiClient: FoodMindApiClient) { this.apiClient = apiClient }

    fun open(sessionId: String?) {
        if (started) return
        started = true
        val api = apiClient ?: return
        viewModelScope.launch {
            runCatching {
                val session = if (sessionId == null) api.createChatSession("FoodMind 助手") else api.chatSession(sessionId)
                val id = session.id ?: error("missing session id")
                val messages = api.chatMessages(id).items
                Triple(id, session.title ?: "FoodMind 助手", messages)
            }.onSuccess { (id, title, messages) -> _state.value = ChatUiState(false, sessionId = id, title = title, messages = messages) }
                .onFailure { _state.value = ChatUiState(false, errorMessage = "聊天会话加载失败，请稍后重试。") }
        }
    }

    fun retry(sessionId: String?) { started = false; open(sessionId) }

    fun send(content: String) {
        val api = apiClient ?: return
        val id = _state.value.sessionId ?: return
        if (content.isBlank() || _state.value.isSending) return
        _state.update { it.copy(isSending = true, errorMessage = null) }
        val referenceIds = _state.value.attachedReferences.mapNotNull { it.id }
        viewModelScope.launch {
            runCatching { api.postChatMessage(id, content.trim(), referenceIds) }
                .onSuccess { message ->
                    val optimisticUser = ChatMessageResponse(id = "local-${System.nanoTime()}", sessionId = id, role = "USER", content = content.trim())
                    _state.update { it.copy(isSending = false, messages = it.messages + optimisticUser + message, attachedReferences = emptyList()) }
                }.onFailure { _state.update { it.copy(isSending = false, errorMessage = "消息发送失败，请稍后重试。") } }
        }
    }

    fun search(query: String) {
        val api = apiClient ?: return
        if (query.trim().length < 2) { _state.update { it.copy(searchResults = emptyList()) }; return }
        _state.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            runCatching { api.search(query.trim()).items }
                .onSuccess { results -> _state.update { it.copy(isSearching = false, searchResults = results) } }
                .onFailure { _state.update { it.copy(isSearching = false, errorMessage = "搜索来源失败。") } }
        }
    }

    fun attach(item: ExploreItemResponse) {
        val api = apiClient ?: return
        val id = _state.value.sessionId ?: return
        val type = item.sourceType ?: return
        val sourceId = item.sourceId ?: return
        viewModelScope.launch {
            runCatching { api.shareChatReference(id, type, sourceId) }
                .onSuccess { reference -> _state.update { it.copy(attachedReferences = (it.attachedReferences + reference).distinctBy(ChatReferenceResponse::id), searchResults = emptyList()) } }
                .onFailure { _state.update { it.copy(errorMessage = "这个来源暂时无法附加。") } }
        }
    }

    fun removeReference(id: String?) { _state.update { state -> state.copy(attachedReferences = state.attachedReferences.filterNot { it.id == id }) } }
}

class ChatActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        viewModel.setApiClient(foodMindApiClient())
        viewModel.open(sessionId)
        setContent {
            FoodMindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ChatScreen(state, ::finish, { viewModel.retry(sessionId) }, viewModel::send, viewModel::search, viewModel::attach, viewModel::removeReference)
            }
        }
    }

    companion object {
        private const val EXTRA_SESSION_ID = "session_id"
        fun intent(context: Context, sessionId: String?): Intent = Intent(context, ChatActivity::class.java).apply { sessionId?.let { putExtra(EXTRA_SESSION_ID, it) } }
    }
}

@Composable
private fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSend: (String) -> Unit,
    onSearch: (String) -> Unit,
    onAttach: (ExploreItemResponse) -> Unit,
    onRemoveReference: (String?) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var sourceQuery by remember { mutableStateOf("") }
    var showSources by remember { mutableStateOf(false) }
    FoodMindDetailScaffold(state.title, onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.padding(24.dp))
                state.sessionId == null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.errorMessage ?: "无法打开聊天", color = FoodMindCoral)
                    OutlinedButton(onClick = onRetry) { Text("重试") }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.messages.isEmpty()) item {
                            Text("我是 FoodMind 助手。可以和你聊饮食决定，也能基于你附加的已授权内容回答。", color = FoodMindMuted, modifier = Modifier.padding(12.dp))
                        }
                        items(state.messages, key = { it.id ?: "${it.createdAt}-${it.role}" }) { message ->
                            val user = message.role == "USER"
                            Column(horizontalAlignment = if (user) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (user) FoodMindGreenDark else Color.White),
                                    border = if (user) null else BorderStroke(1.dp, FoodMindLine),
                                ) { Text(message.content.orEmpty(), Modifier.padding(15.dp), color = if (user) Color.White else FoodMindInk) }
                                if (message.sources.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 5.dp)) {
                                    message.sources.take(3).forEach { source -> AssistChip(onClick = {}, label = { Text(source.title ?: source.sourceType ?: "来源") }) }
                                }
                            }
                        }
                    }
                    state.errorMessage?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = FoodMindCoral) }
                    if (showSources) Column(Modifier.fillMaxWidth().background(Color.White).padding(12.dp)) {
                        OutlinedTextField(
                            sourceQuery, { sourceQuery = it; onSearch(it) }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("搜索可附加的 FoodMind 来源") }, singleLine = true,
                            trailingIcon = { IconButton(onClick = { showSources = false }) { Icon(Icons.Outlined.Close, "关闭") } },
                        )
                        state.searchResults.take(4).forEach { result -> TextButton(onClick = { onAttach(result); showSources = false }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(result.title ?: "未命名"); Text(result.sourceType.orEmpty(), color = FoodMindMuted) }
                            Icon(Icons.Outlined.Add, null)
                        } }
                    }
                    if (state.attachedReferences.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.attachedReferences.forEach { reference -> AssistChip(onClick = { onRemoveReference(reference.id) }, label = { Text(reference.title ?: reference.sourceType ?: "来源") }, trailingIcon = { Icon(Icons.Outlined.Close, "移除") }) }
                    }
                    Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), verticalAlignment = Alignment.Bottom) {
                        IconButton(onClick = { showSources = !showSources }) { Icon(Icons.Outlined.AttachFile, "附加来源") }
                        OutlinedTextField(draft, { draft = it }, placeholder = { Text("发消息…") }, modifier = Modifier.weight(1f), maxLines = 4)
                        IconButton(onClick = { onSend(draft); draft = "" }, enabled = draft.isNotBlank() && !state.isSending) { Icon(Icons.AutoMirrored.Outlined.Send, "发送") }
                    }
                }
            }
        }
    }
}
