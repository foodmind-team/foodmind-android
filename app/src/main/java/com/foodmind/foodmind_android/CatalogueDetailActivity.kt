package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.launch

private data class CatalogueDetailData(
    val title: String,
    val subtitle: String,
    val description: String?,
    val tags: List<String>,
    val facts: List<Pair<String, String>>,
    val warnings: List<String> = emptyList(),
)

class CatalogueDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceType = intent.getStringExtra(EXTRA_SOURCE_TYPE).orEmpty()
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID).orEmpty()
        val client = foodMindApiClient()
        setContent { FoodMindTheme { CatalogueDetailScreen(client, sourceType, sourceId, ::finish) } }
    }

    companion object {
        private const val EXTRA_SOURCE_TYPE = "source_type"
        private const val EXTRA_SOURCE_ID = "source_id"
        fun intent(context: Context, sourceType: String, sourceId: String) = Intent(context, CatalogueDetailActivity::class.java)
            .putExtra(EXTRA_SOURCE_TYPE, sourceType).putExtra(EXTRA_SOURCE_ID, sourceId)
    }
}

@Composable
private fun CatalogueDetailScreen(client: FoodMindApiClient, sourceType: String, sourceId: String, onBack: () -> Unit) {
    var data by remember { mutableStateOf<CatalogueDetailData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sourceType, sourceId, refresh) {
        runCatching { loadCatalogueDetail(client, sourceType, sourceId) }
            .onSuccess { data = it; error = null }.onFailure { error = "Could not load details, or you no longer have access." }
    }
    FoodMindDetailScaffold(data?.title ?: "Details", onBack) { padding ->
        when {
            data == null && error == null -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            data == null -> Column(Modifier.padding(padding).padding(24.dp)) { Text(error.orEmpty(), color = FoodMindCoral); TextButton(onClick = { refresh++ }) { Text("Try again") } }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val detail = data!!
                item {
                    Text(detail.subtitle, color = FoodMindGreen, fontWeight = FontWeight.Bold)
                    Text(detail.title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = FoodMindInk, modifier = Modifier.padding(top = 7.dp))
                    detail.description?.let { Text(it, color = FoodMindMuted, modifier = Modifier.padding(top = 8.dp)) }
                    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { detail.tags.forEach { AssistChip(onClick = {}, label = { Text(it) }) } }
                }
                item { FoodMindSurfaceCard { Column { Text("Information FoodMind can reference", fontWeight = FontWeight.Bold, fontSize = 18.sp); detail.facts.forEach { (label, value) -> Row(Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(label, Modifier.weight(0.4f), color = FoodMindMuted); Text(value, Modifier.weight(0.6f), fontWeight = FontWeight.Medium) } } } } }
                if (detail.warnings.isNotEmpty()) item { FoodMindSurfaceCard { Column { Text("Dietary and allergen context", fontWeight = FontWeight.Bold); detail.warnings.forEach { Text("• $it", color = FoodMindCoral, modifier = Modifier.padding(top = 6.dp)) } } } }
                item {
                    Button(
                        onClick = {
                            val saveType = when (sourceType) { "CURATED_PLACE" -> "PLACE"; "CURATED_PRODUCT" -> "FOOD_PRODUCT"; "GROUP_RECORD" -> "FOOD_RECORD"; else -> sourceType }
                            scope.launch { runCatching { client.saveWantToTry(saveType, sourceId) }.onSuccess { saved = true }.onFailure { error = "Could not save." } }
                        },
                        enabled = !saved,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Icon(Icons.Outlined.BookmarkAdd, null); Text(if (saved) "Added to Want to Try" else "Add to Want to Try", modifier = Modifier.padding(start = 8.dp)) }
                    Text("This screen shows only fields returned by the backend; it does not add nutrition, safety, or inventory claims.", color = FoodMindMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

private suspend fun loadCatalogueDetail(client: FoodMindApiClient, type: String, id: String): CatalogueDetailData = when (type) {
    "PLACE", "CURATED_PLACE" -> client.place(id).let { place ->
        CatalogueDetailData(
            title = place.name,
            subtitle = listOf(place.placeType, place.area).filter(String::isNotBlank).joinToString(" · "),
            description = place.addressText,
            tags = place.offerings.take(5).map { it.mealType }.distinct(),
            facts = buildList {
                add("Area" to place.area)
                place.addressText?.let { add("Address" to it) }
                val amounts = place.offerings.mapNotNull { it.price.amount.takeIf { a -> a > 0.0 } }
                if (amounts.size > 1) add("Price range" to "${formatMoney(amounts.min(), place.offerings.first().price.currency)} – ${formatMoney(amounts.max(), place.offerings.first().price.currency)}")
                else if (amounts.size == 1) add("Price" to formatMoney(amounts.first(), place.offerings.first().price.currency))
                add("Visible meals" to "${place.offerings.size} items")
                add("Evidence observations" to "${place.observations.size} items")
            },
            warnings = place.observations.mapNotNull { it.note }.take(4),
        )
    }
    "FOOD_PRODUCT", "CURATED_PRODUCT" -> client.product(id).let { product ->
        CatalogueDetailData(
            title = product.name,
            subtitle = listOfNotNull(product.brand, product.place?.name).joinToString(" · "),
            description = product.description,
            tags = product.dietaryTagCodes,
            facts = buildList {
                product.price?.let { add("Reference price" to formatMoney(it.amount, it.currency)) }
                product.place?.let { add("Place" to "${it.name} · ${it.area}") }
            },
            warnings = product.allergenCodes,
        )
    }
    "MEAL" -> client.meal(id).let { meal ->
        CatalogueDetailData(
            title = meal.name,
            subtitle = listOfNotNull(meal.cuisine?.name, meal.mealType).filter(String::isNotBlank).joinToString(" · "),
            description = meal.description,
            tags = meal.dietaryTagCodes,
            facts = buildList {
                meal.defaultSpiceLevel?.let { add("Default spice level" to it.toString()) }
                meal.offerings.firstOrNull()?.let { add("Price examples" to formatMoney(it.price.amount, it.price.currency)) }
            },
            warnings = meal.allergenCodes,
        )
    }
    "FOOD_RECORD", "GROUP_RECORD" -> client.foodRecord(id).let { record ->
        CatalogueDetailData(
            title = record.mealNameSnapshot,
            subtitle = listOfNotNull(record.placeNameSnapshot, record.cuisineName).joinToString(" · "),
            description = record.comment,
            tags = listOf(record.visibility),
            facts = buildList {
                add("Occurred at" to formatFoodMindTimestamp(record.occurredAt))
                record.rating?.let { add("Rating" to it.toString()) }
                record.price?.let { add("Spending" to formatMoney(it.amount, it.currency)) }
                record.wouldEatAgain?.let { add("Would eat again" to if (it) "Yes" else "No") }
            },
        )
    }
    else -> error("Unsupported source type $type")
}
