package com.foodmind.foodmind_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.DashboardMetricResponse
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private val activityCodes = setOf("FOOD_COUNT", "DRINK_COUNT", "FOOD_DRINK_COUNT")
private val outcomeCodes = setOf("ACCEPTANCE_RATE", "REJECTION_RATE", "WOULD_AGAIN_RATE", "RECOMMENDATION_WOULD_EAT_AGAIN_RATE", "REJECTION_REASON", "SELECTED_CANDIDATE_TYPE")
private val insightColors = listOf(FoodMindLime, FoodMindGreen, FoodMindCoral, Color(0xFFD4A72C), Color(0xFF6CA4A1))

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = foodMindApiClient()
        setContent { FoodMindTheme { DashboardScreen(client, ::finish) } }
    }
}

internal fun latestInsightMetric(metrics: List<DashboardMetricResponse>, codes: Set<String>): DashboardMetricResponse? =
    metrics.filter { it.code in codes && !it.empty && it.value != null }.maxByOrNull { it.period.orEmpty() }

internal fun formatInsightMetric(metric: DashboardMetricResponse?): String {
    if (metric == null || metric.empty || metric.value == null) return "No data"
    val value = metric.value
    return when (metric.unit) {
        "MONEY" -> listOfNotNull(metric.currency ?: metric.dimension, NumberFormat.getNumberInstance().format(value)).joinToString(" ")
        "RATE" -> "%.1f%%".format(value * 100)
        "RATING" -> "%.1f".format(value)
        else -> NumberFormat.getNumberInstance().format(value)
    }
}

@Composable
private fun DashboardScreen(client: FoodMindApiClient, onBack: () -> Unit) {
    val today = remember { LocalDate.now() }
    var tab by remember { mutableIntStateOf(0) }
    var from by remember { mutableStateOf(today.minusDays(30).toString()) }
    var to by remember { mutableStateOf(today.plusDays(1).toString()) }
    var groupBy by remember { mutableStateOf("WEEK") }
    var weekStart by remember { mutableStateOf(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()) }
    var metrics by remember { mutableStateOf<List<DashboardMetricResponse>>(emptyList()) }
    var spending by remember { mutableStateOf<List<DashboardMetricResponse>>(emptyList()) }
    var empty by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, from, to, groupBy, weekStart, refresh) {
        loading = true
        runCatching {
            if (tab == 0) client.dashboard(from, to, groupBy).let { Triple(it.metrics, it.spendingTotals, it.empty) }
            else client.weeklyRecap(weekStart).let { Triple(it.metrics, it.spendingTotals, it.empty) }
        }.onSuccess { (newMetrics, newSpending, isEmpty) ->
            metrics = newMetrics
            spending = newSpending
            empty = isEmpty
            error = null
        }.onFailure { error = "Could not load insights. Check the dates." }
        loading = false
    }

    FoodMindDetailScaffold("Insights", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Trends") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Weekly recap") })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Your food story", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                        Text("See how activity, spending, tastes, and recommendation outcomes fit together.", color = FoodMindMuted)
                    }
                }
                if (tab == 0) {
                    item { DateRangeControls(from, { from = it }, to, { to = it }, groupBy, { groupBy = it }) }
                } else {
                    item { OutlinedTextField(weekStart, { weekStart = it }, label = { Text("Monday date") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                }
                if (loading) item { CircularProgressIndicator() }
                error?.let { message -> item { Text(message, color = FoodMindCoral); TextButton(onClick = { refresh++ }) { Text("Try again") } } }
                if (!loading && empty) item { FoodMindSurfaceCard { Text("There is not enough data for this period yet.") } }
                if (!loading && !empty) {
                    item { InsightStory(metrics, spending) }
                    item { DiagramCard("Activity trend", "Food and drink counts share a time axis.") { ActivityLineDiagram(metrics.filter { it.code in activityCodes }) } }
                    item { DiagramCard("Spending over time", "Currencies stay separate and are never combined.") { SpendingBarDiagram(spending) } }
                    item { DiagramCard("Cuisine mix", "Backend cuisine dimensions form the recorded mix.") { CuisineDiagram(metrics.filter { it.code == "CUISINE_DISTRIBUTION" }) } }
                    item { DiagramCard("Recommendation outcomes", "Rates and counts use separate scales.") { OutcomeDiagram(metrics.filter { it.code in outcomeCodes }) } }
                    item { RawDataDisclosure(metrics, spending) }
                }
            }
        }
    }
}

