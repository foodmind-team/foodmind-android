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
import androidx.compose.material3.TextButton
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
        const val EXTRA_MOOD = "recommendation_mood"
        const val EXTRA_REQUESTED_FOR = "recommendation_requested_for"
        const val EXTRA_MAX_DISTANCE = "recommendation_max_distance"
        const val EXTRA_LATITUDE = "recommendation_latitude"
        const val EXTRA_LONGITUDE = "recommendation_longitude"

        fun intent(
            context: Context,
            groupId: String,
            mealType: String,
            budget: String,
            currency: String,
            mood: String,
            requestedFor: String,
            maxDistance: String,
            latitude: Double?,
            longitude: Double?,
        ) = Intent(context, RecommendationContextActivity::class.java).apply {
            putExtra(EXTRA_GROUP_ID, groupId)
            putExtra(EXTRA_MEAL_TYPE, mealType)
            putExtra(EXTRA_BUDGET, budget)
            putExtra(EXTRA_CURRENCY, currency)
            putExtra(EXTRA_MOOD, mood)
            putExtra(EXTRA_REQUESTED_FOR, requestedFor)
            putExtra(EXTRA_MAX_DISTANCE, maxDistance)
            if (latitude != null && longitude != null) {
                putExtra(EXTRA_LATITUDE, latitude)
                putExtra(EXTRA_LONGITUDE, longitude)
            }
        }
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
    var groupId by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_GROUP_ID).orEmpty()) }
    var mealType by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_MEAL_TYPE) ?: "DINNER") }
    var budget by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_BUDGET).orEmpty()) }
    var currency by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_CURRENCY) ?: "SGD") }
    var mood by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_MOOD).orEmpty()) }
    var requestedFor by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_REQUESTED_FOR).orEmpty()) }
    var maxDistance by remember { mutableStateOf(initial.getStringExtra(RecommendationContextActivity.EXTRA_MAX_DISTANCE).orEmpty()) }
    var latitude by remember { mutableStateOf(initial.takeIf { it.hasExtra(RecommendationContextActivity.EXTRA_LATITUDE) }?.getDoubleExtra(RecommendationContextActivity.EXTRA_LATITUDE, 0.0)) }
    var longitude by remember { mutableStateOf(initial.takeIf { it.hasExtra(RecommendationContextActivity.EXTRA_LONGITUDE) }?.getDoubleExtra(RecommendationContextActivity.EXTRA_LONGITUDE, 0.0)) }
    var locationMessage by remember {
        mutableStateOf(if (latitude != null && longitude != null) "Using your saved profile location. Update it here without changing your profile." else "No location selected. Distance filtering is off.")
    }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { client.groups() }.onSuccess { groups = it.filter { group -> group.status == "ACTIVE" } }
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
                Text("Budget", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(budget, { budget = it }, label = { Text("Maximum budget") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(currency, { currency = it.uppercase().take(3) }, label = { Text("Currency") }, modifier = Modifier.weight(1f))
                }
            } } }
            item { FoodMindSurfaceCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Starting point", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Use your current location for this recommendation only.", color = FoodMindMuted)
                UseCurrentLocationButton(
                    label = if (latitude != null && longitude != null) "Update current location" else "Use current location",
                    onLocation = { coordinates ->
                        latitude = coordinates.latitude
                        longitude = coordinates.longitude
                        locationMessage = "Using your current location for this recommendation only."
                        error = null
                    },
                    onError = { message -> error = message },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (latitude != null && longitude != null) TextButton(onClick = {
                    latitude = null
                    longitude = null
                    maxDistance = ""
                    locationMessage = "No location selected. Distance filtering is off."
                }) { Text("Use any distance") }
                Text(locationMessage, color = FoodMindMuted)
                OutlinedTextField(
                    maxDistance,
                    { maxDistance = it },
                    label = { Text("Maximum distance (km)") },
                    supportingText = { Text(if (latitude != null && longitude != null) "Limits results by distance from the selected location." else "Choose your current location before setting a distance.") },
                    enabled = latitude != null && longitude != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Dietary requirements, allergens, spice tolerance, and cleanliness settings come from your account preferences and remain enforced automatically.", color = FoodMindMuted, fontSize = 12.sp)
            } } }
            error?.let { item { Text(it, color = FoodMindCoral) } }
            item {
                Button(
                    onClick = {
                        if (budget.isNotBlank() && currency.length != 3) {
                            error = "Currency must use a 3-letter code."
                            return@Button
                        }
                        if (maxDistance.isNotBlank() && (latitude == null || longitude == null)) {
                            error = "Use your current location before setting a maximum distance."
                            return@Button
                        }
                        onApply(Intent().apply {
                            putExtra(RecommendationContextActivity.EXTRA_GROUP_ID, groupId)
                            putExtra(RecommendationContextActivity.EXTRA_MEAL_TYPE, mealType)
                            putExtra(RecommendationContextActivity.EXTRA_BUDGET, budget)
                            putExtra(RecommendationContextActivity.EXTRA_CURRENCY, currency)
                            putExtra(RecommendationContextActivity.EXTRA_MOOD, mood)
                            putExtra(RecommendationContextActivity.EXTRA_REQUESTED_FOR, requestedFor)
                            putExtra(RecommendationContextActivity.EXTRA_MAX_DISTANCE, maxDistance)
                            if (latitude != null && longitude != null) {
                                putExtra(RecommendationContextActivity.EXTRA_LATITUDE, latitude)
                                putExtra(RecommendationContextActivity.EXTRA_LONGITUDE, longitude)
                            }
                        })
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Apply context") }
            }
        }
    }
}
