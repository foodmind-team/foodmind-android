package com.foodmind.foodmind_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindNetwork
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.domain.repository.CookingPlanRepositoryImpl

/** Compose migration slice for the cooking plan; XML remains for the other prototype screens. */
class CookingPlanActivity : ComponentActivity() {
    private val viewModel: CookingPlanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        val recipeNames = intent.getStringExtra(EXTRA_RECIPE_NAMES) ?: getString(R.string.recipe_default_selection)
        val recipeIds = intent.getStringArrayExtra(EXTRA_RECIPE_IDS)?.toList() ?: emptyList()
        val useRealApi = intent.getBooleanExtra(EXTRA_USE_REAL_API, false)
        if (useRealApi) {
            val apiClient = FoodMindApiClient(
                FoodMindNetwork.createApi(BuildConfig.FOODMIND_API_BASE_URL, FoodMindSession.tokenStore),
                FoodMindSession.tokenStore,
            )
            viewModel.setRepository(CookingPlanRepositoryImpl(apiClient))
            viewModel.generate(recipeNames, recipeIds)
        }
        val status = intent.getStringExtra(EXTRA_STATUS)
            ?.let { runCatching { CookingPlanStatus.valueOf(it) }.getOrNull() }
            ?: CookingPlanStatus.READY
        if (!useRealApi) viewModel.load(recipeNames, status)

        setContent {
            FoodMindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                CookingPlanScreen(
                    state = state,
                    onBack = ::finish,
                    onToggleTask = viewModel::toggleTask,
                )
            }
        }
    }

    companion object {
        const val EXTRA_RECIPE_NAMES = "recipe_names"
        const val EXTRA_RECIPE_IDS = "recipe_ids"
        const val EXTRA_STATUS = "plan_status"
        const val EXTRA_USE_REAL_API = "use_real_api"
    }
}

@Composable
private fun CookingPlanScreen(
    state: CookingPlanUiState,
    onBack: () -> Unit,
    onToggleTask: (String) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = FoodMindPaper) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBack) { Text("返回") }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "烹饪计划",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FoodMindInk,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        text = if (state.status == CookingPlanStatus.READY) "已验证 · READY" else state.status.name,
                        color = if (state.status == CookingPlanStatus.READY) FoodMindGreen else FoodMindCoral,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = state.recipeNames,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = FoodMindInk,
                    )
                    Text(
                        text = state.statusMessage,
                        modifier = Modifier.padding(top = 8.dp),
                        color = if (state.status == CookingPlanStatus.READY) FoodMindMuted else FoodMindCoral,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (state.status == CookingPlanStatus.READY) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, FoodMindLine),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "已完成 ${state.completedCount}/${state.tasks.size}",
                                    fontWeight = FontWeight.Bold,
                                    color = FoodMindInk,
                                )
                                LinearProgressIndicator(
                                    progress = { state.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                        .height(8.dp)
                                        .semantics { contentDescription = "执行进度 ${state.progressPercent}%" },
                                    color = FoodMindGreen,
                                    trackColor = FoodMindLine,
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            text = "执行步骤",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FoodMindInk,
                        )
                    }
                    items(state.tasks, key = { it.id }) { task ->
                        val checked = task.id in state.completedTaskIds
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, FoodMindLine),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onToggleTask(task.id) },
                                )
                                Column(modifier = Modifier.padding(start = 4.dp)) {
                                    Text(task.label, color = FoodMindInk, fontWeight = FontWeight.Medium)
                                    Text(task.window, color = FoodMindMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, FoodMindLine),
                        ) {
                            Text(
                                text = "当前不能执行",
                                modifier = Modifier.padding(18.dp),
                                color = FoodMindCoral,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
