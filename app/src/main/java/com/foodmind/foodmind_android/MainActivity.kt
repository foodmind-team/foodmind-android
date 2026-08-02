package com.foodmind.foodmind_android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.core.network.GenerateRecommendationRequest
import com.foodmind.foodmind_android.core.network.GroupResponse
import com.foodmind.foodmind_android.core.network.RecommendationConstraintsRequest
import com.foodmind.foodmind_android.core.network.UserPreferencesResponse
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
        enableEdgeToEdge()
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
                    state, apiClient, viewModel::selectMode, viewModel::generateRecommendation, viewModel::tryAnother,
                    onNavigate = ::openFoodMindRoot,
                    onRecord = { startActivity(RecordEditorActivity.intent(this, "FOOD", null)) },
                    onChat = { startActivity(Intent(this, ChatListActivity::class.java)) },
                    onCook = { startActivity(Intent(this, RecipeLibraryActivity::class.java)) },
                    onManualCook = { startActivity(Intent(this, ManualCookingActivity::class.java)) },
                    onHistory = { startActivity(Intent(this, HistoryActivity::class.java)) },
                    onDashboard = { startActivity(Intent(this, DashboardActivity::class.java)) },
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
    onTryAnother: () -> Unit,
    onNavigate: (FoodMindRoot) -> Unit,
    onRecord: () -> Unit,
    onChat: () -> Unit,
    onCook: () -> Unit,
    onManualCook: () -> Unit,
    onHistory: () -> Unit,
    onDashboard: () -> Unit,
    onRecommendation: (String) -> Unit,
) {
    var groups by remember { mutableStateOf<List<GroupResponse>>(emptyList()) }
    var preferences by remember { mutableStateOf<UserPreferencesResponse?>(null) }
    var showContext by remember { mutableStateOf(false) }
    var groupId by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf("DINNER") }
    var budget by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("SGD") }
    var area by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("") }
    var contextError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { client.groups() }.onSuccess { groups = it }
        runCatching { client.preferences() }.onSuccess { p -> preferences = p; budget = p.budgetMax?.toString().orEmpty(); currency = p.currency ?: "SGD"; area = p.preferredArea.orEmpty(); mealType = p.preferredMealTypes.firstOrNull() ?: "DINNER" }
    }
    val request = GenerateRecommendationRequest(
        groupId = groupId.ifBlank { null }, mealType = mealType, maxBudget = budget.toDoubleOrNull(), currency = currency,
        area = area.ifBlank { null }, mood = mood.ifBlank { null }, requestedFor = Instant.now().plus(1, ChronoUnit.HOURS).toString(),
        maxDistanceKm = preferences?.maxDistanceKm,
        constraints = RecommendationConstraintsRequest(
            avoidAllergenCodes = preferences?.allergens?.map { it.code }, requiredDietaryTagCodes = preferences?.dietaryTagCodes,
            maxSpiceLevel = preferences?.spiceTolerance, minimumCleanlinessEvidenceScore = preferences?.minimumCleanlinessEvidenceScore,
        ),
    )
    FoodMindRootScaffold(
        FoodMindRoot.HOME, "FoodMind", onNavigate,
        topActions = { IconButton(onClick = onChat) { Icon(Icons.Outlined.ChatBubbleOutline, "FoodMind 助手") } },
        onRecord = onRecord,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.mode == HomeMode.RECOMMEND, { onModeChange(HomeMode.RECOMMEND) }, label = { Text("外食与外卖") })
                    FilterChip(state.mode == HomeMode.COOKING, { onModeChange(HomeMode.COOKING) }, label = { Text("烹饪") })
                }
                Text(if (state.mode == HomeMode.RECOMMEND) "晚餐，放心决定。" else "用手头的食材做饭。", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, color = FoodMindInk, modifier = Modifier.padding(top = 12.dp))
                Text(if (state.mode == HomeMode.RECOMMEND) "一个清晰选择，加上可以检查的理由。" else "从本地菜谱或手动食材，生成后端支持的可执行计划。", color = FoodMindMuted, modifier = Modifier.padding(top = 6.dp))
            }
            if (state.mode == HomeMode.RECOMMEND) {
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), border = BorderStroke(0.dp, Color.Transparent)) {
                        Column(Modifier.background(Brush.linearGradient(listOf(FoodMindGreenDark, FoodMindGreen))).padding(20.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("今晚的推荐情境", color = Color(0xFFCFE5D8), fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(groups.firstOrNull { it.id == groupId }?.name ?: "为我推荐", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                                IconButton(onClick = { showContext = !showContext }) { Icon(Icons.Outlined.Tune, "调整推荐情境", tint = Color.White) }
                            }
                            Text(listOf(mealType, budget.takeIf(String::isNotBlank)?.let { "$it $currency" }, area.takeIf(String::isNotBlank)).filterNotNull().joinToString(" · "), color = Color.White, modifier = Modifier.padding(top = 8.dp))
                            if (showContext) Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { FilterChip(groupId.isBlank(), { groupId = "" }, label = { Text("仅自己") }); groups.forEach { group -> FilterChip(groupId == group.id, { groupId = group.id.orEmpty() }, label = { Text(group.name ?: "群组") }) } }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(mealType, { mealType = it.uppercase() }, label = { Text("餐型") }, modifier = Modifier.weight(1f)); OutlinedTextField(budget, { budget = it }, label = { Text("预算") }, modifier = Modifier.weight(1f)) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(currency, { currency = it.uppercase().take(3) }, label = { Text("币种") }, modifier = Modifier.weight(1f)); OutlinedTextField(area, { area = it }, label = { Text("区域") }, modifier = Modifier.weight(1f)) }
                                OutlinedTextField(mood, { mood = it }, label = { Text("今晚的心情") }, modifier = Modifier.fillMaxWidth())
                            }
                            Button(onClick = { if (currency.length == 3) onGenerate(request) else contextError = "币种需要 3 位代码。" }, enabled = !state.isGenerating, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { if (state.isGenerating) CircularProgressIndicator() else Text("生成推荐") }
                        }
                    }
                }
                contextError?.let { item { Text(it, color = FoodMindCoral) } }
                if (state.hasResult) item {
                    Card(
                        onClick = { state.recommendation?.sessionId?.let(onRecommendation) },
                        shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("FoodMind 推荐", color = FoodMindGreen, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(state.resultTitle, Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold); Icon(Icons.Outlined.ChevronRight, null) }
                            Text(state.resultMeta, color = FoodMindMuted, modifier = Modifier.padding(top = 5.dp)); Text(state.resultReason, modifier = Modifier.padding(top = 10.dp))
                            TextButton(onClick = onTryAnother) { Text("整组换一批") }
                        }
                    }
                }
                state.errorMessage?.let { item { FoodMindSurfaceCard { Column { Text(it, color = FoodMindCoral); TextButton(onClick = { onGenerate(request) }) { Text("重试") } } } } }
            } else item {
                FoodMindSurfaceCard { Column { Text("选择烹饪起点", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("本地菜谱不会伪装成服务端数据。生成时只发送食材和约束。", color = FoodMindMuted, modifier = Modifier.padding(top = 6.dp)); Button(onClick = onCook, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("从本地菜谱开始") }; OutlinedButton(onClick = onManualCook, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("手动输入食材") } } }
            }
            item { Text("快捷入口", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { HomeQuick("历史记录", "最近吃过与喝过", Icons.Outlined.History, Modifier.weight(1f), onHistory); HomeQuick("饮食洞察", "看板与每周总结", Icons.Outlined.BarChart, Modifier.weight(1f), onDashboard) } }
            item { HomeQuick("FoodMind 助手", "带着已授权来源继续讨论", Icons.Outlined.ChatBubbleOutline, Modifier.fillMaxWidth(), onChat) }
        }
    }
}

@Composable
private fun HomeQuick(title: String, support: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) {
        Column(Modifier.padding(14.dp)) { Icon(icon, null, tint = FoodMindGreen); Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 9.dp)); Text(support, color = FoodMindMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
    }
}
