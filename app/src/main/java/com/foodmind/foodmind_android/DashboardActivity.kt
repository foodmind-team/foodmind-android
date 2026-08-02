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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.DashboardMetricResponse
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); val client = foodMindApiClient(); setContent { FoodMindTheme { DashboardScreen(client, ::finish) } } }
}

@Composable
private fun DashboardScreen(client: FoodMindApiClient, onBack: () -> Unit) {
    val today = remember { LocalDate.now() }; var tab by remember { mutableIntStateOf(0) }; var from by remember { mutableStateOf(today.minusDays(30).toString()) }; var to by remember { mutableStateOf(today.plusDays(1).toString()) }; var groupBy by remember { mutableStateOf("DAY") }
    var weekStart by remember { mutableStateOf(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()) }; var metrics by remember { mutableStateOf<List<DashboardMetricResponse>>(emptyList()) }; var spending by remember { mutableStateOf<List<DashboardMetricResponse>>(emptyList()) }
    var empty by remember { mutableStateOf(false) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }; var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(tab, from, to, groupBy, weekStart, refresh) {
        loading = true
        runCatching { if (tab == 0) client.dashboard(from, to, groupBy).let { Triple(it.metrics, it.spendingTotals, it.empty) } else client.weeklyRecap(weekStart).let { Triple(it.metrics, it.spendingTotals, it.empty) } }
            .onSuccess { (m, s, e) -> metrics = m; spending = s; empty = e; error = null }.onFailure { error = "Could not load analytics. Check the dates." }
        loading = false
    }
    FoodMindDetailScaffold("Food insights", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(tab) { Tab(tab == 0, { tab = 0 }, text = { Text("Dashboard") }); Tab(tab == 1, { tab = 1 }, text = { Text("Weekly recap") }) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(if (tab == 0) "See trends using backend metrics" else "Your weekly recap", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text("Metrics come directly from the backend and are not recalculated on the device.", color = FoodMindMuted) }
                if (tab == 0) {
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(from, { from = it }, label = { Text("Start date") }, modifier = Modifier.weight(1f)); OutlinedTextField(to, { to = it }, label = { Text("End date") }, modifier = Modifier.weight(1f)) } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("DAY", "WEEK", "MONTH").forEach { FilterChip(groupBy == it, { groupBy = it }, label = { Text(it) }) } } }
                } else item { OutlinedTextField(weekStart, { weekStart = it }, label = { Text("Monday date") }, modifier = Modifier.fillMaxWidth()) }
                if (loading) item { CircularProgressIndicator() }
                error?.let { item { Text(it, color = FoodMindCoral); TextButton(onClick = { refresh++ }) { Text("Try again") } } }
                if (!loading && empty) item { FoodMindSurfaceCard { Text("There is not enough data for this period yet.") } }
                items(metrics, key = { "${it.code}-${it.period}-${it.dimension}" }) { MetricCard(it) }
                if (spending.isNotEmpty()) item { Text("Spending", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp)) }
                items(spending, key = { "spend-${it.code}-${it.period}-${it.dimension}" }) { MetricCard(it) }
                item { Text("Empty values stay empty; the backend defines percentages and denominators.", color = FoodMindMuted, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun MetricCard(metric: DashboardMetricResponse) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(metric.label ?: metric.code ?: "Metrics", fontWeight = FontWeight.Bold); Text(if (metric.empty) "No data yet" else listOfNotNull(metric.value?.toString(), metric.currency, metric.unit).joinToString(" "), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FoodMindGreen, modifier = Modifier.padding(top = 7.dp))
            metric.dimensionLabel?.let { Text(it, color = FoodMindMuted) }; metric.denominator?.takeIf { it > 0 }?.let { denominator -> LinearProgressIndicator(progress = { ((metric.value ?: 0.0) / denominator).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 9.dp)) }
            Text(metric.period.orEmpty(), color = FoodMindMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
