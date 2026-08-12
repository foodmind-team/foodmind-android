package com.foodmind.foodmind_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.CatalogueReferenceDataResponse
import com.foodmind.foodmind_android.core.network.FoodMindApiClient

/**
 * Cook preferences — ported from cooking-app's SettingsScreen. Reference data
 * comes from the real /catalogue/reference-data endpoint; preferences shape the
 * demo scenarios described at the bottom.
 */
class CookingSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = foodMindApiClient()
        setContent { FoodMindTheme { CookingSettingsScreen(client, ::finish) } }
    }
}

@Composable
private fun CookingSettingsScreen(client: FoodMindApiClient, onBack: () -> Unit) {
    var reference by remember { mutableStateOf<CatalogueReferenceDataResponse?>(null) }
    val context = LocalContext.current
    val initial = remember { CookingPreferencesStore.load(context) }
    var region by remember { mutableStateOf(initial.region) }
    var dietary by remember { mutableStateOf(initial.requiredDietaryTagCodes) }
    var allergens by remember { mutableStateOf(initial.avoidAllergenCodes) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { client.referenceData() }.onSuccess { reference = it } }
    val regions = listOf(
        "SG" to "Singapore",
        "US" to "United States",
        "CN" to "Mainland China",
    )
    val toggle = { current: Set<String>, value: String -> if (value in current) current - value else current + value }

    FoodMindDetailScaffold("Cook preferences", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("COOK PREFERENCES", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Plan preferences.", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Saved preferences are sent to the backend with every new Cooking Plan.", color = FoodMindMuted, modifier = Modifier.padding(top = 6.dp))
            }
            item {
                FoodMindSurfaceCard { Column {
                    Text("Region", fontWeight = FontWeight.Bold)
                    Text("Where are you cooking?", color = FoodMindMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        regions.forEach { (code, name) -> FilterChip(region == code, { region = code; saved = false }, label = { Text(name) }) }
                    }
                } }
            }
            item {
                FoodMindSurfaceCard { Column {
                    Text("Dietary requirements", fontWeight = FontWeight.Bold)
                    Text("Tags to honour when the backend can.", color = FoodMindMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    val tags = reference?.dietaryTags.orEmpty()
                    if (tags.isEmpty()) Text("Loading reference data…", color = FoodMindMuted)
                    else FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        tags.forEach { tag -> FilterChip(tag.code in dietary, { dietary = toggle(dietary, tag.code); saved = false }, label = { Text(tag.name) }) }
                    }
                } }
            }
            item {
                FoodMindSurfaceCard { Column {
                    Text("Allergens to avoid", fontWeight = FontWeight.Bold)
                    Text("Flags to pass along as constraints.", color = FoodMindMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    val tags = reference?.allergens.orEmpty()
                    if (tags.isEmpty()) Text("Loading reference data…", color = FoodMindMuted)
                    else FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        tags.forEach { tag -> FilterChip(tag.code in allergens, { allergens = toggle(allergens, tag.code); saved = false }, label = { Text(tag.name) }) }
                    }
                } }
            }
            item {
                Button(onClick = {
                    CookingPreferencesStore.save(context, CookingPreferences(region, dietary, allergens))
                    saved = true
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Check, null)
                    Text(if (saved) " Preferences saved" else " Save preferences")
                }
            }
            item {
                FoodMindSurfaceCard { Column {
                    Text("Demo scenarios", fontWeight = FontWeight.Bold)
                    ScenarioRow("Ready", "Select two quick dishes and generate with no time pressure.")
                    ScenarioRow("Needs confirmation", "Select a dish whose pantry line runs short; the backend asks how to proceed.")
                    ScenarioRow("Infeasible", "Select the slow soup and set a time limit below its cooking span.")
                    ScenarioRow("Failed", "Ask with constraints the backend cannot honour; it returns a retryable failure.")
                } }
            }
        }
    }
}

@Composable
private fun ScenarioRow(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(title, color = FoodMindGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(detail, color = FoodMindMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