@Composable
private fun DateRangeControls(from: String, onFrom: (String) -> Unit, to: String, onTo: (String) -> Unit, groupBy: String, onGroupBy: (String) -> Unit) {
    FoodMindSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Period", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, onFrom, label = { Text("From") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(to, onTo, label = { Text("To") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DAY", "WEEK", "MONTH").forEach { option -> FilterChip(groupBy == option, { onGroupBy(option) }, label = { Text(option.lowercase().replaceFirstChar(Char::uppercase)) }) }
            }
        }
    }
}

private data class StoryNode(val title: String, val metric: DashboardMetricResponse?, val hint: String, val color: Color)

@Composable
private fun InsightStory(metrics: List<DashboardMetricResponse>, spending: List<DashboardMetricResponse>) {
    val nodes = listOf(
        StoryNode("Activity", latestInsightMetric(metrics, setOf("FOOD_DRINK_COUNT", "FOOD_COUNT", "DRINK_COUNT")), "Recorded", FoodMindGreen),
        StoryNode("Spending", latestInsightMetric(spending, setOf("SPENDING_TOTAL")), "Cost", Color(0xFFD4A72C)),
        StoryNode("Cuisine", latestInsightMetric(metrics, setOf("CUISINE_DISTRIBUTION")), "Taste", FoodMindLime),
        StoryNode("Rating", latestInsightMetric(metrics, setOf("MEAN_RATING")), "Feeling", FoodMindGreen),
        StoryNode("Outcomes", latestInsightMetric(metrics, setOf("ACCEPTANCE_RATE", "WOULD_AGAIN_RATE", "RECOMMENDATION_WOULD_EAT_AGAIN_RATE")), "Worked", FoodMindCoral),
    )
    FoodMindSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column {
                Text("Relationship overview", color = FoodMindGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Read the signals together", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Connections organize the data; they do not claim causation.", color = FoodMindMuted, fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                nodes.forEachIndexed { index, node ->
                    StoryNodeView(node, Modifier.weight(1f))
                    if (index < nodes.lastIndex) Box(Modifier.padding(top = 22.dp).width(7.dp).height(2.dp).background(node.color.copy(alpha = .55f)))
                }
            }
        }
    }
}

@Composable
private fun StoryNodeView(node: StoryNode, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp).background(node.color.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
            Text(node.title.take(1), color = node.color, fontWeight = FontWeight.ExtraBold)
        }
        Text(node.title, fontSize = 9.sp, color = FoodMindMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(formatInsightMetric(node.metric), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(node.metric?.dimensionLabel ?: node.hint, fontSize = 8.sp, color = FoodMindMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DiagramCard(title: String, summary: String, content: @Composable () -> Unit) {
    FoodMindSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text(summary, color = FoodMindMuted, fontSize = 11.sp)
            }
            content()
        }
    }
}

