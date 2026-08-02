package com.foodmind.foodmind_android

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindNetwork
import com.foodmind.foodmind_android.core.network.FoodMindSession

class RecipeLibraryActivity : ComponentActivity() {
    private val viewModel: RecipeSelectionViewModel by viewModels()
    private lateinit var apiClient: FoodMindApiClient

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        viewModel.loadRemote(apiClient)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        apiClient = FoodMindApiClient(
            FoodMindNetwork.createApi(BuildConfig.FOODMIND_API_BASE_URL, FoodMindSession.tokenStore),
            FoodMindSession.tokenStore,
        )
        setContent {
            FoodMindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                RecipeLibraryScreen(
                    state = state,
                    onBack = ::finish,
                    onToggleRecipe = viewModel::toggleRecipe,
                    onStatusChange = viewModel::setStatus,
                    onGenerate = {
                        startActivity(
                            Intent(this, CookingPlanActivity::class.java)
                                .putExtra(CookingPlanActivity.EXTRA_RECIPE_NAMES, state.selectedNames)
                                .putExtra(CookingPlanActivity.EXTRA_RECIPE_IDS, state.selectedIds.toTypedArray())
                                .putExtra(CookingPlanActivity.EXTRA_STATUS, state.status.name),
                        )
                    },
                    onGenerateReal = {
                        startActivity(
                            Intent(this, CookingPlanActivity::class.java)
                                .putExtra(CookingPlanActivity.EXTRA_RECIPE_NAMES, state.selectedNames)
                                .putExtra(CookingPlanActivity.EXTRA_RECIPE_IDS, state.selectedIds.toTypedArray())
                                .putExtra(CookingPlanActivity.EXTRA_USE_REAL_API, true),
                        )
                    },
                    onAdd = { startActivity(Intent(this, RecipeEditorActivity::class.java)) },
                    onEdit = { id -> startActivity(RecipeEditorActivity.intent(this, id)) },
                    onDelete = { id -> viewModel.deleteRecipeRemote(apiClient, id) },
                )
            }
        }
    }
}

@Composable
private fun RecipeLibraryScreen(
    state: RecipeSelectionUiState,
    onBack: () -> Unit,
    onToggleRecipe: (String) -> Unit,
    onStatusChange: (CookingPlanStatus) -> Unit,
    onGenerate: () -> Unit,
    onGenerateReal: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Spacer(modifier = Modifier.width(12.dp))
            Text("我的菜谱", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FoodMindInk)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "从已保存的菜谱中选择 1–N 道，FoodMind 会生成可执行的时间线。",
                    color = FoodMindMuted,
                )
                Text(
                    "已保存菜谱",
                    modifier = Modifier.padding(top = 22.dp),
                    fontWeight = FontWeight.Bold,
                    color = FoodMindInk,
                )
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text("新增本地草稿菜谱")
                }
            }
            items(state.recipes, key = { it.id }) { recipe ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                    border = BorderStroke(1.dp, FoodMindLine),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = recipe.id in state.selectedIds,
                            onCheckedChange = { onToggleRecipe(recipe.id) },
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(recipe.name, fontWeight = FontWeight.Medium, color = FoodMindInk)
                            Text(recipe.meta, color = FoodMindMuted, fontSize = 12.sp)
                            Row {
                                TextButton(onClick = { onEdit(recipe.id) }) { Text("编辑") }
                                TextButton(onClick = { onDelete(recipe.id) }) { Text("删除") }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "演示状态（C-03 fixture）",
                    modifier = Modifier.padding(top = 12.dp),
                    fontWeight = FontWeight.Bold,
                    color = FoodMindInk,
                )
                Column {
                    CookingPlanStatus.values().forEach { status ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = state.status == status,
                                onClick = { onStatusChange(status) },
                            )
                            Text(status.label(), color = FoodMindMuted, fontSize = 12.sp)
                        }
                    }
                }
                Text(
                    if (state.remoteError == null) "菜谱由 C-08 owner-scoped API 加载；无会话/服务不可用时保留本地演示草稿。"
                    else "服务端菜谱暂不可用，当前显示本地演示草稿。",
                    modifier = Modifier.padding(top = 8.dp),
                    color = FoodMindMuted,
                    fontSize = 12.sp,
                )
            }
            item {
                Button(
                    onClick = onGenerate,
                    enabled = state.canGenerate,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("生成烹饪计划") }
                OutlinedButton(
                    onClick = onGenerateReal,
                    enabled = state.canGenerate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) { Text("调用真实 API 生成") }
            }
        }
    }
}

private fun CookingPlanStatus.label(): String = when (this) {
    CookingPlanStatus.READY -> "READY"
    CookingPlanStatus.NEEDS_CONFIRMATION -> "待确认"
    CookingPlanStatus.INFEASIBLE -> "不可行"
    CookingPlanStatus.FAILED -> "失败"
}
