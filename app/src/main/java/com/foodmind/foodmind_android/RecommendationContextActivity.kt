package com.foodmind.foodmind_android

import android.app.Activity
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.GroupResponse
import com.foodmind.foodmind_android.core.network.CatalogueReferenceDataResponse

class RecommendationContextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoodMindTheme {
                RecommendationContextScreen(foodMindApiClient(), intent, ::finish) { result ->
                    setResult(Activity.RESULT_OK, result)
                    finish()
                }
            }
        }
    }

    companion object {
        const val EXTRA_GROUP_ID = "recommendation_group_id"
        const val EXTRA_MEAL_TYPE = "recommendation_meal_type"
        const val EXTRA_BUDGET = "recommendation_budget"
        const val EXTRA_CURRENCY = "recommendation_currency"
        const val EXTRA_AREA = "recommendation_area"
        const val EXTRA_MOOD = "recommendation_mood"
        const val EXTRA_REQUESTED_FOR = "recommendation_requested_for"
        const val EXTRA_MAX_DISTANCE = "recommendation_max_distance"
        const val EXTRA_LATITUDE = "recommendation_latitude"
        const val EXTRA_LONGITUDE = "recommendation_longitude"
        const val EXTRA_MAX_SPICE = "recommendation_max_spice"
        const val EXTRA_CLEANLINESS = "recommendation_cleanliness"
        const val EXTRA_DIETARY_TAGS = "recommendation_dietary_tags"
        const val EXTRA_ALLERGENS = "recommendation_allergens"

        fun intent(
            context: Context,
            groupId: String,
            mealType: String,
            budget: String,
            currency: String,
            area: String,
            mood: String,
            requestedFor: String,
            maxDistance: String,
            latitude: String,
            longitude: String,
            maxSpice: String,
            cleanliness: String,
            dietaryTagCodes: List<String>,
            allergenCodes: List<String>,
        ) = Intent(context, RecommendationContextActivity::class.java)
            .putExtra(EXTRA_GROUP_ID, groupId)
            .putExtra(EXTRA_MEAL_TYPE, mealType)
            .putExtra(EXTRA_BUDGET, budget)
            .putExtra(EXTRA_CURRENCY, currency)
            .putExtra(EXTRA_AREA, area)
            .putExtra(EXTRA_MOOD, mood)
            .putExtra(EXTRA_REQUESTED_FOR, requestedFor)
            .putExtra(EXTRA_MAX_DISTANCE, maxDistance)
            .putExtra(EXTRA_LATITUDE, latitude)
            .putExtra(EXTRA_LONGITUDE, longitude)
            .putExtra(EXTRA_MAX_SPICE, maxSpice)
            .putExtra(EXTRA_CLEANLINESS, cleanliness)
            .putStringArrayListExtra(EXTRA_DIETARY_TAGS, ArrayList(dietaryTagCodes))
            .putStringArrayListExtra(EXTRA_ALLERGENS, ArrayList(allergenCodes))
    }
}

