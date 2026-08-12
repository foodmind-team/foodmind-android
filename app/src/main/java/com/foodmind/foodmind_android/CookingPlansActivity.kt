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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.CookingPlanSummary
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.formatFoodMindTimestamp

/**
 * Plan history — ported from cooking-app's PlansScreen. Reads the real
 * /cooking-plans/history endpoint and opens each plan's detail board.
 */
class CookingPlansActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = foodMindApiClient()
        setContent { FoodMindTheme { CookingPlansScreen(client, ::finish, { startActivity(CookingPlanDetailActivity.intent(this, it)) }) } }
    }
}

@Composable
private fun CookingPlansScreen(client: FoodMindApiClient, onBack: () -> Unit, onOpenPlan: (String) -> Unit) {
    var items by remember { mutableStateOf<List<CookingPlanSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true; error = null
        runCatching { client.cookingPlanHistory(0).items }
            .onSuccess { items = it }
            .onFailure { error = "Could not load cooking history." }
        loading = false
    }
    FoodMindDetailScaffold("Cooking history", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text("COOKING HISTORY", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Plans you have generated.", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Every cooking plan the backend accepted for this account, newest first.", color = FoodMindMuted, modifier = Modifier.padding(top = 6.dp))
            }
            when {
                loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                error != null -> Column(Modifier.padding(20.dp)) {
                    Text(error.orEmpty(), color = FoodMindCoral)
                    TextButton(onClick = { refresh++ }) { Text("Try again") }
                }
                items.isEmpty() -> Text("No cooking plans yet. Generate one from the Cook screen.", color = FoodMindMuted, modifier = Modifier.padding(20.dp))
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.planId.orEmpty() }) { plan ->
                        Card(
                            onClick = { plan.planId?.let(onOpenPlan) },
                            colors = CardDefaults.cardColors(containerColor = FoodMindSurface),
                            border = BorderStroke(1.dp, FoodMindLineSoft),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(plan.status.orEmpty().replace('_', ' '), color = if (plan.status == "READY") FoodMindGreen else FoodMindCoral, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("${plan.sourceCount} sources · ${plan.taskCount} tasks", fontWeight = FontWeight.Bold)
                                    Text("${plan.makespanMinutes?.let { "$it minutes" } ?: "—"} · ${formatFoodMindTimestamp(plan.createdAt)}", color = FoodMindMuted, fontSize = 12.sp)
                                }
                                Icon(Icons.Outlined.ChevronRight, null, tint = FoodMindMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
