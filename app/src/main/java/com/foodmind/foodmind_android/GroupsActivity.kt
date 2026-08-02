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
import com.foodmind.foodmind_android.domain.repository.GroupItem
import com.foodmind.foodmind_android.domain.repository.GroupRepository
import com.foodmind.foodmind_android.domain.repository.GroupRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(
    val isLoading: Boolean = true,
    val groups: List<GroupItem> = emptyList(),
    val errorMessage: String? = null,
)

class GroupsViewModel : ViewModel() {
    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state.asStateFlow()
    private var repository: GroupRepository? = null

    fun setRepository(repository: GroupRepository) { this.repository = repository }

    fun load() {
        val active = repository ?: run {
            _state.update { it.copy(isLoading = false, errorMessage = "群组服务未配置") }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            active.list()
                .onSuccess { groups -> _state.value = GroupsUiState(isLoading = false, groups = groups) }
                .onFailure { _state.value = GroupsUiState(isLoading = false, errorMessage = "群组加载失败，请稍后重试") }
        }
    }
}

class GroupsActivity : ComponentActivity() {
    private val viewModel: GroupsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        val api = FoodMindApiClient(
            FoodMindNetwork.createApi(BuildConfig.FOODMIND_API_BASE_URL, FoodMindSession.tokenStore),
            FoodMindSession.tokenStore,
        )
        viewModel.setRepository(GroupRepositoryImpl(api))
        viewModel.load()
        setContent {
            FoodMindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                GroupsScreen(
                    state = state,
                    onBack = ::finish,
                    onRetry = viewModel::load,
                    onGroupClick = { groupId -> startActivity(GroupFeedActivity.intent(this, groupId)) },
                )
            }
        }
    }
}

@Composable
private fun GroupsScreen(
    state: GroupsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onGroupClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Text("群组", modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Bold)
        }
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            state.errorMessage != null -> Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.errorMessage, color = Color(0xFFB42318))
                OutlinedButton(onClick = onRetry) { Text("重试") }
            }
            state.groups.isEmpty() -> Text("还没有加入群组。", modifier = Modifier.padding(24.dp), color = FoodMindMuted)
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.groups, key = { it.id }) { group ->
                    Card(
                        onClick = { onGroupClick(group.id) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, FoodMindLine),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(group.name, fontWeight = FontWeight.Bold, color = FoodMindInk)
                            if (group.description.isNotBlank()) Text(group.description, modifier = Modifier.padding(top = 5.dp), color = FoodMindMuted)
                            Text(group.status, modifier = Modifier.padding(top = 7.dp), color = FoodMindGreen)
                        }
                    }
                }
            }
        }
    }
}
