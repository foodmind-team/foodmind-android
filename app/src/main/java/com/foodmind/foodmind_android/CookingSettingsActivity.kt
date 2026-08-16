package com.foodmind.foodmind_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Cook preferences — ported from cooking-app's SettingsScreen. Reference data
 * comes from the real /catalogue/reference-data endpoint; preferences shape the
 * demo scenarios described at the bottom.
 */
class CookingSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FoodMindTheme { CookingSettingsScreen(::finish) } }
    }
}

@Composable
private fun CookingSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val initial = remember { CookingPreferencesStore.load(context) }
    var region by remember { mutableStateOf(initial.region) }
    var saved by remember { mutableStateOf(false) }
    val regions = listOf(
        "SG" to "Singapore",
        "US" to "United States",
        "CN" to "Mainland China",
    )

    FoodMindDetailScaffold("Cook preferences", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("COOK PREFERENCES", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Cooking preferences", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Choose the region FoodMind should use for ingredient guidance and cooking conventions.", color = FoodMindMuted, modifier = Modifier.padding(top = 6.dp))
            }
            item {
                FoodMindSurfaceCard { Column {
                    Text("Region", fontWeight = FontWeight.Bold)
                    Text("Where are you cooking?", color = FoodMindMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        regions.forEach { (code, name) -> FilterChip(region == code, { region = code; saved = false }, label = { Text(name) }) }
                    }
                } }
            }
            item { FoodMindSurfaceCard { Column {
                Text("Dietary rules come from Preferences", fontWeight = FontWeight.Bold)
                Text("Account-level dietary requirements and allergens are always applied when a cooking plan is generated.", color = FoodMindMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            } } }
            item {
                Button(onClick = {
                    CookingPreferencesStore.save(context, CookingPreferences(region))
                    saved = true
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Check, null)
                    Text(if (saved) " Preferences saved" else " Save preferences")
                }
            }
        }
    }
}
