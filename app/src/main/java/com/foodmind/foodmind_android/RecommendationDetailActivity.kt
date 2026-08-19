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
import androidx.compose.material3.AlertDialog
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
        setContent { FoodMindTheme { RecommendationDetailScreen(client, sessionId, ::finish, { startActivity(RecommendationDetailActivity.intent(this, it)); finish() }, { type, id -> startActivity(CatalogueDetailActivity.intent(this, type, id)) }, { startActivity(RecordEditorActivity.intent(this, foodRecordPrefillFrom(it, sessionId))) }) } }
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
    onRecord: (RecommendationCandidateResponse) -> Unit,
) {
    var response by remember { mutableStateOf<RecommendationResponse?>(null) }; var groups by remember { mutableStateOf<List<GroupResponse>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }; var notice by remember { mutableStateOf<String?>(null) }; var feedback by remember { mutableStateOf(setOf<String>()) }; var permanentlyHidden by remember { mutableStateOf(setOf<String>()) }; var pendingPermanentRejection by remember { mutableStateOf<RecommendationCandidateResponse?>(null) }; var saved by remember { mutableStateOf(setOf<String>()) }; var shared by remember { mutableStateOf(setOf<String>()) }; var busy by remember { mutableStateOf(false) }; var refresh by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sessionId, refresh) { runCatching { coroutineScope { val recommendation = async { client.recommendation(sessionId) }; val groupList = async { client.groups() }; recommendation.await() to groupList.await() } }.onSuccess { response = it.first; groups = it.second; error = null }.onFailure { error = "Could not load recommendation details." } }
    FoodMindDetailScaffold("Recommendation details", onBack) { padding ->
        val data = response
        when {
            data == null && error == null -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            data == null -> Column(Modifier.padding(padding).padding(24.dp)) { Text(error.orEmpty(), color = FoodMindCoral); TextButton(onClick = { refresh++ }) { Text("Try again") } }
            data.items.ifEmpty { data.candidates }.isEmpty() -> Column(Modifier.padding(padding).padding(24.dp)) { Text("No valid candidates right now", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text("Dietary and allergen constraints are still active. Adjust your options and try again.", color = FoodMindMuted) }
            else -> {
                val candidates = data.items.ifEmpty { data.candidates }.sortedBy { it.rank ?: Int.MAX_VALUE }.filterNot { it.candidateId in permanentlyHidden }
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { Text("Clear choices, honest reasons", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Text("${data.status} · ${data.fallbackStatus ?: "Standard path"}", color = FoodMindGreen); if (data.fallbackStatus?.contains("SUCCEEDED") == true) Text("The backend used a deterministic fallback because the smart path was unavailable.", color = FoodMindMuted, modifier = Modifier.padding(top = 5.dp)); notice?.let { Text(it, color = FoodMindGreen, modifier = Modifier.padding(top = 8.dp)) } }
                    itemsIndexed(candidates, key = { _, item -> item.candidateId.orEmpty() }) { index, candidate ->
                        CandidateCard(index, candidate, candidate.candidateId in feedback, candidate.mealId in saved, candidate.candidateId in shared,
                            onPlace = { candidate.placeId?.let { onCatalogue("PLACE", it) } },
                            onAccept = { candidate.candidateId?.let { id -> scope.launch { busy = true; runCatching { client.submitRecommendationFeedback(sessionId, RecommendationFeedbackRequest(id, "ACCEPTED")) }.onSuccess { feedback = feedback + id; onRecord(candidate) }.onFailure { error = "Could not submit feedback." }; busy = false } } },
                            onReject = { candidate.candidateId?.let { id -> scope.launch { runCatching { client.submitRecommendationFeedback(sessionId, RecommendationFeedbackRequest(id, "REJECTED", "NOT_IN_MOOD")) }.onSuccess { feedback = feedback + id } } } },
                            onPermanentReject = { pendingPermanentRejection = candidate },
                            onSave = { candidate.mealId?.let { id -> scope.launch { runCatching { client.saveWantToTry("MEAL", id) }.onSuccess { saved = saved + id }.onFailure { error = "Could not save." } } } },
                            onShare = { groupId -> candidate.candidateId?.let { id -> scope.launch { runCatching { client.shareRecommendation(groupId, id, "Recommendation from FoodMind") }.onSuccess { shared = shared + id }.onFailure { error = "Could not share the recommendation." } } } }, groups = groups,
                        )
                    }
                    item {
                        error?.let { Text(it, color = FoodMindCoral) }
                        OutlinedButton(onClick = { scope.launch { busy = true; runCatching { client.submitRecommendationFeedback(sessionId, RecommendationFeedbackRequest(eventType = "RERECOMMEND_REQUESTED")); client.generateRecommendation(GenerateRecommendationRequest(parentSessionId = sessionId)) }.onSuccess { it.sessionId?.let(onNewSession) }.onFailure { error = "Could not generate another set of recommendations right now." }; busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Refresh, null); Text("Try another set for the group") }
                    }
                }
            }
        }
    }
    pendingPermanentRejection?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingPermanentRejection = null },
            title = { Text("Never recommend this again?") },
            text = { Text("This meal at this place will be hidden from all future recommendations. This cannot be undone, and this saved recommendation session will remain in history.") },
            confirmButton = {
                Button(onClick = {
                    val candidateId = candidate.candidateId ?: return@Button
                    scope.launch {
                        busy = true
                        runCatching { client.submitRecommendationFeedback(sessionId, permanentRejectionRequest(candidateId)) }
                            .onSuccess {
                                permanentlyHidden = permanentlyHidden + candidateId
                                notice = "Hidden from future recommendations. This saved session remains unchanged."
                                pendingPermanentRejection = null
                            }
                            .onFailure { error = "Could not hide this candidate from future recommendations." }
                        busy = false
                    }
                }, enabled = !busy) { Text("Never recommend") }
            },
            dismissButton = { TextButton(onClick = { pendingPermanentRejection = null }, enabled = !busy) { Text("Cancel") } },
        )
    }
}

