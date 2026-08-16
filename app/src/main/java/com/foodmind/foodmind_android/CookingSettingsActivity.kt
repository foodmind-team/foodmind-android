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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.foodmind.foodmind_android.core.network.UpdateCookingRegionRequest
import kotlinx.coroutines.launch

/** Account-backed cooking guidance preferences shared with Web. */
class CookingSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = foodMindApiClient()
        setContent { FoodMindTheme { CookingSettingsScreen(client, ::finish) } }
    }
}

@Composable
private fun CookingSettingsScreen(client: FoodMindApiClient, onBack: () -> Unit) {
    var region by remember { mutableStateOf("SG") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val regions = listOf(
        "SG" to "Singapore",
        "US" to "United States",
        "CN" to "Mainland China",
    )
    LaunchedEffect(Unit) {
        runCatching { client.preferences() }
            .onSuccess { region = it.cookingRegion; error = null }
            .onFailure { error = "Could not load account cooking preferences." }
        loading = false
    }

    FoodMindDetailScaffold("Cook preferences", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("COOK PREFERENCES", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Cooking preferences", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Your cooking region is saved to your FoodMind account and shared with Web.", color = FoodMindMuted, modifier = Modifier.padding(top = 6.dp))
            }
            if (loading) item { CircularProgressIndicator() }
            if (!loading) item {
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
            error?.let { message -> item { Text(message, color = FoodMindCoral) } }
            item {
                Button(onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        runCatching { client.updateCookingRegion(UpdateCookingRegionRequest(region)) }
                            .onSuccess { updated -> region = updated.cookingRegion; saved = true }
                            .onFailure { error = "Could not sync cooking preferences. Please try again." }
                        saving = false
                    }
                }, enabled = !loading && !saving, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Check, null)
                    Text(if (saving) " Saving…" else if (saved) " Preferences synced" else " Save preferences")
                }
            }
        }
    }
}
