package com.foodmind.foodmind_android

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.core.network.InventoryLotRequest
import com.foodmind.foodmind_android.core.network.InventoryLotResponse
import kotlinx.coroutines.launch

class CookingInventoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        setContent {
            FoodMindTheme {
                CookingInventoryScreen(
                    client = foodMindApiClient(),
                    onBack = ::finish,
                    onShoppingLists = { startActivity(Intent(this, ShoppingListsActivity::class.java)) },
                )
            }
        }
    }
}

@Composable
private fun CookingInventoryScreen(client: FoodMindApiClient, onBack: () -> Unit, onShoppingLists: () -> Unit) {
    var lots by remember { mutableStateOf<List<InventoryLotResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("g") }
    var expiry by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        loading = true
        runCatching { client.inventoryLots().items }
            .onSuccess { lots = it; error = null }
            .onFailure { error = friendlyCookingError(it, "Could not load inventory.") }
        loading = false
    }

    val createValid = name.isNotBlank() && quantity.toDoubleOrNull()?.let { it > 0 } == true && unit.isNotBlank()
    FoodMindDetailScaffold("Inventory", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("REAL INVENTORY", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("What is in your kitchen?", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Cooking Plan checks these active lots. Completed Shopping Lists are added here automatically.", color = FoodMindMuted)
                OutlinedButton(onClick = onShoppingLists, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Open shopping lists") }
            }
            item {
                FoodMindSurfaceCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add inventory", color = FoodMindGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Create a lot", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(name, { name = it.take(128) }, label = { Text("Ingredient") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(unit, { unit = it.take(16) }, label = { Text("Unit") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(expiry, { expiry = it }, label = { Text("Expiry date (YYYY-MM-DD, optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        scope.launch {
                            adding = true; error = null
                            runCatching { client.createInventoryLot(InventoryLotRequest(name.trim(), quantity.toDouble(), unit.trim(), expiry.ifBlank { null })) }
                                .onSuccess { saved -> lots = listOf(saved) + lots; name = ""; quantity = ""; expiry = "" }
                                .onFailure { error = friendlyCookingError(it, "Could not add this inventory lot.") }
                            adding = false
                        }
                    }, enabled = createValid && !adding, modifier = Modifier.fillMaxWidth()) { Text(if (adding) "Adding…" else "Add lot") }
                } }
            }
            error?.let { message -> item { Text(message, color = FoodMindCoral); TextButton(onClick = { reload++ }) { Text("Try again") } } }
            if (loading) item { CircularProgressIndicator() }
            if (!loading && error == null && lots.isEmpty()) item { Text("Your inventory is empty. Add what you have before generating a Cooking Plan.", color = FoodMindMuted) }
            items(lots, key = { it.lotId }) { lot ->
                InventoryLotEditor(
                    lot = lot,
                    onUpdated = { updated -> lots = lots.map { if (it.lotId == updated.lotId) updated else it } },
                    onArchived = { archivedId -> lots = lots.filterNot { it.lotId == archivedId } },
                    update = { request -> client.updateInventoryLot(lot, request) },
                    archive = { client.archiveInventoryLot(lot) },
                )
            }
        }
    }
}

@Composable
private fun InventoryLotEditor(
    lot: InventoryLotResponse,
    onUpdated: (InventoryLotResponse) -> Unit,
    onArchived: (String) -> Unit,
    update: suspend (InventoryLotRequest) -> InventoryLotResponse,
    archive: suspend () -> Unit,
) {
    var editing by remember(lot.version) { mutableStateOf(false) }
    var name by remember(lot.version) { mutableStateOf(lot.ingredientName) }
    var quantity by remember(lot.version) { mutableStateOf(lot.quantity.toString()) }
    var unit by remember(lot.version) { mutableStateOf(lot.unit) }
    var expiry by remember(lot.version) { mutableStateOf(lot.expiryDate.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val valid = name.isNotBlank() && quantity.toDoubleOrNull()?.let { it > 0 } == true && unit.isNotBlank()

    FoodMindSurfaceCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(lot.ingredientName, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("${lot.available} ${lot.unit} available · ${lot.reserved} reserved", color = FoodMindGreen, fontSize = 12.sp)
            lot.expiryDate?.let { Text("Expires $it", color = FoodMindMuted, fontSize = 12.sp) }
            if (editing) {
                OutlinedTextField(name, { name = it.take(128) }, label = { Text("Ingredient") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(unit, { unit = it.take(16) }, label = { Text("Unit") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(expiry, { expiry = it }, label = { Text("Expiry date") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (!editing) editing = true else scope.launch {
                        busy = true; error = null
                        runCatching { update(InventoryLotRequest(name.trim(), quantity.toDouble(), unit.trim(), expiry.ifBlank { null })) }
                            .onSuccess { onUpdated(it); editing = false }
                            .onFailure { error = friendlyCookingError(it, "Could not update this lot. Reload and try again.") }
                        busy = false
                    }
                }, enabled = !busy && (!editing || valid), modifier = Modifier.weight(1f)) { Text(if (editing) "Save" else "Edit") }
                OutlinedButton(onClick = {
                    scope.launch {
                        busy = true; error = null
                        runCatching { archive() }
                            .onSuccess { onArchived(lot.lotId) }
                            .onFailure { error = friendlyCookingError(it, "Could not archive this lot. Reload and try again.") }
                        busy = false
                    }
                }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Archive") }
            }
            error?.let { Text(it, color = FoodMindCoral, fontSize = 12.sp) }
        }
    }
}
