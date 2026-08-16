package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.core.network.ShoppingListItemResponse
import com.foodmind.foodmind_android.core.network.ShoppingListResponse
import com.foodmind.foodmind_android.core.network.UpdateShoppingListItemRequest
import kotlinx.coroutines.launch

class ShoppingListsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        setContent {
            FoodMindTheme {
                ShoppingListsScreen(foodMindApiClient(), ::finish) {
                    startActivity(ShoppingListActivity.intent(this, it))
                }
            }
        }
    }
}

@Composable
private fun ShoppingListsScreen(client: FoodMindApiClient, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var lists by remember { mutableStateOf<List<ShoppingListResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        loading = true
        runCatching { client.shoppingLists().items }
            .onSuccess { lists = it; error = null }
            .onFailure { error = friendlyCookingError(it, "Could not load shopping lists.") }
        loading = false
    }
    FoodMindDetailScaffold("Shopping lists", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("PERSISTED SHOPPING", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Your shopping lists.", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Open lists stay where you left them when you pocket your phone or reopen the app.", color = FoodMindMuted)
            }
            if (loading) item { CircularProgressIndicator() }
            error?.let { message -> item { Text(message, color = FoodMindCoral); TextButton(onClick = { reload++ }) { Text("Try again") } } }
            if (!loading && error == null && lists.isEmpty()) item {
                FoodMindSurfaceCard { Text("No shopping lists yet. Generate a Cooking Plan with missing inventory to create one.", color = FoodMindMuted) }
            }
            items(lists, key = { it.shoppingListId }) { list ->
                Card(
                    onClick = { onOpen(list.shoppingListId) },
                    colors = CardDefaults.cardColors(containerColor = FoodMindSurface),
                    border = BorderStroke(1.dp, FoodMindLineSoft),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(list.status, color = FoodMindGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${list.checkedItemCount} of ${list.totalItemCount} purchased", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (list.status == "COMPLETED") "Purchases committed to inventory" else "Tap to continue shopping", color = FoodMindMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

class ShoppingListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        val id = intent.getStringExtra(EXTRA_SHOPPING_LIST_ID).orEmpty()
        setContent { FoodMindTheme { ShoppingListScreen(foodMindApiClient(), id, ::finish) } }
    }

    companion object {
        private const val EXTRA_SHOPPING_LIST_ID = "shopping_list_id"
        fun intent(context: Context, shoppingListId: String) =
            Intent(context, ShoppingListActivity::class.java).putExtra(EXTRA_SHOPPING_LIST_ID, shoppingListId)
    }
}

@Composable
private fun ShoppingListScreen(client: FoodMindApiClient, shoppingListId: String, onBack: () -> Unit) {
    var shoppingList by remember { mutableStateOf<ShoppingListResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var completing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(shoppingListId, reload) {
        loading = true
        runCatching { client.shoppingList(shoppingListId) }
            .onSuccess { shoppingList = it; error = null }
            .onFailure { error = friendlyCookingError(it, "Could not open this shopping list.") }
        loading = false
    }

    FoodMindDetailScaffold("Shopping list", onBack) { padding ->
        when {
            loading -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            shoppingList == null -> Column(Modifier.padding(padding).padding(24.dp)) {
                Text(error.orEmpty(), color = FoodMindCoral)
                TextButton(onClick = { reload++ }) { Text("Try again") }
            }
            else -> {
                val list = shoppingList!!
                val allChecked = list.totalItemCount > 0 && list.checkedItemCount == list.totalItemCount
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text("${list.status} · ${list.checkedItemCount}/${list.totalItemCount} PURCHASED", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Shopping list", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Check off each item as you shop.", color = FoodMindMuted, modifier = Modifier.padding(top = 6.dp))
                        LinearProgressIndicator(
                            progress = { if (list.totalItemCount == 0) 0f else list.checkedItemCount.toFloat() / list.totalItemCount },
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        )
                    }
                    items(list.items, key = { it.itemId }) { item ->
                        ShoppingItemEditor(item, list.status == "OPEN") { request ->
                            shoppingList = client.updateShoppingListItem(list.shoppingListId, item, request)
                        }
                    }
                    error?.let { item { Text(it, color = FoodMindCoral) } }
                    if (list.status == "OPEN") item {
                        Button(
                            onClick = {
                                scope.launch {
                                    completing = true
                                    error = null
                                    runCatching { client.completeShoppingList(list.shoppingListId) }
                                        .onSuccess { response ->
                                            val planId = response.body()?.planId.takeIf { response.isSuccessful }
                                            if (planId == null) error = "The backend did not return the continued plan."
                                            else {
                                                context.startActivity(CookingPlanDetailActivity.intent(context, planId))
                                                onBack()
                                            }
                                        }
                                        .onFailure { error = friendlyCookingError(it, "Could not add these purchases to inventory.") }
                                    completing = false
                                }
                            },
                            enabled = allChecked && !completing,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (completing) "Adding inventory…" else if (allChecked) "Everything purchased — continue" else "Check every item to continue") }
                    }
                    if (list.status == "COMPLETED") item {
                        FoodMindSurfaceCard { Column(Modifier.padding(14.dp)) {
                            Text("Purchases committed", fontWeight = FontWeight.Bold)
                            Text("These items are now part of your real inventory.", color = FoodMindMuted)
                            list.continuationPlanId?.let { planId ->
                                OutlinedButton(onClick = { context.startActivity(CookingPlanDetailActivity.intent(context, planId)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Text("Open continued Cooking Plan")
                                }
                            }
                        } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemEditor(
    item: ShoppingListItemResponse,
    editable: Boolean,
    onSave: suspend (UpdateShoppingListItemRequest) -> Unit,
) {
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun save(checked: Boolean) {
        scope.launch {
            saving = true
            error = null
            runCatching {
                onSave(
                    UpdateShoppingListItemRequest(
                        checked = checked,
                        purchasedQuantity = item.purchasedQuantity ?: item.requiredQuantity,
                        unit = item.unit,
                        expiryDate = null,
                    ),
                )
            }.onFailure { error = friendlyCookingError(it, "Could not update this item. Please try again.") }
            saving = false
        }
    }

    FoodMindSurfaceCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.checked, onCheckedChange = { save(it) }, enabled = editable && !saving)
                Column(Modifier.weight(1f)) {
                    Text(item.ingredientName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${item.requiredQuantity} ${item.unit} needed · ${item.purchasedQuantity ?: item.requiredQuantity} ${item.unit} planned",
                        color = FoodMindMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            error?.let { Text(it, color = FoodMindCoral, fontSize = 12.sp) }
        }
    }
}
