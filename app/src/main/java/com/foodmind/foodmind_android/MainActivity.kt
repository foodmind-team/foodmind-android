package com.foodmind.foodmind_android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindNetwork
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.domain.repository.RecommendationRepositoryImpl
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FoodMindSession.initialize(this)
        val apiClient = FoodMindApiClient(
            FoodMindNetwork.createApi(BuildConfig.FOODMIND_API_BASE_URL, FoodMindSession.tokenStore),
            FoodMindSession.tokenStore,
        )
        viewModel.setRecommendationRepository(
            RecommendationRepositoryImpl(apiClient::generateRecommendation),
        )
        setContent {
            FoodMindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onModeChange = viewModel::selectMode,
                    onGenerate = {
                        if (state.mode == HomeMode.COOKING) {
                            startActivity(Intent(this, RecipeLibraryActivity::class.java))
                        } else viewModel.generateRecommendation()
                    },
                    onTryAnother = viewModel::tryAnother,
                    onNavigate = { destination -> navigate(destination) },
                )
            }
        }
    }

    private fun navigate(destination: HomeDestination) {
        when (destination) {
            HomeDestination.HOME -> Unit
            HomeDestination.GROUPS -> startActivity(SectionActivity.intent(this, SectionActivity.Section.GROUPS))
            HomeDestination.EXPLORE -> startActivity(SectionActivity.intent(this, SectionActivity.Section.EXPLORE))
            HomeDestination.SAVED -> startActivity(Intent(this, RecipeLibraryActivity::class.java))
            HomeDestination.ME -> startActivity(SectionActivity.intent(this, SectionActivity.Section.PROFILE))
            HomeDestination.HISTORY -> startActivity(Intent(this, HistoryActivity::class.java))
        }
    }
}

private enum class HomeDestination { HOME, GROUPS, EXPLORE, SAVED, ME, HISTORY }

@Composable
private fun HomeScreen(
    state: HomeUiState,
    onModeChange: (HomeMode) -> Unit,
    onGenerate: () -> Unit,
    onTryAnother: () -> Unit,
    onNavigate: (HomeDestination) -> Unit,
) {
    Scaffold(
        containerColor = FoodMindPaper,
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding(), containerColor = Color.White) {
                listOf(
                    HomeDestination.HOME to ("首页" to Icons.Outlined.Home),
                    HomeDestination.GROUPS to ("群组" to Icons.Outlined.Group),
                    HomeDestination.EXPLORE to ("发现" to Icons.Outlined.Explore),
                    HomeDestination.SAVED to ("收藏" to Icons.Outlined.BookmarkBorder),
                    HomeDestination.ME to ("我的" to Icons.Outlined.Person),
                ).forEach { (destination, labelAndIcon) ->
                    NavigationBarItem(
                        selected = destination == HomeDestination.HOME,
                        onClick = { onNavigate(destination) },
                        icon = { Icon(labelAndIcon.second, contentDescription = labelAndIcon.first) },
                        label = { Text(labelAndIcon.first) },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("FoodMind", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FoodMindGreenDark)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { onNavigate(HomeDestination.ME) }) {
                        Icon(Icons.Outlined.Person, contentDescription = "打开我的页面", tint = FoodMindGreen)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.mode == HomeMode.RECOMMEND,
                        onClick = { onModeChange(HomeMode.RECOMMEND) },
                        label = { Text("外食与外卖") },
                    )
                    FilterChip(
                        selected = state.mode == HomeMode.COOKING,
                        onClick = { onModeChange(HomeMode.COOKING) },
                        label = { Text("烹饪") },
                    )
                }
                Text(
                    if (state.mode == HomeMode.RECOMMEND) "晚餐，一起决定。" else "用手头的食材做饭。",
                    modifier = Modifier.padding(top = 12.dp),
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoodMindInk,
                )
                Text(
                    if (state.mode == HomeMode.RECOMMEND) "只给出一个有理由的推荐，而不是再列一张长清单。"
                    else "把当前食材、时间与偏好变成一个可执行的烹饪计划。",
                    modifier = Modifier.padding(top = 6.dp),
                    color = FoodMindMuted,
                )
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = FoodMindGreenDark),
                    border = BorderStroke(1.dp, FoodMindGreenDark),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            if (state.mode == HomeMode.RECOMMEND) "为你和群组生成一个推荐" else "从我的菜谱生成烹饪计划",
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (state.mode == HomeMode.RECOMMEND) "结合群组口味、距离、预算和今晚的约束。"
                            else "从已保存的菜谱开始，生成经资源和安全校验的时间线。",
                            modifier = Modifier.padding(top = 8.dp),
                            color = Color(0xFFDCEFE4),
                        )
                        Button(
                            onClick = onGenerate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp),
                        ) { Text(if (state.mode == HomeMode.RECOMMEND) "生成推荐" else "开始选择菜谱") }
                    }
                }
            }
            if (state.hasResult) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, FoodMindLine),
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("FoodMind 推荐", color = FoodMindGreen, fontWeight = FontWeight.Bold)
                            Text(
                                state.resultTitle,
                                modifier = Modifier.padding(top = 8.dp),
                                color = FoodMindInk,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(state.resultMeta, modifier = Modifier.padding(top = 6.dp), color = FoodMindMuted)
                            Text(state.resultReason, modifier = Modifier.padding(top = 12.dp), color = FoodMindInk)
                            OutlinedButton(onClick = onTryAnother, modifier = Modifier.padding(top = 14.dp)) {
                                Text("换一个")
                            }
                        }
                    }
                }
            }
            if (state.isGenerating) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("正在生成推荐…", modifier = Modifier.padding(top = 6.dp), color = FoodMindMuted)
                }
            }
            state.errorMessage?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F0))) {
                        Text(message, modifier = Modifier.padding(14.dp), color = Color(0xFFB42318))
                    }
                }
            }
            item {
                Text("快捷入口", fontWeight = FontWeight.Bold, color = FoodMindInk)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickCard("历史记录", "查看最近活动", Modifier.weight(1f), onClick = { onNavigate(HomeDestination.HISTORY) })
                    QuickCard("我的菜谱", "管理已保存菜谱", Modifier.weight(1f), onClick = { onNavigate(HomeDestination.SAVED) })
                }
            }
        }
    }
}

@Composable
private fun QuickCard(title: String, support: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, FoodMindLine),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = FoodMindInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(support, modifier = Modifier.padding(top = 4.dp), color = FoodMindMuted, fontSize = 11.sp)
        }
    }
}
