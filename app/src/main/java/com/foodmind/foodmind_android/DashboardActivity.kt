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
import com.foodmind.foodmind_android.domain.repository.DashboardData
import com.foodmind.foodmind_android.domain.repository.DashboardMetric
import com.foodmind.foodmind_android.domain.repository.DashboardRepository
import com.foodmind.foodmind_android.domain.repository.DashboardRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DashboardUiState(
    val isLoading: Boolean = true,
    val empty: Boolean = false,
    val metrics: List<DashboardMetric> = emptyList(),
    val errorMessage: String? = null,
)

class DashboardViewModel : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()
    private var repository: DashboardRepository? = null

    fun setRepository(repository: DashboardRepository) { this.repository = repository }

    fun load() {
        val active = repository ?: run {
            _state.update { it.copy(isLoading = false, errorMessage = "统计服务未配置") }
            return
        }
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val now = Calendar.getInstance()
        val to = now.clone() as Calendar
        to.add(Calendar.DAY_OF_YEAR, 1)
        val from = now.clone() as Calendar
        from.add(Calendar.DAY_OF_YEAR, -30)
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            active.load(formatter.format(from.time), formatter.format(to.time))
                .onSuccess { data -> _state.value = DashboardUiState(false, data.empty, data.metrics) }
                .onFailure { _state.value = DashboardUiState(isLoading = false, errorMessage = "统计加载失败，请稍后重试") }
        }
    }
}

class DashboardActivity : ComponentActivity() {
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        val api = FoodMindApiClient(
            FoodMindNetwork.createApi(BuildConfig.FOODMIND_API_BASE_URL, FoodMindSession.tokenStore),
            FoodMindSession.tokenStore,
        )
        viewModel.setRepository(DashboardRepositoryImpl(api))
        viewModel.load()
        setContent {
            FoodMindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                DashboardScreen(state = state, onBack = ::finish, onRetry = viewModel::load)
            }
        }
    }
}

@Composable
private fun DashboardScreen(state: DashboardUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Text("数据看板", modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Bold)
        }
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            state.errorMessage != null -> Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.errorMessage, color = Color(0xFFB42318))
                OutlinedButton(onClick = onRetry) { Text("重试") }
            }
            state.empty || state.metrics.isEmpty() -> Text("近 30 天还没有足够数据生成看板。", modifier = Modifier.padding(24.dp), color = FoodMindMuted)
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.metrics, key = { "${it.code}-${it.period}" }) { metric ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(metric.label, fontWeight = FontWeight.Bold, color = FoodMindInk)
                            Text("${metric.value} ${metric.unit}".trim(), modifier = Modifier.padding(top = 8.dp), color = FoodMindGreenDark)
                            if (metric.period.isNotBlank()) Text(metric.period, modifier = Modifier.padding(top = 5.dp), color = FoodMindMuted)
                        }
                    }
                }
            }
        }
    }
}
