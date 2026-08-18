package com.foodmind.foodmind_android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.foodmind.foodmind_android.core.network.ExploreItemResponse
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import kotlinx.coroutines.launch

class ExploreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = foodMindApiClient()
        setContent {
            FoodMindTheme {
                ExploreScreen(
                    client = client,
                    onNavigate = ::openFoodMindRoot,
                    onOpen = { type, id -> startActivity(CatalogueDetailActivity.intent(this, type, id)) },
                    onRecord = { startActivity(RecordEditorActivity.intent(this, "FOOD", null)) },
                    onChat = { startActivity(Intent(this, ChatListActivity::class.java)) },
                )
            }
        }
    }
}

@Composable
private fun ExploreScreen(
    client: FoodMindApiClient,
    onNavigate: (FoodMindRoot) -> Unit,
    onOpen: (String, String) -> Unit,
    onRecord: () -> Unit,
    onChat: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var activeQuery by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<ExploreItemResponse>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var hasNext by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedIds by remember { mutableStateOf(setOf<String>()) }
    var refresh by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    LaunchedEffect(activeQuery, type, refresh) {
        loading = true
        runCatching {
            if (activeQuery.isBlank()) client.explore(topics = null).let { Triple(it.items, it.nextCursor, it.hasNext) }
            else client.search(activeQuery, type).let { Triple(it.items, it.nextCursor, it.hasNext) }
        }.onSuccess { (results, cursor, more) -> items = results; nextCursor = cursor; hasNext = more; error = null }
            .onFailure { error = "Could not load Discover content. Please try again." }
        loading = false
    }

    FoodMindRootScaffold(
        selected = FoodMindRoot.EXPLORE,
        title = "Discover",
        onNavigate = onNavigate,
        topActions = { IconButton(onClick = onChat) { Icon(Icons.Outlined.ChatBubbleOutline, "FoodMind Assistant") } },
        onRecord = onRecord,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().background(FoodMindSurface).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    query, { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Search meals, places, and products…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { activeQuery = query.trim() }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                )
                IconButton(onClick = { activeQuery = query.trim() }) { Icon(Icons.Outlined.Search, "Search") }
            }
            Row(Modifier.fillMaxWidth().background(FoodMindSurface).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "Recommendations", "FOOD_RECORD" to "Meal", "PLACE" to "Place", "FOOD_PRODUCT" to "Product").forEach { (code, label) ->
                    FilterChip(selected = type == code, onClick = { type = code; if (activeQuery.isBlank() && code != null) activeQuery = query.ifBlank { label } }, label = { Text(label) })
                }
            }
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null -> Column(Modifier.padding(24.dp)) { Text(error!!, color = FoodMindCoral); TextButton(onClick = { refresh++ }) { Text("Try again") } }
                items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No matching content found.", color = FoodMindMuted) }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { "${it.sourceType}-${it.sourceId}" }) { item ->
                        ExploreCard(
                            item = item,
                            saved = item.sourceId in savedIds,
                            onOpen = { item.sourceType?.let { sourceType -> item.sourceId?.let { onOpen(sourceType, it) } } },
                            onSave = {
                                val sourceId = item.sourceId ?: return@ExploreCard
                                val saveType = when (item.sourceType) { "CURATED_PLACE" -> "PLACE"; "CURATED_PRODUCT" -> "FOOD_PRODUCT"; "GROUP_RECORD" -> "FOOD_RECORD"; else -> item.sourceType ?: return@ExploreCard }
                                savedIds = savedIds + sourceId
                                scope.launch { runCatching { client.saveWantToTry(saveType, sourceId) }.onFailure { savedIds = savedIds - sourceId; error = "Could not save." } }
                            },
                        )
                    }
                    if (hasNext) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        TextButton(onClick = { scope.launch {
                            loading = true
                            runCatching {
                                if (activeQuery.isBlank()) client.explore(after = nextCursor).let { Triple(it.items, it.nextCursor, it.hasNext) }
                                else client.search(activeQuery, type, nextCursor).let { Triple(it.items, it.nextCursor, it.hasNext) }
                            }.onSuccess { page -> items = items + page.first; nextCursor = page.second; hasNext = page.third }
                            loading = false
                        } }, modifier = Modifier.fillMaxWidth()) { Text("Load more") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreCard(item: ExploreItemResponse, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = FoodMindSurface),
        border = BorderStroke(1.dp, FoodMindLine),
    ) {
        Column {
            AuthorisedImage(
                model = item.imageReference,
                contentDescription = item.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(if ((item.title?.length ?: 0) % 2 == 0) 0.9f else 1.15f).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                emptyLabel = item.sourceType.orEmpty().replace('_', ' '),
            )
            Column(Modifier.padding(11.dp)) {
                Text(item.title ?: "Untitled content", maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, color = FoodMindInk)
                item.snippet?.takeIf(String::isNotBlank)?.let { Text(it, color = FoodMindMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.subtitle ?: item.sourceType.orEmpty(), Modifier.weight(1f), color = FoodMindMuted, fontSize = 11.sp, maxLines = 1)
                    IconButton(onClick = onSave) { Icon(Icons.Outlined.BookmarkAdd, if (saved) "Saved" else "Want to Try", tint = if (saved) FoodMindCoral else FoodMindMuted) }
                }
            }
        }
    }
}
