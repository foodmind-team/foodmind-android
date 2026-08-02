package com.foodmind.foodmind_android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.core.network.WantToTryResponse
import com.foodmind.foodmind_android.domain.repository.RecipeDraft
import com.foodmind.foodmind_android.domain.repository.RecipeDraftStore
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class SavedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        RecipeDraftStore.initialize(this, FoodMindSession.tokenStore.userId())
        val client = foodMindApiClient()
        setContent {
            FoodMindTheme {
                SavedScreen(
                    client = client,
                    onNavigate = ::openFoodMindRoot,
                    onAddRecipe = { startActivity(Intent(this, RecipeEditorActivity::class.java)) },
                    onEditRecipe = { startActivity(RecipeEditorActivity.intent(this, it)) },
                    onCook = { startActivity(Intent(this, RecipeLibraryActivity::class.java)) },
                    onOpenSource = { type, id -> startActivity(CatalogueDetailActivity.intent(this, type, id)) },
                )
            }
        }
    }
}

@Composable
private fun SavedScreen(
    client: FoodMindApiClient,
    onNavigate: (FoodMindRoot) -> Unit,
    onAddRecipe: () -> Unit,
    onEditRecipe: (String) -> Unit,
    onCook: () -> Unit,
    onOpenSource: (String, String) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf<List<WantToTryResponse>>(emptyList()) }
    var recipes by remember { mutableStateOf(RecipeDraftStore.list()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { recipes = RecipeDraftStore.list() }

    LaunchedEffect(refreshKey) {
        loading = true
        runCatching { client.wantToTry().items }
            .onSuccess { saved = it; error = null }
            .onFailure { error = "暂时无法加载想尝试列表。" }
        recipes = RecipeDraftStore.list()
        loading = false
    }

    FoodMindRootScaffold(
        selected = FoodMindRoot.SAVED,
        title = "收藏",
        onNavigate = onNavigate,
        topActions = {
            IconButton(onClick = onAddRecipe) { Icon(Icons.Outlined.Add, contentDescription = "新增菜谱") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.White, contentColor = FoodMindGreen) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("想尝试 ${saved.size}", fontWeight = FontWeight.Bold) })
                Tab(selected = tab == 1, onClick = { tab = 1; recipes = RecipeDraftStore.list() }, text = { Text("本地菜谱 ${recipes.size}", fontWeight = FontWeight.Bold) })
            }
            when {
                loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                tab == 0 -> SavedIdeas(
                    items = saved,
                    error = error,
                    onRetry = { refreshKey++ },
                    onOpen = onOpenSource,
                    onDelete = { item ->
                        saved = saved.filterNot { it.id == item.id }
                        error = null
                        // Mutation is reconciled by the next refresh if the request fails.
                        scope.launch { runCatching { client.deleteWantToTry(item.id) }.onFailure { error = "移除失败，请刷新后重试。" } }
                    },
                )
                else -> RecipeDrafts(
                    items = recipes,
                    onAdd = onAddRecipe,
                    onCook = onCook,
                    onEdit = onEditRecipe,
                    onDelete = { id -> RecipeDraftStore.delete(id); recipes = RecipeDraftStore.list() },
                )
            }
        }
    }
}

@Composable
private fun SavedIdeas(
    items: List<WantToTryResponse>,
    error: String?,
    onRetry: () -> Unit,
    onOpen: (String, String) -> Unit,
    onDelete: (WantToTryResponse) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("留到合适的时候", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = FoodMindInk)
            Text("保存的餐点、地点、产品和社区内容会集中在这里。", color = FoodMindMuted, modifier = Modifier.padding(top = 6.dp))
            error?.let {
                Text(it, color = FoodMindCoral, modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
        if (items.isEmpty() && error == null) item {
            FoodMindSurfaceCard { Column { Text("还没有收藏", fontWeight = FontWeight.Bold); Text("在发现页点“想尝试”，内容就会出现在这里。", color = FoodMindMuted) } }
        }
        items(items, key = { it.id }) { item ->
            Card(
                onClick = { if (item.sourceAvailable) onOpen(item.sourceType, item.sourceId) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, FoodMindLine),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FoodMindAvatar(item.sourceType)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(item.source?.title ?: "内容已不可用", fontWeight = FontWeight.Bold, color = FoodMindInk)
                        item.source?.subtitle?.takeIf(String::isNotBlank)?.let { Text(it, color = FoodMindMuted, fontSize = 13.sp) }
                        item.note?.takeIf(String::isNotBlank)?.let { Text(it, color = FoodMindGreen, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp)) }
                    }
                    IconButton(onClick = { onDelete(item) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "移除", tint = FoodMindMuted) }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = FoodMindMuted)
                }
            }
        }
    }
}

@Composable
private fun RecipeDrafts(
    items: List<RecipeDraft>,
    onAdd: () -> Unit,
    onCook: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("真正能做的菜谱", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = FoodMindInk)
                    Text("草稿只保存在此设备，并按账号隔离。", color = FoodMindMuted)
                }
                Button(onClick = onCook) { Icon(Icons.Outlined.RestaurantMenu, null); Spacer(Modifier.width(6.dp)); Text("去烹饪") }
            }
        }
        if (items.isEmpty()) item { FoodMindSurfaceCard { Text("还没有本地菜谱。") } }
        items(items, key = RecipeDraft::id) { recipe ->
            Card(
                onClick = { onEdit(recipe.id) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, FoodMindLine),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(recipe.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FoodMindInk)
                            Text("${recipe.servings} 人份 · ${recipe.minutes} 分钟 · ${recipe.category}", color = FoodMindMuted, modifier = Modifier.padding(top = 4.dp))
                        }
                        IconButton(onClick = { onDelete(recipe.id) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除菜谱") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recipe.tags.take(3).forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                    Text("${recipe.ingredients.size} 种食材 · ${recipe.steps.size} 个步骤", color = FoodMindGreen, fontSize = 12.sp)
                }
            }
        }
        item { TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("新增本地菜谱") } }
    }
}