@Composable
private fun RecommendationContextScreen(
    client: FoodMindApiClient,
    initial: Intent,
    onBack: () -> Unit,
    onApply: (Intent) -> Unit,
) {
    var groups by remember { mutableStateOf<List<GroupResponse>>(emptyList()) }
    var reference by remember { mutableStateOf<CatalogueReferenceDataResponse?>(null) }
    var groupId by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_GROUP_ID).orEmpty()) }
    var mealType by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_MEAL_TYPE) ?: "DINNER") }
    var budget by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_BUDGET).orEmpty()) }
    var currency by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_CURRENCY) ?: "SGD") }
    var area by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_AREA).orEmpty()) }
    var mood by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_MOOD).orEmpty()) }
    var requestedFor by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_REQUESTED_FOR).orEmpty()) }
    var maxDistance by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_MAX_DISTANCE).orEmpty()) }
    var latitude by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_LATITUDE).orEmpty()) }
    var longitude by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_LONGITUDE).orEmpty()) }
    var maxSpice by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_MAX_SPICE).orEmpty()) }
    var cleanliness by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_CLEANLINESS).orEmpty()) }
    var dietaryTagCodes by remember { mutableStateOf(initial.getStringArrayListExtra(RecommendationContextActivity.EXTRA_DIETARY_TAGS).orEmpty().toSet()) }
    var allergenCodes by remember { mutableStateOf(initial.getStringArrayListExtra(RecommendationContextActivity.EXTRA_ALLERGENS).orEmpty().toSet()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { client.groups() }.onSuccess { groups = it.filter { group -> group.status == "ACTIVE" } }
        runCatching { client.referenceData() }.onSuccess { reference = it }
    }

    FoodMindDetailScaffold("Recommendation context", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("TONIGHT'S RECOMMENDATION", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Shape your decision context", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Set what matters for this decision. These changes apply to the next recommendation only.", color = FoodMindMuted, modifier = Modifier.padding(top = 6.dp))
            }
            item { FoodMindSurfaceCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Who and when", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(groupId.isBlank(), { groupId = "" }, label = { Text("Just for me") })
                    groups.forEach { group -> FilterChip(groupId == group.id, { groupId = group.id.orEmpty() }, label = { Text(group.name ?: "Group") }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("BREAKFAST", "LUNCH", "DINNER").forEach { value -> FilterChip(mealType == value, { mealType = value }, label = { Text(value.lowercase().replaceFirstChar(Char::uppercase)) }) }
                }
                OutlinedTextField(requestedFor, { requestedFor = it }, label = { Text("Requested time (ISO 8601)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(mood, { mood = it }, label = { Text("Mood") }, placeholder = { Text("Comforting, quick, adventurous…") }, modifier = Modifier.fillMaxWidth())
            } } }
            item { FoodMindSurfaceCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Place and budget", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(budget, { budget = it }, label = { Text("Maximum budget") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(currency, { currency = it.uppercase().take(3) }, label = { Text("Currency") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(area, { area = it }, label = { Text("Area") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(maxDistance, { maxDistance = it }, label = { Text("Maximum distance (km)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(latitude, { latitude = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(longitude, { longitude = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f))
                }
            } } }
            item { FoodMindSurfaceCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Hard needs", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(maxSpice, { maxSpice = it.filter(Char::isDigit).take(1) }, label = { Text("Maximum spice (0–5)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(cleanliness, { cleanliness = it }, label = { Text("Cleanliness (0–1)") }, modifier = Modifier.weight(1f))
                }
                Text("Dietary requirements", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    reference?.dietaryTags.orEmpty().forEach { item ->
                        FilterChip(item.code in dietaryTagCodes, { dietaryTagCodes = if (item.code in dietaryTagCodes) dietaryTagCodes - item.code else dietaryTagCodes + item.code }, label = { Text(item.name) })
                    }
                }
                Text("Allergens to avoid", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    reference?.allergens.orEmpty().forEach { item ->
                        FilterChip(item.code in allergenCodes, { allergenCodes = if (item.code in allergenCodes) allergenCodes - item.code else allergenCodes + item.code }, label = { Text(item.name) })
                    }
                }
                Text("These overrides apply to the next recommendation only. Cooking always uses account Preferences.", color = FoodMindMuted, fontSize = 12.sp)
            } } }
            error?.let { item { Text(it, color = FoodMindCoral) } }
            item {
                Button(
                    onClick = {
                        if (budget.isNotBlank() && currency.length != 3) {
                            error = "Currency must use a 3-letter code."
                            return@Button
                        }
                        onApply(Intent()
                            .putExtra(RecommendationContextActivity.EXTRA_GROUP_ID, groupId)
                            .putExtra(RecommendationContextActivity.EXTRA_MEAL_TYPE, mealType)
                            .putExtra(RecommendationContextActivity.EXTRA_BUDGET, budget)
                            .putExtra(RecommendationContextActivity.EXTRA_CURRENCY, currency)
                            .putExtra(RecommendationContextActivity.EXTRA_AREA, area)
                            .putExtra(RecommendationContextActivity.EXTRA_MOOD, mood)
                            .putExtra(RecommendationContextActivity.EXTRA_REQUESTED_FOR, requestedFor)
                            .putExtra(RecommendationContextActivity.EXTRA_MAX_DISTANCE, maxDistance)
                            .putExtra(RecommendationContextActivity.EXTRA_LATITUDE, latitude)
                            .putExtra(RecommendationContextActivity.EXTRA_LONGITUDE, longitude)
                            .putExtra(RecommendationContextActivity.EXTRA_MAX_SPICE, maxSpice)
                            .putExtra(RecommendationContextActivity.EXTRA_CLEANLINESS, cleanliness)
                            .putStringArrayListExtra(RecommendationContextActivity.EXTRA_DIETARY_TAGS, ArrayList(dietaryTagCodes))
                            .putStringArrayListExtra(RecommendationContextActivity.EXTRA_ALLERGENS, ArrayList(allergenCodes)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Apply context") }
            }
        }
    }
}
