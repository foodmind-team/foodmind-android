package com.foodmind.foodmind_android

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
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
import com.foodmind.foodmind_android.core.network.InventoryLotRequest
import com.foodmind.foodmind_android.core.network.InventoryLotResponse
import kotlinx.coroutines.launch

class InventoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FoodMindTheme { InventoryScreen(foodMindApiClient(), ::finish) } }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun InventoryScreen(client: FoodMindApiClient, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var lots by remember { mutableStateOf<List<InventoryLotResponse>>(emptyList()) }
    var editing by remember { mutableStateOf<InventoryLotResponse?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var archiveTarget by remember { mutableStateOf<InventoryLotResponse?>(null) }
    var ingredient by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("g") }
    var expiry by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }

    fun clearDraft() {
        editing = null
        ingredient = ""
        quantity = ""
        unit = "g"
        expiry = ""
        showEditor = false
    }

    if (showEditor) {
        ModalBottomSheet(onDismissRequest = ::clearDraft) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(if (editing == null) "Add ingredient" else "Edit ingredient", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                OutlinedTextField(ingredient, { ingredient = it }, label = { Text("Ingredient") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(expiry, { expiry = it }, label = { Text("Expiry date (YYYY-MM-DD, optional)") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = FoodMindCoral) }
                Button(
                    onClick = {
                        val amount = quantity.toDoubleOrNull()
                        if (ingredient.isBlank() || amount == null || amount <= 0 || unit.isBlank()) {
                            error = "Ingredient, positive quantity, and unit are required."
                            return@Button
                        }
                        scope.launch {
                            saving = true
                            val wasEditing = editing != null
                            val request = InventoryLotRequest(ingredient.trim(), amount, unit.trim(), expiry.trim().ifBlank { null })
                            runCatching {
                                editing?.let { client.updateInventoryLot(it.lotId, it.version, request) }
                                    ?: client.createInventoryLot(request)
                            }.onSuccess {
                                clearDraft()
                                notice = if (wasEditing) "Ingredient updated." else "Ingredient added."
                                refresh++
                            }.onFailure { error = it.message ?: "Could not save ingredient." }
                            saving = false
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (saving) "Saving…" else if (editing == null) "Add ingredient" else "Save changes") }
            }
        }
    }

    archiveTarget?.let { lot ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text("Archive ${lot.ingredientName}?") },
            text = { Text("This removes the lot from active inventory. Cooking plans will no longer count it as available.") },
            dismissButton = { TextButton(onClick = { archiveTarget = null }) { Text("Cancel") } },
            confirmButton = { Button(onClick = {
                archiveTarget = null
                scope.launch {
                    runCatching { client.archiveInventoryLot(lot.lotId, lot.version) }
                        .onSuccess {
                            if (editing?.lotId == lot.lotId) clearDraft()
                            notice = "${lot.ingredientName} archived."
                            refresh++
                        }
                        .onFailure { error = it.message ?: "Could not archive ingredient." }
                }
            }) { Text("Archive") } },
        )
    }

    LaunchedEffect(refresh) {
        loading = true
        runCatching { client.inventoryLots().items }
            .onSuccess { lots = it; error = null }
            .onFailure { error = it.message ?: "Could not load inventory." }
        loading = false
    }

    FoodMindDetailScaffold(
        title = "Inventory",
        onBack = onBack,
        actions = { IconButton(onClick = { refresh++ }) { Icon(Icons.Outlined.Refresh, "Refresh inventory") } },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("What is in your kitchen?", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("See what is available before adding or editing ingredients.", color = FoodMindMuted)
                Button(onClick = { clearDraft(); showEditor = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Add ingredient") }
            }
            notice?.let { message -> item { Text(message, color = FoodMindGreen) } }
            error?.takeIf { !showEditor }?.let { message -> item { Text(message, color = FoodMindCoral) } }
            if (loading) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
            if (!loading && lots.isEmpty()) item { FoodMindSurfaceCard { Text("Inventory is empty. Add your first ingredient.") } }
            items(lots, key = InventoryLotResponse::lotId) { lot ->
                FoodMindSurfaceCard {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(lot.ingredientName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${lot.available} ${lot.unit} available · ${lot.reserved} reserved", color = FoodMindMuted)
                            Text(lot.expiryDate?.let { "Expires $it" } ?: "No expiry date", color = FoodMindMuted, fontSize = 12.sp)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { client.inventoryLot(lot.lotId) }
                                    .onSuccess { detail ->
                                        editing = detail
                                        ingredient = detail.ingredientName
                                        quantity = detail.quantity.toString()
                                        unit = detail.unit
                                        expiry = detail.expiryDate.orEmpty()
                                        showEditor = true
                                    }
                                    .onFailure { error = it.message ?: "Could not load lot detail." }
                            }
                        }) { Icon(Icons.Outlined.Edit, "Edit ${lot.ingredientName}") }
                        IconButton(onClick = { archiveTarget = lot }) { Icon(Icons.Outlined.Archive, "Archive ${lot.ingredientName}") }
                    }
                }
            }
        }
    }
}
