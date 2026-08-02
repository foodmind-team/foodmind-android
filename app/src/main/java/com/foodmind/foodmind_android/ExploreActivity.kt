package com.foodmind.foodmind_android

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
import com.foodmind.foodmind_android.domain.repository.ExploreItem
import com.foodmind.foodmind_android.domain.repository.ExplorePage
import com.foodmind.foodmind_android.domain.repository.ExploreRepository
import com.foodmind.foodmind_android.domain.repository.ExploreRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(
    val isLoading: Boolean = true,
    val items: List<ExploreItem> = emptyList(),
    val nextCursor: String? = null,
    val hasNext: Boolean = false,
    val errorMessage: String? = null,
)

class ExploreViewModel : ViewModel() {
    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()
    private var repository: ExploreRepository? = null

    fun setRepository(repository: ExploreRepository) { this.repository = repository }

    fun load() = loadPage(after = null, replace = true)

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoading) return
        loadPage(after = cursor, replace = false)
    }

    private fun loadPage(after: String?, replace: Boolean) {
        val active = repository ?: run {
            _state.update { it.copy(isLoading = false, errorMessage = "发现服务未配置") }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            active.page(after)
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            items = if (replace) page.items else it.items + page.items,
                            nextCursor = page.nextCursor,
                            hasNext = page.hasNext,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(isLoading = false, errorMessage = "发现内容加载失败，请稍后重试") } }
        }
    }
}

class ExploreActivity : ComponentActivity() {
    private val viewModel: ExploreViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        val api = FoodMindApiClient(
            FoodMindNetwork.createApi(BuildConfig.FOODMIND_API_BASE_URL, FoodMindSession.tokenStore),
            FoodMindSession.tokenStore,
        )
        viewModel.setRepository(ExploreRepositoryImpl(api))
        viewModel.load()
        setContent {
            FoodMindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ExploreScreen(state = state, onBack = ::finish, onRetry = viewModel::load, onLoadMore = viewModel::loadMore)
            }
        }
    }
}

@Composable
private fun ExploreScreen(
    state: ExploreUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Text("发现", modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Bold)
        }
        when {
            state.isLoading && state.items.isEmpty() -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            state.errorMessage != null && state.items.isEmpty() -> Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.errorMessage, color = Color(0xFFB42318))
                OutlinedButton(onClick = onRetry) { Text("重试") }
            }
            state.items.isEmpty() -> Text("暂时没有发现内容。", modifier = Modifier.padding(24.dp), color = FoodMindMuted)
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(item.title, fontWeight = FontWeight.Bold, color = FoodMindInk)
                            if (item.subtitle.isNotBlank()) Text(item.subtitle, modifier = Modifier.padding(top = 4.dp), color = FoodMindMuted)
                            if (item.snippet.isNotBlank()) Text(item.snippet, modifier = Modifier.padding(top = 7.dp), color = FoodMindInk)
                            Text(item.sourceType, modifier = Modifier.padding(top = 7.dp), color = FoodMindGreen, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (state.hasNext) item {
                    Button(onClick = onLoadMore, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
                }
            }
        }
    }
}