internal fun permanentRejectionRequest(candidateId: String) =
    RecommendationFeedbackRequest(candidateId, "REJECTED", "DO_NOT_RECOMMEND")

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
    onPermanentReject: () -> Unit,
    onSave: () -> Unit,
    onShare: (String) -> Unit,
    groups: List<GroupResponse>,
) {
    Card(colors = CardDefaults.cardColors(containerColor = FoodMindSurface), border = BorderStroke(1.dp, FoodMindLine)) {
        Column(Modifier.padding(17.dp)) {
            Text("#${candidate.rank ?: index + 1} · ${candidate.recommendationType?.replace('_', ' ') ?: "MATCH"}", color = FoodMindGreen, fontWeight = FontWeight.Bold)
            Text(candidate.mealName ?: "UntitledMeal", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
            Text(listOfNotNull(candidate.placeName, candidate.area, candidate.price?.let { "${it.amount} ${it.currency}" }).joinToString(" · "), color = FoodMindMuted)
            Text(candidate.explanation ?: candidate.reasons.firstOrNull() ?: "The backend did not return further details.", modifier = Modifier.padding(top = 10.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { candidate.reasonCodes.forEach { AssistChip(onClick = {}, label = { Text(it.replace('_', ' ')) }) } }
            TextButton(onClick = onPlace, enabled = candidate.placeId != null) { Icon(Icons.Outlined.LocationOn, null); Text("View place") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onAccept, enabled = !responded) { Text(if (responded) "Feedback sent" else "Choose this") }; OutlinedButton(onClick = onReject, enabled = !responded) { Text("Not right tonight") }; OutlinedButton(onClick = onPermanentReject, enabled = !responded) { Text("Never recommend") }; OutlinedButton(onClick = onSave, enabled = !saved && candidate.mealId != null) { Icon(Icons.Outlined.BookmarkAdd, null) } }
            if (groups.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { groups.take(4).forEach { group -> OutlinedButton(onClick = { group.id?.let(onShare) }, enabled = !shared) { Icon(Icons.Outlined.Share, null); Text(if (shared) "Shared" else group.name ?: "Groups") } } }
        }
    }
}
