package com.foodmind.foodmind_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindNetwork
import com.foodmind.foodmind_android.core.network.FoodMindSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val sessionId: String? = null,
    val messages: List<ChatMessageResponse> = emptyList(),
    val errorMessage: String? = null,
)

class ChatViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var apiClient: FoodMindApiClient? = null

    fun setApiClient(apiClient: FoodMindApiClient) { this.apiClient = apiClient }

    fun start() {
        val api = apiClient ?: run {
            _state.update { it.copy(isLoading = false, errorMessage = "聊天服务未配置") }
            return
        }
        viewModelScope.launch {
            runCatching { api.createChatSession("FoodMind 助手") }
                .onSuccess { session ->
                    val id = session.id
                    if (id == null) _state.value = ChatUiState(isLoading = false, errorMessage = "聊天会话无效")
                    else {
                        _state.update { it.copy(isLoading = false, sessionId = id) }
                        loadMessages(api, id)
                    }
                }
                .onFailure { _state.value = ChatUiState(isLoading = false, errorMessage = "聊天会话创建失败，请稍后重试") }
        }
    }

    fun send(content: String) {
        val api = apiClient ?: return
        val sessionId = _state.value.sessionId ?: return
        if (content.isBlank() || _state.value.isSending) return
        _state.update { it.copy(isSending = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { api.postChatMessage(sessionId, content.trim()) }
                .onSuccess { message -> _state.update { it.copy(isSending = false, messages = it.messages + message) } }
                .onFailure { _state.update { it.copy(isSending = false, errorMessage = "消息发送失败，请稍后重试") } }
        }
    }

    private suspend fun loadMessages(api: FoodMindApiClient, sessionId: String) {
        runCatching { api.chatMessages(sessionId) }
            .onSuccess { page -> _state.update { it.copy(messages = page.items) } }
            .onFailure { _state.update { it.copy(errorMessage = "历史消息加载失败") } }
    }
}

class ChatActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        val api = FoodMindApiClient(
            FoodMindNetwork.createApi(BuildConfig.FOODMIND_API_BASE_URL, FoodMindSession.tokenStore),
            FoodMindSession.tokenStore,
        )
        viewModel.setApiClient(api)
        viewModel.start()
        setContent {
            FoodMindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ChatScreen(state = state, onBack = ::finish, onRetry = viewModel::start, onSend = viewModel::send)
            }
        }
    }
}

@Composable
private fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSend: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Text("FoodMind 助手", modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Bold)
        }
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            state.errorMessage != null && state.sessionId == null -> Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.errorMessage, color = Color(0xFFB42318))
                OutlinedButton(onClick = onRetry) { Text("重试") }
            }
            else -> {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.messages.isEmpty()) item { Text("可以问我关于记录、群组和饮食偏好的问题。", color = FoodMindMuted) }
                    items(state.messages, key = { it.id ?: "${it.createdAt}-${it.role}" }) { message ->
                        Card(colors = CardDefaults.cardColors(containerColor = if (message.role == "USER") FoodMindGreenDark else Color.White), border = BorderStroke(1.dp, FoodMindLine)) {
                            Text(message.content.orEmpty(), modifier = Modifier.padding(14.dp), color = if (message.role == "USER") Color.White else FoodMindInk)
                        }
                    }
                }
                state.errorMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp), color = Color(0xFFB42318)) }
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("输入消息") }, modifier = Modifier.weight(1f), maxLines = 4)
                    Button(onClick = { onSend(draft); draft = "" }, enabled = draft.isNotBlank() && !state.isSending, modifier = Modifier.padding(start = 8.dp)) { Text("发送") }
                }
            }
        }
    }
}
