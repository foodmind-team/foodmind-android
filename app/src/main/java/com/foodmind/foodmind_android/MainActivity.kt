package com.foodmind.foodmind_android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.core.network.GenerateRecommendationRequest
import com.foodmind.foodmind_android.core.network.GroupResponse
import com.foodmind.foodmind_android.domain.repository.RecipeDraftStore
import com.foodmind.foodmind_android.domain.repository.RecommendationRepositoryImpl
import java.time.Instant
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()
    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        val bypassAuthForTest = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_BYPASS_AUTH_FOR_TEST, false)
        if (!bypassAuthForTest && FoodMindSession.tokenStore.accessToken().isNullOrBlank() && FoodMindSession.tokenStore.refreshToken().isNullOrBlank()) {
            authLauncher.launch(Intent(this, LoginActivity::class.java))
        }
        RecipeDraftStore.initialize(this, FoodMindSession.tokenStore.userId())
        val apiClient = foodMindApiClient()
        viewModel.setRecommendationRepository(RecommendationRepositoryImpl(apiClient::generateRecommendation))
        setContent {
            FoodMindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state, apiClient, viewModel::selectMode, viewModel::generateRecommendation,
                    onNavigate = ::openFoodMindRoot,
                    onRecord = { startActivity(RecordEditorActivity.intent(this, "FOOD", null)) },
                    onChat = { startActivity(Intent(this, ChatListActivity::class.java)) },
                    onCook = {
                        startActivity(
                            Intent(this, CookingHomeActivity::class.java)
                                .putExtra(EXTRA_BYPASS_AUTH_FOR_TEST, bypassAuthForTest),
                        )
                    },
                    onRecommendation = { startActivity(RecommendationDetailActivity.intent(this, it)) },
                )
            }
        }
    }

    companion object { const val EXTRA_BYPASS_AUTH_FOR_TEST = "bypass_auth_for_test" }
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    client: FoodMindApiClient,
    onModeChange: (HomeMode) -> Unit,
    onGenerate: (GenerateRecommendationRequest) -> Unit,
    onNavigate: (FoodMindRoot) -> Unit,
    onRecord: () -> Unit,
    onChat: () -> Unit,
    onCook: () -> Unit,
    onRecommendation: (String) -> Unit,
) {
    var groups by remember { mutableStateOf<List<GroupResponse>>(emptyList()) }
    var groupId by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf("DINNER") }
    var budget by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("SGD") }
    var mood by remember { mutableStateOf("") }
    var requestedFor by remember { mutableStateOf(Instant.now().plus(1, ChronoUnit.HOURS).toString()) }
    var maxDistance by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var contextError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val contextLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.let { data ->
            groupId = data.getStringExtra(RecommendationContextActivity.EXTRA_GROUP_ID).orEmpty()
            mealType = data.getStringExtra(RecommendationContextActivity.EXTRA_MEAL_TYPE) ?: mealType
            budget = data.getStringExtra(RecommendationContextActivity.EXTRA_BUDGET).orEmpty()
            currency = data.getStringExtra(RecommendationContextActivity.EXTRA_CURRENCY) ?: currency
            mood = data.getStringExtra(RecommendationContextActivity.EXTRA_MOOD).orEmpty()
            requestedFor = data.getStringExtra(RecommendationContextActivity.EXTRA_REQUESTED_FOR) ?: requestedFor
            maxDistance = data.getStringExtra(RecommendationContextActivity.EXTRA_MAX_DISTANCE).orEmpty()
            latitude = data.takeIf { it.hasExtra(RecommendationContextActivity.EXTRA_LATITUDE) }?.getDoubleExtra(RecommendationContextActivity.EXTRA_LATITUDE, 0.0)
            longitude = data.takeIf { it.hasExtra(RecommendationContextActivity.EXTRA_LONGITUDE) }?.getDoubleExtra(RecommendationContextActivity.EXTRA_LONGITUDE, 0.0)
        }
    }
    LaunchedEffect(Unit) {
        runCatching { client.groups() }.onSuccess { groups = it }
        runCatching { client.preferences() }.onSuccess { p ->
            budget = p.budgetMax?.toString().orEmpty(); currency = p.currency ?: "SGD"; mealType = p.preferredMealTypes.firstOrNull() ?: "DINNER"
            maxDistance = p.maxDistanceKm?.toString().orEmpty(); latitude = p.preferredLatitude; longitude = p.preferredLongitude
        }
    }
    val request = buildHomeRecommendationRequest(
        groupId = groupId,
        mealType = mealType,
        budget = budget,
        currency = currency,
        mood = mood,
        latitude = latitude,
        longitude = longitude,
        maxDistanceKm = maxDistance.toDoubleOrNull(),
        requestedFor = requestedFor,
    )
    LaunchedEffect(state.recommendation?.sessionId) {
        state.recommendation?.sessionId?.let(onRecommendation)
    }
    FoodMindRootScaffold(
        FoodMindRoot.HOME, "FoodMind", onNavigate,
        topActions = { IconButton(onClick = onChat) { Icon(Icons.Outlined.ChatBubbleOutline, "FoodMind Assistant") } },
        onRecord = onRecord,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(true, { onModeChange(HomeMode.RECOMMEND) }, label = { Text("Dining out & delivery") })
                    FilterChip(false, onCook, label = { Text("Cooking") })
                }
                Text("Decide dinner with confidence.", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, color = FoodMindInk, modifier = Modifier.padding(top = 12.dp))
                Text("One clear choice, with reasons you can inspect.", color = FoodMindMuted, modifier = Modifier.padding(top = 6.dp))
            }
            item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), border = BorderStroke(0.dp, Color.Transparent)) {
                        Column(Modifier.background(Brush.linearGradient(listOf(FoodMindGreenDark, FoodMindGreen))).padding(20.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("Tonight’s recommendation context", color = Color(0xFFCFE5D8), fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(groups.firstOrNull { it.id == groupId }?.name ?: "Recommend for me", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                                IconButton(onClick = {
                                    contextLauncher.launch(RecommendationContextActivity.intent(
                                        context, groupId, mealType, budget, currency, mood, requestedFor,
                                        maxDistance, latitude, longitude,
                                    ))
                                }) { Icon(Icons.Outlined.Tune, "Adjust recommendation context", tint = Color.White) }
                            }
                            Text(listOf(mealType, budget.takeIf(String::isNotBlank)?.let { "$it $currency" }, maxDistance.takeIf(String::isNotBlank)?.let { "Within $it km" }).filterNotNull().joinToString(" · "), color = Color.White, modifier = Modifier.padding(top = 8.dp))
                            Button(onClick = { if (currency.length == 3) onGenerate(request) else contextError = "Currency must use a 3-letter code." }, enabled = !state.isGenerating, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { if (state.isGenerating) CircularProgressIndicator() else Text("Generate recommendations") }
                        }
                    }
            }
            contextError?.let { item { Text(it, color = FoodMindCoral) } }
            state.errorMessage?.let { item { FoodMindSurfaceCard { Column { Text(it, color = FoodMindCoral); TextButton(onClick = { onGenerate(request) }) { Text("Try again") } } } } }
        }
    }
}

internal fun buildHomeRecommendationRequest(
    groupId: String,
    mealType: String,
    budget: String,
    currency: String,
    mood: String,
    latitude: Double?,
    longitude: Double?,
    maxDistanceKm: Double?,
    requestedFor: String,
): GenerateRecommendationRequest {
    val maxBudget = budget.toDoubleOrNull()
    return GenerateRecommendationRequest(
        groupId = groupId.ifBlank { null },
        mealType = mealType,
        maxBudget = maxBudget,
        currency = currency.takeIf { maxBudget != null },
        latitude = latitude,
        longitude = longitude,
        maxDistanceKm = maxDistanceKm,
        mood = mood.ifBlank { null },
        requestedFor = requestedFor,
    )
}
