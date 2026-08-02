package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.foodmind.foodmind_android.core.network.GenerateRecommendationRequest
import com.foodmind.foodmind_android.core.network.GroupResponse
import com.foodmind.foodmind_android.core.network.RecommendationCandidateResponse
import com.foodmind.foodmind_android.core.network.RecommendationFeedbackRequest
import com.foodmind.foodmind_android.core.network.RecommendationResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class RecommendationDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty(); val client = foodMindApiClient()
        setContent { FoodMindTheme { RecommendationDetailScreen(client, sessionId, ::finish, { startActivity(RecommendationDetailActivity.intent(this, it)); finish() }, { type, id -> startActivity(CatalogueDetailActivity.intent(this, type, id)) }) } }
    }
    companion object { private const val EXTRA_SESSION_ID = "session_id"; fun intent(context: Context, sessionId: String) = Intent(context, RecommendationDetailActivity::class.java).putExtra(EXTRA_SESSION_ID, sessionId) }
}

@Composable
private fun RecommendationDetailScreen(
    client: FoodMindApiClient,
    sessionId: String,
    onBack: () -> Unit,
    onNewSession: (String) -> Unit,
    onCatalogue: (String, String) -> Unit,
) {
    var response by remember { mutableStateOf<RecommendationResponse?>(null) }; var groups by remember { mutableStateOf<List<GroupResponse>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }; var feedback by remember { mutableStateOf(setOf<String>()) }; var saved by remember { mutableStateOf(setOf<String>()) }; var shared by remember { mutableStateOf(setOf<String>()) }; var busy by remember { mutableStateOf(false) }; var refresh by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sessionId, refresh) { runCatching { coroutineScope { val recommendation = async { client.recommendation(sessionId) }; val groupList = async { client.groups() }; recommendation.await() to groupList.await() } }.onSuccess { response = it.first; groups = it.second; error = null }.onFailure { error = "推荐详情加载失败。" } }
    FoodMindDetailScaffold("推荐详情", onBack) { padding ->
        val data = response
        when {
            data == null && error == null -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            data == null -> Column(Modifier.padding(padding).padding(24.dp)) { Text(error.orEmpty(), color = FoodMindCoral); TextButton(onClick = { refresh++ }) { Text("重试") } }
            data.items.ifEmpty { data.candidates }.isEmpty() -> Column(Modifier.padding(padding).padding(24.dp)) { Text("暂时没有有效候选", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text("硬性饮食与过敏原约束仍保持生效。调整条件后再试。", color = FoodMindMuted) }
            else -> {
                val candidates = data.items.ifEmpty { data.candidates }.sortedBy { it.rank ?: Int.MAX_VALUE }
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { Text("清楚选择，诚实理由", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Text("${data.status} · ${data.fallbackStatus ?: "标准路径"}", color = FoodMindGreen); if (data.fallbackStatus?.contains("SUCCEEDED") == true) Text("智能路径不可用时，后端使用了确定性回退。", color = FoodMindMuted, modifier = Modifier.padding(top = 5.dp)) }
                    itemsIndexed(candidates, key = { _, item -> item.candidateId.orEmpty() }) { index, candidate ->
                        CandidateCard(index, candidate, candidate.candidateId in feedback, candidate.mealId in saved, candidate.candidateId in shared,
                            onPlace = { candidate.placeId?.let { onCatalogue("PLACE", it) } },
                            onAccept = { candidate.candidateId?.let { id -> scope.launch { busy = true; runCatching { client.submitRecommendationFeedback(sessionId, RecommendationFeedbackRequest(id, "ACCEPTED")) }.onSuccess { feedback = feedback + id }.onFailure { error = "反馈提交失败。" }; busy = false } } },
                            onReject = { candidate.candidateId?.let { id -> scope.launch { runCatching { client.submitRecommendationFeedback(sessionId, RecommendationFeedbackRequest(id, "REJECTED", "NOT_IN_MOOD")) }.onSuccess { feedback = feedback + id } } } },
                            onSave = { candidate.mealId?.let { id -> scope.launch { runCatching { client.saveWantToTry("MEAL", id) }.onSuccess { saved = saved + id }.onFailure { error = "收藏失败。" } } } },
                            onShare = { groupId -> candidate.candidateId?.let { id -> scope.launch { runCatching { client.shareRecommendation(groupId, id, "来自 FoodMind 的推荐") }.onSuccess { shared = shared + id }.onFailure { error = "分享失败。" } } } }, groups = groups,
                        )
                    }
                    item {
                        error?.let { Text(it, color = FoodMindCoral) }
                        OutlinedButton(onClick = { scope.launch { busy = true; runCatching { client.submitRecommendationFeedback(sessionId, RecommendationFeedbackRequest(eventType = "RERECOMMEND_REQUESTED")); client.generateRecommendation(GenerateRecommendationRequest(parentSessionId = sessionId)) }.onSuccess { it.sessionId?.let(onNewSession) }.onFailure { error = "暂时无法生成新一批推荐。" }; busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Refresh, null); Text("整组换一批") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    index: Int,
    candidate: RecommendationCandidateResponse,
    responded: Boolean,
    saved: Boolean,
    shared: Boolean,
    onPlace: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onSave: () -> Unit,
    onShare: (String) -> Unit,
    groups: List<GroupResponse>,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) {
        Column(Modifier.padding(17.dp)) {
            Text("#${candidate.rank ?: index + 1} · ${candidate.recommendationType?.replace('_', ' ') ?: "MATCH"}", color = FoodMindGreen, fontWeight = FontWeight.Bold)
            Text(candidate.mealName ?: "未命名餐食", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
            Text(listOfNotNull(candidate.placeName, candidate.area, candidate.price?.let { "${it.amount} ${it.currency}" }).joinToString(" · "), color = FoodMindMuted)
            Text(candidate.explanation ?: candidate.reasons.firstOrNull() ?: "后端没有返回更多说明。", modifier = Modifier.padding(top = 10.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { candidate.reasonCodes.forEach { AssistChip(onClick = {}, label = { Text(it.replace('_', ' ')) }) } }
            TextButton(onClick = onPlace, enabled = candidate.placeId != null) { Icon(Icons.Outlined.LocationOn, null); Text("查看地点") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onAccept, enabled = !responded) { Text(if (responded) "已反馈" else "选择这个") }; OutlinedButton(onClick = onReject, enabled = !responded) { Text("不合适") }; OutlinedButton(onClick = onSave, enabled = !saved && candidate.mealId != null) { Icon(Icons.Outlined.BookmarkAdd, null) } }
            if (groups.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { groups.take(4).forEach { group -> OutlinedButton(onClick = { group.id?.let(onShare) }, enabled = !shared) { Icon(Icons.Outlined.Share, null); Text(if (shared) "已分享" else group.name ?: "群组") } } }
        }
    }
}
