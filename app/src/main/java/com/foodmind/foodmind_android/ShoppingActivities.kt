package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.ShoppingListItemResponse
import com.foodmind.foodmind_android.core.network.ShoppingListResponse
import com.foodmind.foodmind_android.core.network.UpdateShoppingListItemRequest
import kotlinx.coroutines.launch

class ShoppingListsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoodMindTheme {
                ShoppingListsScreen(foodMindApiClient(), ::finish) {
                    startActivity(ShoppingListDetailActivity.intent(this, it))
                }
            }
        }
    }
}

class ShoppingListDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = requireNotNull(intent.getStringExtra(EXTRA_LIST_ID))
        setContent {
            FoodMindTheme {
                ShoppingListDetailScreen(foodMindApiClient(), id, ::finish) { planId ->
                    startActivity(CookingPlanDetailActivity.intent(this, planId))
                }
            }
        }
    }

    companion object {
        private const val EXTRA_LIST_ID = "shopping_list_id"
        fun intent(context: Context, id: String) = Intent(context, ShoppingListDetailActivity::class.java)
            .putExtra(EXTRA_LIST_ID, id)
    }
}

@Composable
private fun ShoppingListsScreen(client: FoodMindApiClient, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var status by remember { mutableStateOf("ALL") }
    var lists by remember { mutableStateOf<List<ShoppingListResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh, status) {
        loading = true
        runCatching { client.shoppingLists(status.takeUnless { it == "ALL" }).items }
            .onSuccess { lists = it; error = null }
            .onFailure { error = it.message ?: "Could not load shopping lists." }
        loading = false
    }
    FoodMindDetailScaffold(
        title = "Shopping lists",
        onBack = onBack,
        actions = { IconButton(onClick = { refresh++ }) { Icon(Icons.Outlined.Refresh, "Refresh shopping lists") } },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Persisted shopping", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Lists and purchase progress stay consistent across Web and Android.", color = FoodMindMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    listOf("ALL", "OPEN", "COMPLETED").forEach { value ->
                        FilterChip(status == value, { status = value }, label = { Text(value.lowercase().replaceFirstChar(Char::uppercase)) })
                    }
                }
            }
            error?.let { item { Text(it, color = FoodMindCoral) } }
            if (loading) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
            if (!loading && lists.isEmpty()) item { FoodMindSurfaceCard { Text("No ${status.lowercase()} shopping lists yet. Create one from an infeasible Cooking Plan.") } }
            items(lists, key = ShoppingListResponse::shoppingListId) { list ->
                FoodMindSurfaceCard(Modifier.clickable { onOpen(list.shoppingListId) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${list.checkedItemCount} of ${list.totalItemCount} purchased", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${list.status} · ${list.originalServings} servings", color = FoodMindMuted)
                            Text("Updated ${list.updatedAt}", color = FoodMindMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Outlined.ChevronRight, "Open shopping list")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingListDetailScreen(
    client: FoodMindApiClient,
    shoppingListId: String,
    onBack: () -> Unit,
    onOpenPlan: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<ShoppingListResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var completing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh, shoppingListId) {
        loading = true
        runCatching { client.shoppingList(shoppingListId) }
            .onSuccess { list = it; error = null }
            .onFailure { error = it.message ?: "Could not load shopping list." }
        loading = false
    }
    FoodMindDetailScaffold(
        title = "Shopping list",
        onBack = onBack,
        actions = { IconButton(onClick = { refresh++ }) { Icon(Icons.Outlined.Refresh, "Refresh shopping list") } },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loading) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
            error?.let { item { Text(it, color = FoodMindCoral) } }
            list?.let { current ->
                item {
                    Text("Buy for ${current.originalServings} servings", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${current.checkedItemCount}/${current.totalItemCount} purchased · ${current.status}", color = FoodMindMuted)
                }
                items(current.items, key = ShoppingListItemResponse::itemId) { item ->
                    ShoppingItemCard(current, item) { request ->
                        scope.launch {
                            runCatching {
                                client.updateShoppingListItem(current.shoppingListId, item.itemId, item.version, request)
                            }.onSuccess { list = it; error = null }
                                .onFailure { error = it.message ?: "Could not update shopping item." }
                        }
                    }
                }
                if (current.status == "OPEN") item {
                    val complete = current.items.isNotEmpty() && current.items.all(ShoppingListItemResponse::checked)
                    Button(
                        onClick = {
                            scope.launch {
                                completing = true
                                runCatching { client.completeShoppingList(current.shoppingListId) }
                                    .onSuccess { response ->
                                        val accepted = response.body()
                                        accepted?.planId?.let(onOpenPlan) ?: run { error = "Completion returned no continuation plan." }
                                    }
                                    .onFailure { error = it.message ?: "Could not complete shopping list." }
                                completing = false
                            }
                        },
                        enabled = complete && !completing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (completing) "Adding purchases…" else "Everything purchased — continue") }
                }
                if (current.status == "COMPLETED") item { FoodMindSurfaceCard { Text("Purchases are committed to inventory.") } }
            }
        }
    }
}

@Composable
private fun ShoppingItemCard(
    list: ShoppingListResponse,
    item: ShoppingListItemResponse,
    onSave: (UpdateShoppingListItemRequest) -> Unit,
) {
    var checked by remember(item.version) { mutableStateOf(item.checked) }
    var quantity by remember(item.version) { mutableStateOf(item.purchasedQuantity.toString()) }
    var unit by remember(item.version) { mutableStateOf(item.unit) }
    var expiry by remember(item.version) { mutableStateOf(item.expiryDate.orEmpty()) }
    val valid = quantity.toDoubleOrNull()?.let { it > 0 } == true && unit.isNotBlank()
    FoodMindSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    enabled = list.status == "OPEN",
                )
                Column {
                    Text(item.ingredientName, fontWeight = FontWeight.Bold)
                    Text("Need ${item.requiredQuantity} ${item.unit}", color = FoodMindMuted, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Purchased") }, enabled = list.status == "OPEN", modifier = Modifier.weight(1f))
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, enabled = list.status == "OPEN", modifier = Modifier.weight(1f))
            }
            OutlinedTextField(expiry, { expiry = it }, label = { Text("Expiry date (optional)") }, enabled = list.status == "OPEN", modifier = Modifier.fillMaxWidth())
            if (list.status == "OPEN") Button(
                onClick = { onSave(UpdateShoppingListItemRequest(checked, quantity.toDouble(), unit.trim(), expiry.trim().ifBlank { null })) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save item") }
        }
    }
}
