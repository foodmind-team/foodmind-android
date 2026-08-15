package com.foodmind.foodmind_android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.AllergenPreferenceRequest
import com.foodmind.foodmind_android.core.network.CatalogueReferenceDataResponse
import com.foodmind.foodmind_android.core.network.CurrentUserResponse
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.core.network.RecommendationSessionSummary
import com.foodmind.foodmind_android.core.network.ReplacePreferencesRequest
import com.foodmind.foodmind_android.core.network.UpdateCurrentUserRequest
import com.foodmind.foodmind_android.core.network.UserPreferencesResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = foodMindApiClient()
        setContent {
            FoodMindTheme {
                ProfileScreen(
                    client = client,
                    onNavigate = ::openFoodMindRoot,
                    onEdit = { startActivity(Intent(this, ProfileEditorActivity::class.java)) },
                    onPreferences = { startActivity(Intent(this, PreferencesActivity::class.java)) },
                    onHistory = { startActivity(Intent(this, HistoryActivity::class.java)) },
                    onDashboard = { startActivity(Intent(this, DashboardActivity::class.java)) },
                    onChat = { startActivity(Intent(this, ChatListActivity::class.java)) },
                    onRecords = { startActivity(Intent(this, RecordCollectionActivity::class.java)) },
                    onLogin = { startActivity(Intent(this, LoginActivity::class.java)) },
                    onRecommendation = { startActivity(RecommendationDetailActivity.intent(this, it)) },
                    onSignedOut = { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)) },
                )
            }
        }
    }
}

private data class ProfileData(
    val user: CurrentUserResponse,
    val preferences: UserPreferencesResponse,
    val decisions: List<RecommendationSessionSummary>,
)