@Composable
private fun ActivityLineDiagram(metrics: List<DashboardMetricResponse>) {
    val series = metrics.filter { !it.empty && it.value != null }.groupBy { it.label ?: it.code.orEmpty() }
    val maxValue = series.values.flatten().maxOfOrNull { it.value ?: 0.0 }?.takeIf { it > 0 } ?: 1.0
    if (series.isEmpty()) return Text("No activity data", color = FoodMindMuted)
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(FoodMindLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        series.entries.forEachIndexed { seriesIndex, (_, values) ->
            val sorted = values.sortedBy { it.period }
            val path = Path()
            sorted.forEachIndexed { index, metric ->
                val x = if (sorted.size == 1) size.width / 2 else size.width * index / (sorted.size - 1)
                val y = size.height - ((metric.value ?: 0.0) / maxValue * size.height).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(insightColors[seriesIndex % insightColors.size], 4.5f, Offset(x, y))
            }
            drawPath(path, insightColors[seriesIndex % insightColors.size], style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { series.keys.forEachIndexed { index, label -> Text("● $label", color = insightColors[index % insightColors.size], fontSize = 10.sp) } }
}

@Composable
private fun SpendingBarDiagram(metrics: List<DashboardMetricResponse>) {
    val values = metrics.filter { !it.empty && it.value != null }.sortedBy { it.period }
    val maxValue = values.maxOfOrNull { it.value ?: 0.0 }?.takeIf { it > 0 } ?: 1.0
    if (values.isEmpty()) return Text("No spending data", color = FoodMindMuted)
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val gap = 7.dp.toPx()
        val barWidth = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(3.dp.toPx())
        values.forEachIndexed { index, metric ->
            val height = ((metric.value ?: 0.0) / maxValue * size.height).toFloat()
            drawRect(insightColors[index % insightColors.size], Offset(index * (barWidth + gap), size.height - height), Size(barWidth, height))
        }
    }
    Text(values.mapNotNull { it.currency ?: it.dimension }.distinct().joinToString(" · "), color = FoodMindMuted, fontSize = 10.sp)
}

@Composable
private fun CuisineDiagram(metrics: List<DashboardMetricResponse>) {
    val values = metrics.filter { !it.empty && it.value != null && it.value > 0 }
    val total = values.sumOf { it.value ?: 0.0 }
    if (values.isEmpty() || total <= 0) return Text("No cuisine data", color = FoodMindMuted)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Canvas(Modifier.size(150.dp)) {
            var start = -90f
            values.forEachIndexed { index, metric ->
                val sweep = (((metric.value ?: 0.0) / total) * 360).toFloat()
                drawArc(insightColors[index % insightColors.size], start, sweep, false, style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            values.forEachIndexed { index, metric -> Text("● ${metric.dimensionLabel ?: metric.dimension ?: metric.label}: ${formatInsightMetric(metric)}", color = insightColors[index % insightColors.size], fontSize = 10.sp) }
        }
    }
}

@Composable
private fun OutcomeDiagram(metrics: List<DashboardMetricResponse>) {
    val valid = metrics.filter { !it.empty && it.value != null }
    val rates = valid.filter { it.unit == "RATE" }
    val counts = valid.filter { it.unit == "COUNT" }
    val maxCount = counts.maxOfOrNull { it.value ?: 0.0 }?.takeIf { it > 0 } ?: 1.0
    if (valid.isEmpty()) return Text("No recommendation outcome data", color = FoodMindMuted)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rates.forEach { metric -> OutcomeBar(metric, (metric.value ?: 0.0).toFloat().coerceIn(0f, 1f)) }
        if (rates.isNotEmpty() && counts.isNotEmpty()) HorizontalDivider(color = FoodMindLine)
        counts.forEach { metric -> OutcomeBar(metric, ((metric.value ?: 0.0) / maxCount).toFloat().coerceIn(0f, 1f)) }
    }
}

@Composable
private fun OutcomeBar(metric: DashboardMetricResponse, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(metric.dimensionLabel ?: metric.label ?: metric.code.orEmpty(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(formatInsightMetric(metric), color = FoodMindMuted, fontSize = 11.sp)
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = if (metric.unit == "RATE") FoodMindLime else FoodMindCoral, trackColor = FoodMindLine)
    }
}

@Composable
private fun RawDataDisclosure(metrics: List<DashboardMetricResponse>, spending: List<DashboardMetricResponse>) {
    var expanded by remember { mutableStateOf(false) }
    val metricKeys = metrics.map { "${it.code}-${it.period}-${it.dimension}-${it.currency}" }.toSet()
    val rows = metrics + spending.filter { "${it.code}-${it.period}-${it.dimension}-${it.currency}" !in metricKeys }
    FoodMindSurfaceCard {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Backend values", fontWeight = FontWeight.Bold); Text("${rows.size} metric rows", color = FoodMindMuted, fontSize = 11.sp) }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide data" else "View data") }
            }
            if (expanded) {
                HorizontalDivider(color = FoodMindLine, modifier = Modifier.padding(vertical = 10.dp))
                rows.forEachIndexed { index, metric ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text(metric.dimensionLabel ?: metric.label ?: metric.code.orEmpty(), fontSize = 11.sp); Text(metric.period.orEmpty(), color = FoodMindMuted, fontSize = 9.sp) }
                        Text(formatInsightMetric(metric), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    }
                    if (index < rows.lastIndex) HorizontalDivider(color = FoodMindLineSoft)
                }
            }
        }
    }
}
