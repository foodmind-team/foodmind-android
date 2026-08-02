package com.foodmind.foodmind_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.HistoryEntryResponse
import kotlinx.coroutines.launch
import java.time.LocalDate

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); val client = foodMindApiClient(); setContent { FoodMindTheme { HistoryScreen(client, ::finish) { type, id -> startActivity(RecordDetailActivity.intent(this, type, id)) } } } }
}

@Composable
private fun HistoryScreen(client: FoodMindApiClient, onBack: () -> Unit, onOpen: (String, String) -> Unit) {
    val today = remember { LocalDate.now() }; var from by remember { mutableStateOf(today.minusDays(30).toString()) }; var to by remember { mutableStateOf(today.plusDays(1).toString()) }; var type by remember { mutableStateOf<String?>(null) }; var period by remember { mutableStateOf("DAY") }; var groupId by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<HistoryEntryResponse>>(emptyList()) }; var cursor by remember { mutableStateOf<String?>(null) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }; var refresh by remember { mutableStateOf(0) }; val scope = rememberCoroutineScope()
    LaunchedEffect(from, to, type, period, groupId, refresh) { loading = true; runCatching { client.history(from, to, period, type, groupId.ifBlank { null }) }.onSuccess { entries = it.entries; cursor = it.nextCursor; error = null }.onFailure { error = "Could not load history. Check the date range." }; loading = false }
    FoodMindDetailScaffold("History", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Meals and drinks", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Text("The backend returns combined history for your time zone and selected filters.", color = FoodMindMuted) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(from, { from = it }, label = { Text("Start") }, modifier = Modifier.weight(1f)); OutlinedTextField(to, { to = it }, label = { Text("End") }, modifier = Modifier.weight(1f)) } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf(null to "All", "FOOD" to "Meal", "DRINK" to "Drink").forEach { (code, label) -> FilterChip(type == code, { type = code }, label = { Text(label) }) } } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("DAY", "WEEK", "MONTH").forEach { FilterChip(period == it, { period = it }, label = { Text(it) }) } } }
            item { OutlinedTextField(groupId, { groupId = it }, label = { Text("Group ID (optional)") }, modifier = Modifier.fillMaxWidth()) }
            if (loading) item { CircularProgressIndicator() }
            error?.let { item { Text(it, color = FoodMindCoral); TextButton(onClick = { refresh++ }) { Text("Try again") } } }
            if (!loading && entries.isEmpty() && error == null) item { FoodMindSurfaceCard { Text("No records in this date range.") } }
            items(entries, key = { "${it.sourceType}-${it.sourceId}" }) { entry ->
                Card(onClick = { entry.sourceId?.let { onOpen(if (entry.sourceType == "DRINK") "DRINK" else "FOOD", it) } }, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp)) { Text(entry.title ?: "Untitled record", fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(entry.context.orEmpty(), color = FoodMindMuted); Text("${entry.sourceType} · ${entry.occurredAt}", color = FoodMindMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp)) }
                }
            }
            cursor?.let { next -> item { TextButton(onClick = { scope.launch { runCatching { client.history(from, to, period, type, groupId.ifBlank { null }, next) }.onSuccess { page -> entries = entries + page.entries; cursor = page.nextCursor } } }, modifier = Modifier.fillMaxWidth()) { Text("Load more") } } }
        }
    }
}