@Composable
private fun ProfileScreen(
    client: FoodMindApiClient,
    onNavigate: (FoodMindRoot) -> Unit,
    onEdit: () -> Unit,
    onPreferences: () -> Unit,
    onHistory: () -> Unit,
    onDashboard: () -> Unit,
    onChat: () -> Unit,
    onRecords: () -> Unit,
    onLogin: () -> Unit,
    onRecommendation: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
    var data by remember { mutableStateOf<ProfileData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(refresh) {
        loading = true
        runCatching {
            coroutineScope {
                val user = async { client.currentUser() }
                val preferences = async { client.preferences() }
                val history = async { client.recommendationHistory().items }
                ProfileData(user.await(), preferences.await(), history.await())
            }
        }.onSuccess { data = it; error = null }
            .onFailure { error = "Sign in to manage your profile and preferences." }
        loading = false
    }
    FoodMindRootScaffold(
        selected = FoodMindRoot.ME,
        title = "Me",
        onNavigate = onNavigate,
        topActions = { if (data != null) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit profile") } },
    ) { padding ->
        when {
            loading -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            data == null -> Column(Modifier.padding(padding).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                FoodMindAvatar("F")
                Text("Help FoodMind get to know you", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 18.dp))
                Text(error.orEmpty(), color = FoodMindMuted, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = onLogin, modifier = Modifier.padding(top = 20.dp)) { Text("Sign in or register") }
                TextButton(onClick = { refresh++ }) { Text("I have signed in — reload") }
            }
            else -> {
                val profile = data!!
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Column(Modifier.fillMaxWidth().background(FoodMindGreenDark).padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FoodMindAvatar(profile.user.displayName ?: "F")
                                Column(Modifier.padding(start = 16.dp)) {
                                    Text(profile.user.displayName ?: "FoodMind user", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                                    Text(profile.user.email.orEmpty(), color = Color(0xFFCFE5D8))
                                    Text(profile.user.timeZone ?: "Time zone not set", color = Color(0xFF9EC4AE), fontSize = 12.sp)
                                }
                            }
                            Text(profile.preferences.foodGoal ?: "Make food decisions that suit you with less uncertainty.", color = Color.White, modifier = Modifier.padding(top = 22.dp), fontWeight = FontWeight.Medium)
                            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                profile.preferences.dietaryTagCodes.take(3).forEach { FilterChip(selected = true, onClick = onPreferences, label = { Text(it) }) }
                            }
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ProfileAction("Preferences", "Food preferences, budget, and distance", Icons.Outlined.Settings, Modifier.weight(1f), onPreferences)
                                ProfileAction("Insights", "Dashboard & weekly recap", Icons.Outlined.BarChart, Modifier.weight(1f), onDashboard)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ProfileAction("History", "Meals and drinks", Icons.Outlined.History, Modifier.weight(1f), onHistory)
                                ProfileAction("Assistant", "Continue your FoodMind conversation", Icons.Outlined.ChatBubbleOutline, Modifier.weight(1f), onChat)
                            }
                            ProfileAction("Record management", "Create, view, edit, or delete meal and drink records", Icons.Outlined.Restaurant, Modifier.fillMaxWidth(), onRecords)
                        }
                    }
                    item { Text("Recent decisions", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                    if (profile.decisions.isEmpty()) item { Text("No recommendation history yet.", Modifier.padding(20.dp), color = FoodMindMuted) }
                    items(profile.decisions.take(5), key = { it.sessionId.orEmpty() }) { item ->
                        Card(
                            onClick = { item.sessionId?.let(onRecommendation) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                            colors = CardDefaults.cardColors(containerColor = FoodMindSurface),
                            border = BorderStroke(1.dp, FoodMindLine),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${item.returnedCandidateCount} candidates", fontWeight = FontWeight.Bold)
                                    Text("${item.status ?: "Unknown"} · ${formatFoodMindTimestamp(item.createdAt)}", color = FoodMindMuted, fontSize = 12.sp)
                                }
                                Icon(Icons.Outlined.ChevronRight, null)
                            }
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { scope.launch { runCatching { client.logout() }; onSignedOut() } },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Icon(Icons.AutoMirrored.Outlined.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sign out of this device") }
                            TextButton(
                                onClick = { scope.launch { runCatching { client.logoutAll() }; onSignedOut() } },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Sign out of all devices") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileAction(title: String, support: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = FoodMindSurface), border = BorderStroke(1.dp, FoodMindLine)) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = FoodMindGreen)
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            Text(support, color = FoodMindMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

class ProfileEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = foodMindApiClient()
        setContent { FoodMindTheme { ProfileEditorScreen(client, ::finish) } }
    }
}

@Composable
private fun ProfileEditorScreen(client: FoodMindApiClient, onBack: () -> Unit) {
    var displayName by remember { mutableStateOf("") }
    var timeZone by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { client.currentUser() }.onSuccess { displayName = it.displayName.orEmpty(); timeZone = it.timeZone.orEmpty() }
            .onFailure { error = "Could not load profile." }
        loading = false
    }
    FoodMindDetailScaffold("Edit profile", onBack) { padding ->
        Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (loading) CircularProgressIndicator() else {
                OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(timeZone, { timeZone = it }, label = { Text("Time zone, for example Asia/Singapore") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                error?.let { Text(it, color = FoodMindCoral) }
                Button(
                    onClick = { scope.launch { saving = true; runCatching { client.updateCurrentUser(UpdateCurrentUserRequest(displayName.trim(), timeZone.trim())) }.onSuccess { onBack() }.onFailure { error = "Could not save. Please try again." }; saving = false } },
                    enabled = displayName.trim().isNotEmpty() && !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (saving) "Saving…" else "Save") }
            }
        }
    }
}

class PreferencesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = foodMindApiClient()
        setContent { FoodMindTheme { PreferencesScreen(client, ::finish) } }
    }
}

@Composable
private fun PreferencesScreen(client: FoodMindApiClient, onBack: () -> Unit) {
    var value by remember { mutableStateOf<UserPreferencesResponse?>(null) }
    var reference by remember { mutableStateOf(CatalogueReferenceDataResponse()) }
    var budgetMin by remember { mutableStateOf("") }
    var budgetMax by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("SGD") }
    var area by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var spice by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var cleanlinessPriority by remember { mutableStateOf("") }
    var cleanlinessScore by remember { mutableStateOf("") }
    var sweetness by remember { mutableStateOf("") }
    var ice by remember { mutableStateOf("") }
    var dietary by remember { mutableStateOf(setOf<String>()) }
    var allergens by remember { mutableStateOf(setOf<String>()) }
    var allergenSeverity by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var likedCuisines by remember { mutableStateOf(setOf<String>()) }
    var dislikedCuisines by remember { mutableStateOf(setOf<String>()) }
    var meals by remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { coroutineScope { val preferences = async { client.preferences() }; val references = async { client.referenceData() }; preferences.await() to references.await() } }
            .onSuccess { (p, r) ->
                value = p; reference = r; budgetMin = p.budgetMin?.toString().orEmpty(); budgetMax = p.budgetMax?.toString().orEmpty()
                currency = p.currency ?: "SGD"; area = p.preferredArea.orEmpty(); distance = p.maxDistanceKm?.toString().orEmpty()
                goal = p.foodGoal.orEmpty(); spice = p.spiceTolerance?.toString().orEmpty(); latitude = p.preferredLatitude?.toString().orEmpty(); longitude = p.preferredLongitude?.toString().orEmpty()
                cleanlinessPriority = p.cleanlinessPriority?.toString().orEmpty(); cleanlinessScore = p.minimumCleanlinessEvidenceScore?.toString().orEmpty(); sweetness = p.drinkSweetnessPreference.orEmpty(); ice = p.drinkIcePreference.orEmpty(); dietary = p.dietaryTagCodes.toSet()
                allergens = p.allergens.map { it.code }.toSet(); allergenSeverity = p.allergens.associate { it.code to it.severity }; likedCuisines = p.likedCuisineCodes.toSet(); dislikedCuisines = p.dislikedCuisineCodes.toSet(); meals = p.preferredMealTypes.toSet()
            }.onFailure { error = "Could not load preferences." }
    }
    FoodMindDetailScaffold("Preferences", onBack) { padding ->
        if (value == null && error == null) CircularProgressIndicator(Modifier.padding(padding).padding(24.dp)) else LazyColumn(
            Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text("Everyday context", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold); Text("These settings are sent as backend recommendation constraints.", color = FoodMindMuted) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(budgetMin, { budgetMin = it }, label = { Text("Minimum budget") }, modifier = Modifier.weight(1f))
                OutlinedTextField(budgetMax, { budgetMax = it }, label = { Text("Maximum budget") }, modifier = Modifier.weight(1f))
            } }
            item { OutlinedTextField(currency, { currency = it.uppercase().take(3) }, label = { Text("Currency") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(area, { area = it }, label = { Text("Usual area") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(distance, { distance = it }, label = { Text("Maximum distance (km)") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(spice, { spice = it.filter(Char::isDigit) }, label = { Text("Spice tolerance") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(goal, { goal = it }, label = { Text("Food goals") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
            item { PreferenceChips("Preferred cuisines", reference.cuisines.map { it.code to it.name }, likedCuisines) { likedCuisines = it } }
            item { PreferenceChips("Disliked cuisines", reference.cuisines.map { it.code to it.name }, dislikedCuisines) { dislikedCuisines = it } }
            item { PreferenceChips("Meal type", reference.mealTypes.map { it to it.replace('_', ' ') }, meals) { meals = it } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(sweetness, { sweetness = it.uppercase() }, label = { Text("Drink sweetness") }, modifier = Modifier.weight(1f)); OutlinedTextField(ice, { ice = it.uppercase() }, label = { Text("Drink ice level") }, modifier = Modifier.weight(1f)) } }
            item { PreferenceChips("Dietary requirements", reference.dietaryTags.map { it.code to it.name }, dietary) { dietary = it } }
            item { PreferenceChips("Allergens to avoid", reference.allergens.map { it.code to it.name }, allergens) { allergens = it } }
            if (allergens.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Allergen severity", fontWeight = FontWeight.Bold)
                    allergens.forEach { code ->
                        Column { Text(reference.allergens.firstOrNull { it.code == code }?.name ?: code, color = FoodMindMuted); FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("MILD", "MODERATE", "SEVERE").forEach { severity -> FilterChip((allergenSeverity[code] ?: "MODERATE") == severity, { allergenSeverity = allergenSeverity + (code to severity) }, label = { Text(severity) }) }
                        } }
                    }
                }
            }
            item { Text("Cleanliness evidence & location", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text("These are decision-support signals and do not mean FoodMind has inspected or certified a kitchen.", color = FoodMindMuted) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(cleanlinessPriority, { cleanlinessPriority = it.filter(Char::isDigit) }, label = { Text("Evidence priority 0–5") }, modifier = Modifier.weight(1f)); OutlinedTextField(cleanlinessScore, { cleanlinessScore = it }, label = { Text("Minimum evidence score") }, modifier = Modifier.weight(1f)) } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(latitude, { latitude = it }, label = { Text("Usual latitude") }, modifier = Modifier.weight(1f)); OutlinedTextField(longitude, { longitude = it }, label = { Text("Usual longitude") }, modifier = Modifier.weight(1f)) } }
            item { error?.let { Text(it, color = FoodMindCoral) }; if (saved) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6F0)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Preferences saved to FoodMind", color = FoodMindGreen, fontWeight = FontWeight.ExtraBold); Text("Your Android update is now available to Web and future recommendations. Open Web Preferences and select Refresh from FoodMind to verify it live.", color = FoodMindMuted) } }; Button(
                onClick = { scope.launch {
                    saving = true; saved = false
                    val request = ReplacePreferencesRequest(
                        budgetMin = budgetMin.toDoubleOrNull(), budgetMax = budgetMax.toDoubleOrNull(), currency = currency,
                        spiceTolerance = spice.toIntOrNull(), preferredArea = area.trim().ifBlank { null }, maxDistanceKm = distance.toDoubleOrNull(),
                        cleanlinessPriority = cleanlinessPriority.toIntOrNull(), minimumCleanlinessEvidenceScore = cleanlinessScore.toDoubleOrNull(),
                        foodGoal = goal.trim().ifBlank { null }, drinkSweetnessPreference = sweetness.trim().ifBlank { null },
                        drinkIcePreference = ice.trim().ifBlank { null }, likedCuisineCodes = likedCuisines.toList(),
                        dislikedCuisineCodes = dislikedCuisines.toList(), dietaryTagCodes = dietary.toList(),
                        allergens = allergens.map { AllergenPreferenceRequest(it, allergenSeverity[it] ?: "MODERATE") }, preferredMealTypes = meals.toList(),
                        preferredLatitude = latitude.toDoubleOrNull(), preferredLongitude = longitude.toDoubleOrNull(),
                    )
                    runCatching { client.replacePreferences(request) }.onSuccess { updated -> value = updated; error = null; saved = true }.onFailure { error = "Could not save. Check your input." }
                    saving = false
                } }, enabled = !saving, modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Saving…" else if (saved) "Saved to FoodMind" else "Save preferences") }; if (saved) TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") } }
        }
    }
}

@Composable
private fun PreferenceChips(title: String, options: List<Pair<String, String>>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Column {
        Text(title, fontWeight = FontWeight.Bold)
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (code, label) -> FilterChip(selected = code in selected, onClick = { onChange(if (code in selected) selected - code else selected + code) }, label = { Text(label) }) }
        }
    }
}
