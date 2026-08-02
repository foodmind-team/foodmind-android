package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindNetwork
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.domain.repository.GroupFeedItem
import com.foodmind.foodmind_android.domain.repository.GroupFeedRepository
import com.foodmind.foodmind_android.domain.repository.GroupFeedRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupFeedUiState(
    val isLoading: Boolean = true,
    val items: List<GroupFeedItem> = emptyList(),
    val nextCursor: String? = null,
    val errorMessage: String? = null,
)

class GroupFeedViewModel : ViewModel() {
    private val _state = MutableStateFlow(GroupFeedUiState())
    val state: StateFlow<GroupFeedUiState> = _state.asStateFlow()
    private var repository: GroupFeedRepository? = null
    private var groupId: String? = null

    fun setRepository(repository: GroupFeedRepository) { this.repository = repository }

    fun load(groupId: String) {
        this.groupId = groupId
        _state.value = GroupFeedUiState()
        request(after = null, replace = true)
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (!_state.value.isLoading) request(cursor, replace = false)
    }

    private fun request(after: String?, replace: Boolean) {
        val active = repository ?: run {
            _state.update { it.copy(isLoading = false, errorMessage = "群组动态服务未配置") }
            return
        }
        val activeGroupId = groupId ?: return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            active.page(activeGroupId, after)
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            items = if (replace) page.items else it.items + page.items,
                            nextCursor = page.nextCursor,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(isLoading = false, errorMessage = "群组动态加载失败，请稍后重试") } }
        }
    }
}

class GroupFeedActivity : ComponentActivity() {
    private val viewModel: GroupFeedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: run { finish(); return }
        FoodMindSession.initialize(this)
        val api = FoodMindApiClient(
            FoodMindNetwork.createApi(BuildConfig.FOODMIND_API_BASE_URL, FoodMindSession.tokenStore),
            FoodMindSession.tokenStore,
        )
        viewModel.setRepository(GroupFeedRepositoryImpl(api))
        viewModel.load(groupId)
        setContent {
            FoodMindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                GroupFeedScreen(
                    state = state,
                    onBack = ::finish,
                    onRetry = { viewModel.load(groupId) },
                    onLoadMore = viewModel::loadMore,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_GROUP_ID = "group_id"
        fun intent(context: Context, groupId: String): Intent =
            Intent(context, GroupFeedActivity::class.java).putExtra(EXTRA_GROUP_ID, groupId)
    }
}

@Composable
private fun GroupFeedScreen(
    state: GroupFeedUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Text("群组动态", modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Bold)
        }
        when {
            state.isLoading && state.items.isEmpty() -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            state.errorMessage != null && state.items.isEmpty() -> Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.errorMessage, color = Color(0xFFB42318))
                OutlinedButton(onClick = onRetry) { Text("重试") }
            }
            state.items.isEmpty() -> Text("这个群组还没有动态。", modifier = Modifier.padding(24.dp), color = FoodMindMuted)
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(item.title, fontWeight = FontWeight.Bold, color = FoodMindInk)
                            Text("${item.actor} · ${item.sourceType}", modifier = Modifier.padding(top = 4.dp), color = FoodMindGreen)
                            if (item.message.isNotBlank()) Text(item.message, modifier = Modifier.padding(top = 7.dp), color = FoodMindInk)
                            Text(item.occurredAt, modifier = Modifier.padding(top = 7.dp), color = FoodMindMuted)
                        }
                    }
                }
                if (state.nextCursor != null) item {
                    Button(onClick = onLoadMore, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
                }
            }
        }
    }
}
