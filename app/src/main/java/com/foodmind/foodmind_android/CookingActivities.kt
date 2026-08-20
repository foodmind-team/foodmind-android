package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.CookingDecisionResponse
import com.foodmind.foodmind_android.core.network.CookingConfirmationQuestionResponse
import com.foodmind.foodmind_android.core.network.CookingIngredientRequest
import com.foodmind.foodmind_android.core.network.CookingPlanResponse
import com.foodmind.foodmind_android.core.network.CookingPlanExecutionResponse
import com.foodmind.foodmind_android.core.network.CookingPlanSourceResponse
import com.foodmind.foodmind_android.core.network.CookingRepairOptionResponse
import com.foodmind.foodmind_android.core.network.CookingPlanSummary
import com.foodmind.foodmind_android.core.network.CookingPlanTaskResponse
import com.foodmind.foodmind_android.core.network.CookingTimelineTaskResponse
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.GenerateCookingPlanRequest
import com.foodmind.foodmind_android.core.network.QuestionAnswerRequest
import com.foodmind.foodmind_android.formatFoodMindTimestamp
import com.foodmind.foodmind_android.domain.repository.AsyncSubmitResult
import com.foodmind.foodmind_android.domain.repository.CookingPlanTaskRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Locale

class ManualCookingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = foodMindApiClient()
        setContent { FoodMindTheme { ManualCookingScreen(client, ::finish) { startActivity(CookingPlanDetailActivity.intent(this, it)) } } }
    }
}

@Composable
private fun ManualCookingScreen(client: FoodMindApiClient, onBack: () -> Unit, onOpenPlan: (String) -> Unit) {
    var ingredients by remember { mutableStateOf("") }; var servings by remember { mutableStateOf("2") }; var maxMinutes by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }; var currency by remember { mutableStateOf("SGD") }; var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }; var history by remember { mutableStateOf<List<CookingPlanSummary>>(emptyList()) }
    var asyncRunning by remember { mutableStateOf(false) }; var asyncPlanId by remember { mutableStateOf<String?>(null) }
    var taskProgress by remember { mutableStateOf<CookingPlanTaskResponse?>(null) }; var asyncError by remember { mutableStateOf<String?>(null) }
    var cancelling by remember { mutableStateOf(false) }; var asyncToken by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val taskRepo = remember(client) {
        CookingPlanTaskRepository(
            submitAsync = { client.generateCookingPlanAsync(it) },
            getTask = { client.cookingPlanTask(it) },
            readPlan = { client.cookingPlan(it) },
            cancelTask = { client.cancelCookingPlanTask(it) },
        )
    }
    LaunchedEffect(Unit) { runCatching { client.cookingPlanHistory().items }.onSuccess { history = it } }
    LaunchedEffect(asyncToken) {
        if (asyncToken == 0) return@LaunchedEffect
        asyncRunning = true; asyncError = null; taskProgress = null; asyncPlanId = null
        val lines = ingredients.lines().map(String::trim).filter(String::isNotBlank)
        taskRepo.generateAsync(GenerateCookingPlanRequest(
            ingredients = lines.map { CookingIngredientRequest(it, source = "MANUAL") },
            servings = servings.toIntOrNull() ?: 2,
            maxMinutes = maxMinutes.toIntOrNull(),
            maxBudget = budget.toDoubleOrNull(),
            currency = currency.takeIf { budget.isNotBlank() },
        )).onSuccess { accepted ->
            val planId = accepted.planId
            if (accepted is AsyncSubmitResult.Accepted && planId != null) {
                asyncPlanId = planId
                taskRepo.pollUntilTerminal(planId, onProgress = { taskProgress = it })
                    .onSuccess { plan ->
                        when {
                            plan.status == "FAILED" || plan.status == "NO_VALID_RECIPE" ->
                                asyncError = "后台生成失败（${plan.errorCode ?: plan.status}）。请调整约束后重试。"
                            else -> plan.planId?.let(onOpenPlan) ?: run { asyncError = "后台生成完成，但未返回计划 ID。" }
                        }
                    }
                    .onFailure { asyncError = "后台生成未能在超时前完成，请稍后在计划详情中查看。" }
            } else if (accepted is AsyncSubmitResult.Terminal && accepted.status == "READY" && planId != null) {
                onOpenPlan(planId)
            } else {
                val failedStatus = (accepted as? AsyncSubmitResult.Terminal)?.status ?: "未知"
                asyncError = "后台提交失败（$failedStatus），请改用同步生成。"
            }
        }.onFailure { asyncError = "无法提交后台生成，请检查网络后重试。" }
        asyncRunning = false
    }
    FoodMindDetailScaffold("Manual ingredients", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Cook with ingredients you know you have", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Text("Enter one ingredient per line, up to 30 lines. FoodMind does not infer your inventory.", color = FoodMindMuted) }
            item { OutlinedTextField(ingredients, { ingredients = it }, label = { Text("Ingredients, one per line") }, minLines = 7, modifier = Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(servings, { servings = it.filter(Char::isDigit) }, label = { Text("Servings") }, modifier = Modifier.weight(1f)); OutlinedTextField(maxMinutes, { maxMinutes = it.filter(Char::isDigit) }, label = { Text("Maximum minutes") }, modifier = Modifier.weight(1f)) } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(budget, { budget = it }, label = { Text("Extra budget") }, modifier = Modifier.weight(1f)); OutlinedTextField(currency, { currency = it.uppercase().take(3) }, label = { Text("Currency") }, modifier = Modifier.weight(1f)) } }
            item {
                error?.let { Text(it, color = FoodMindCoral) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch {
                        val lines = ingredients.lines().map(String::trim).filter(String::isNotBlank)
                        if (lines.isEmpty() || lines.size > 30) { error = "Enter 1–30 ingredients."; return@launch }
                        generating = true; error = null
                        runCatching { client.generateCookingPlan(GenerateCookingPlanRequest(
                            ingredients = lines.map { CookingIngredientRequest(it, source = "MANUAL") },
                            servings = servings.toIntOrNull() ?: 2,
                            maxMinutes = maxMinutes.toIntOrNull(),
                            maxBudget = budget.toDoubleOrNull(),
                            currency = currency.takeIf { budget.isNotBlank() },
                        )) }
                            .onSuccess { plan -> plan.planId?.let(onOpenPlan) ?: run { error = "The backend did not return a plan ID." } }.onFailure { error = "Could not generate a cooking plan. Check your constraints and try again." }
                        generating = false
                    } }, enabled = !generating, modifier = Modifier.fillMaxWidth()) { Text(if (generating) "Generating…" else "Generate cooking plan") }
                    OutlinedButton(onClick = {
                        val lines = ingredients.lines().map(String::trim).filter(String::isNotBlank)
                        if (lines.isEmpty() || lines.size > 30) { error = "Enter 1–30 ingredients."; return@OutlinedButton }
                        error = null; asyncToken++
                    }, enabled = !generating && !asyncRunning, modifier = Modifier.fillMaxWidth()) { Text(if (asyncRunning) "Generating in background…" else "Generate in background") }
                }
            }
            if (asyncRunning || taskProgress != null || asyncError != null) item {
                FoodMindSurfaceCard {
                    Column {
                        Text("Background generation", fontWeight = FontWeight.Bold)
                        taskProgress?.let { task ->
                            task.progress?.let { progress ->
                                Text(progress.node?.replace('_', ' ') ?: "Working", color = FoodMindGreen, fontWeight = FontWeight.Bold)
                                Text("${progress.completedSteps} steps completed", color = FoodMindMuted)
                                progress.message?.let { Text(it, color = FoodMindMuted, fontSize = 12.sp) }
                            } ?: Text("Submitted — waiting for the first progress update…", color = FoodMindMuted)
                        } ?: Text("Submitting…", color = FoodMindMuted)
                        asyncPlanId?.let {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    cancelling = true
                                    taskRepo.cancel(it)
                                        .onSuccess { asyncError = "已取消后台生成。"; taskProgress = null; asyncPlanId = null }
                                        .onFailure { asyncError = "取消失败：任务可能已完成（409），请刷新查看结果。" }
                                    cancelling = false
                                }
                            }, enabled = !cancelling, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(if (cancelling) "Cancelling…" else "Cancel") }
                        }
                        asyncError?.let { Text(it, color = FoodMindCoral, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
            }
            item { Text("Recent plans", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp)) }
            if (history.isEmpty()) item { Text("No plans yet.", color = FoodMindMuted) }
            items(history, key = { it.planId.orEmpty() }) { plan -> Card(onClick = { plan.planId?.let(onOpenPlan) }, colors = CardDefaults.cardColors(containerColor = FoodMindSurface), border = BorderStroke(1.dp, FoodMindLine)) { Column(Modifier.fillMaxWidth().padding(14.dp)) { Text("${plan.sourceCount} sources · ${plan.taskCount} tasks${plan.makespanMinutes?.let { " · $it min" } ?: ""}", fontWeight = FontWeight.Bold); Text("${plan.status} · ${formatFoodMindTimestamp(plan.createdAt)}", color = FoodMindMuted, fontSize = 12.sp) } } }
        }
    }
}

class CookingPlanDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_PLAN_ID).orEmpty(); val client = foodMindApiClient()
        val onHome = {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
        setContent {
            FoodMindTheme {
                CookingPlanDetailScreen(client, id, ::finish, onHome) { recipeIds ->
                    startActivity(
                        Intent(this, CookingHomeActivity::class.java).putStringArrayListExtra(
                            CookingHomeActivity.EXTRA_SELECTED_RECIPE_IDS,
                            ArrayList(recipeIds),
                        ),
                    )
                }
            }
        }
    }
    companion object { private const val EXTRA_PLAN_ID = "plan_id"; fun intent(context: Context, planId: String) = Intent(context, CookingPlanDetailActivity::class.java).putExtra(EXTRA_PLAN_ID, planId) }
}

@Composable
private fun CookingPlanDetailScreen(
    client: FoodMindApiClient,
    planId: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onCookAgain: (List<String>) -> Unit,
) {
    var plan by remember { mutableStateOf<CookingPlanResponse?>(null) }
    var execution by remember { mutableStateOf<CookingPlanExecutionResponse?>(null) }
    var taskProgress by remember { mutableStateOf<CookingPlanTaskResponse?>(null) }
    var cancelling by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(planId, refresh) {
        runCatching { client.cookingPlan(planId) }
            .onSuccess {
                plan = it
                error = null
                if (it.status == "PROCESSING") {
                    runCatching { client.cookingPlanTask(planId) }
                        .onSuccess { response ->
                            when {
                                response.isSuccessful -> taskProgress = response.body()
                                response.code() == 404 -> taskProgress = null
                                else -> error = "Could not refresh generation progress (HTTP ${response.code()})."
                            }
                        }
                    delay(2000)
                    refresh++
                } else {
                    taskProgress = null
                    if (it.status == "READY") {
                        runCatching { client.cookingPlanExecution(planId) }
                            .onSuccess { progress -> execution = progress }
                            .onFailure { error = friendlyCookingError(it, "Could not load account-synchronised cooking progress.") }
                    }
                }
            }
            .onFailure { error = friendlyCookingError(it, "Could not load the plan.") }
    }
    FoodMindDetailScaffold("Cooking plan", onBack) { padding ->
        val value = plan
        when {
            value == null && error == null -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            value == null -> Column(Modifier.padding(padding).padding(24.dp)) {
                Text(error.orEmpty(), color = FoodMindCoral)
                TextButton(onClick = { refresh++ }) { Text("Try again") }
            }
            else -> {
                val status = value.status
                val timeline = buildAndroidExecutionTimeline(value)
                val boardStates = timeline.associate { task ->
                    val remote = execution?.steps?.firstOrNull { it.stepId == task.taskId }?.status
                    task.taskId to runCatching { remote?.let(BoardState::valueOf) }.getOrNull().let { it ?: BoardState.PENDING }
                }
                val board = computeExecutionBoard(timeline, boardStates, execution?.version?.toInt() ?: 0)
                val total = timeline.size
                val done = board.completed.size
                var finishing by remember(planId) { mutableStateOf(false) }
                var finishError by remember(planId) { mutableStateOf<String?>(null) }
                var timelineExpanded by remember(planId) { mutableStateOf(false) }
                var preparationExpanded by remember(planId) { mutableStateOf(false) }
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Text(status.replace('_', ' '), color = if (status == "READY") FoodMindGreen else FoodMindCoral, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("Your FoodMind cooking plan", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            value.explanation ?: when (status) {
                                "PROCESSING" -> "The Cooking Agent is building an ordered plan in the background."
                                "NEEDS_CONFIRMATION" -> "The Cooking Agent needs one inventory decision before it can finish."
                                else -> "${timeline.size} ordered tasks across the dishes you picked."
                            },
                            color = FoodMindMuted,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                        Text(
                            listOfNotNull(
                                value.solverStatus?.let { "Solver ${it.replace('_', ' ').lowercase()}" },
                                value.makespanMinutes?.let { "$it minute makespan" },
                                value.region?.let { "Region $it" },
                            ).joinToString(" · "),
                            color = FoodMindFaint,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (status == "READY" && value.finishedAt != null) {
                        item {
                            FoodMindSurfaceCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Cooking complete", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Your allocated ingredients have been deducted from inventory.", color = FoodMindMuted)
                                Text(
                                    "${value.sources.size} ${if (value.sources.size == 1) "dish" else "dishes"} · ${value.timeline.size} cooking steps" +
                                        (value.makespanMinutes?.let { " · $it minute planned cook" } ?: ""),
                                    color = FoodMindGreen,
                                )
                                if (value.reusedFromPlanId != null) {
                                    Text("This schedule was reused from your previous equivalent plan.", color = FoodMindMuted, fontSize = 12.sp)
                                }
                                AndroidSavedPlanControls(client, planId, execution, finished = true) { execution = it }
                                OutlinedButton(
                                    onClick = {
                                        onCookAgain(cookAgainRecipeIds(value.sources))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Cook again") }
                                Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Back to Home") }
                            } }
                        }
                    } else when (status) {
                        "PROCESSING" -> item {
                            FoodMindSurfaceCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Building your cooking plan", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                Text("The Cooking Agent is working in the background. This page updates automatically.", color = FoodMindMuted)
                                taskProgress?.progress?.let { progress ->
                                    Text(
                                        progress.node?.replace('_', ' ')?.lowercase()?.replaceFirstChar(Char::uppercase)
                                            ?: "Working",
                                        color = FoodMindGreen,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text("${progress.completedSteps} steps completed", color = FoodMindMuted, fontSize = 12.sp)
                                    progress.message?.let { Text(it, color = FoodMindMuted, fontSize = 12.sp) }
                                } ?: Text("Submitted — waiting for the first progress update…", color = FoodMindMuted, fontSize = 12.sp)
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            cancelling = true
                                            error = null
                                            runCatching { client.cancelCookingPlanTask(planId) }
                                                .onSuccess { response ->
                                                    when {
                                                        response.isSuccessful && response.body() != null -> plan = response.body()
                                                        response.code() == 409 -> refresh++
                                                        else -> error = "Could not cancel generation (HTTP ${response.code()})."
                                                    }
                                                }
                                                .onFailure { error = friendlyCookingError(it, "Could not cancel generation.") }
                                            cancelling = false
                                        }
                                    },
                                    enabled = !cancelling,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (cancelling) "Cancelling…" else "Cancel generation") }
                                error?.let { Text(it, color = FoodMindCoral, fontSize = 12.sp) }
                            } }
                        }
                        "READY" -> {
                            fun advance(taskId: String, next: BoardState) {
                                val allowed = when (next) {
                                    BoardState.IN_PROGRESS -> board.available.any { it.taskId == taskId }
                                    else -> board.inProgress.any { it.taskId == taskId }
                                }
                                if (!allowed) {
                                    Toast.makeText(context, "EXECUTION_STATE_CONFLICT — the board moved elsewhere. Refreshing…", Toast.LENGTH_SHORT).show()
                                    return
                                }
                                val current = execution ?: return
                                scope.launch {
                                    runCatching { client.updateCookingPlanExecution(planId, taskId, next.name, current.version) }
                                        .onSuccess { execution = it }
                                        .onFailure {
                                            Toast.makeText(context, "Progress changed on another device. Loading the latest state…", Toast.LENGTH_SHORT).show()
                                            runCatching { client.cookingPlanExecution(planId) }.onSuccess { execution = it }
                                        }
                                }
                            }
                            item {
                                FoodMindSurfaceCard { Column(Modifier.padding(16.dp)) {
                                    AndroidSavedPlanControls(client, planId, execution) { execution = it }
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("EXECUTION PROGRESS", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                                            Text("$done of $total tasks complete", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("${if (total == 0) 0 else done * 100 / total}%", color = FoodMindLime, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    LinearProgressIndicator(
                                        progress = { if (total == 0) 0f else done.toFloat() / total },
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                    )
                                    if ((done > 0 || board.inProgress.isNotEmpty()) && done != total) TextButton(onClick = {
                                        val current = execution ?: return@TextButton
                                        scope.launch {
                                            runCatching { client.resetCookingPlanExecution(planId, current.version) }
                                                .onSuccess { execution = it }
                                                .onFailure { Toast.makeText(context, "Progress changed on another device. Refresh and try again.", Toast.LENGTH_SHORT).show(); refresh++ }
                                        }
                                    }, enabled = execution != null) { Text("Reset progress") }
                                    Text("Progress is saved to your FoodMind account and shared with Web. Inventory is deducted when you finish the plan.", color = FoodMindMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                finishing = true
                                                finishError = null
                                                runCatching { client.finishCookingPlan(planId) }
                                                    .onSuccess { finished ->
                                                        plan = finished
                                                    }
                                                    .onFailure { finishError = friendlyCookingError(it, "Could not finish the plan. Refresh inventory or regenerate the plan.") }
                                                finishing = false
                                            }
                                        },
                                        enabled = execution != null && canFinishCookingPlan(total, done, value.finishedAt) && !finishing,
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                    ) { Text(if (finishing) "Finishing plan…" else "Finish plan") }
                                    finishError?.let { Text(it, color = FoodMindCoral, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                                    if (value.reusedFromPlanId != null) {
                                        Text("Reused from your previous equivalent plan.", color = FoodMindMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                                    }
                                } }
                            }
                            when {
                                board.inProgress.isNotEmpty() -> item {
                                    BoardLane("Now", board.inProgress, FoodMindCoral) { task ->
                                        TextButton(onClick = { advance(task.taskId, BoardState.COMPLETED) }) { Text("Complete") }
                                    }
                                }
                                board.available.isNotEmpty() -> item {
                                    BoardLane("Up next", board.available, FoodMindLime) { task ->
                                        TextButton(onClick = { advance(task.taskId, BoardState.IN_PROGRESS) }) { Text("Start") }
                                    }
                                }
                            }

                            if (total > 0) item {
                                OutlinedButton(
                                    onClick = { timelineExpanded = !timelineExpanded },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (timelineExpanded) "Hide full timeline" else "View full timeline · $total steps")
                                }
                            }
                            if (timelineExpanded) {
                                if (board.blocked.isNotEmpty()) {
                                    item { BoardLane("Later", board.blocked.map { it.first }, FoodMindFaint, reason = { task -> board.blocked.firstOrNull { it.first.taskId == task.taskId }?.second }) {} }
                                }
                                if (board.completed.isNotEmpty()) {
                                    item { BoardLane("Completed", board.completed, FoodMindGreen) {} }
                                }
                            }

                            if (value.miseEnPlace.isNotEmpty() || value.completionChecklist.isNotEmpty()) item {
                                OutlinedButton(
                                    onClick = { preparationExpanded = !preparationExpanded },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    val count = value.miseEnPlace.size + value.completionChecklist.size
                                    Text(if (preparationExpanded) "Hide preparation & checks" else "Preparation & checks · $count items")
                                }
                            }
                            if (preparationExpanded && value.miseEnPlace.isNotEmpty()) {
                                item { Text("Mise en place", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                                items(value.miseEnPlace, key = { it.sequenceNo ?: it.instruction.hashCode() }) { mise ->
                                    FoodMindSurfaceCard { Column(Modifier.padding(14.dp)) {
                                        Text("${mise.sequenceNo ?: ""}. ${mise.instruction}", fontWeight = FontWeight.Medium)
                                        Text(listOfNotNull(mise.ingredient, mise.durationMinutes?.let { "$it min" }).joinToString(" · "), color = FoodMindMuted, fontSize = 12.sp)
                                    } }
                                }
                            }
                            if (preparationExpanded && value.completionChecklist.isNotEmpty()) {
                                item { Text("Completion checklist", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                                items(value.completionChecklist, key = { it.completionItemId }) { item ->
                                    FoodMindSurfaceCard { Row(Modifier.fillMaxWidth()) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.ingredientName, fontWeight = FontWeight.Bold)
                                            Text("Used by ${item.recipeIds.size} dishes", color = FoodMindMuted, fontSize = 12.sp)
                                        }
                                        Text(item.allocations.joinToString { "${it.quantity} ${it.unit}" }, color = FoodMindGreen)
                                    } }
                                }
                            }
                        }
                        "NEEDS_CONFIRMATION" -> {
                            item {
                                Text("Your plan needs a decision", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Choose how FoodMind should handle the inventory shortage.", color = FoodMindMuted, fontSize = 12.sp)
                            }
                            item {
                                ConfirmationForm(
                                    questions = value.confirmationQuestions,
                                    repairOptions = value.repairOptions,
                                    decisions = value.decisions,
                                    onSubmit = { answers ->
                                        val response = client.submitCookingPlanDecisionsAsync(planId, answers)
                                        val nextId = response.body()?.planId.takeIf { response.isSuccessful }
                                            ?: error("The backend did not accept this decision.")
                                        context.startActivity(CookingPlanDetailActivity.intent(context, nextId))
                                        onBack()
                                    },
                                    onPurchase = {
                                        val shoppingList = client.createShoppingList(planId)
                                        context.startActivity(ShoppingListActivity.intent(context, shoppingList.shoppingListId))
                                        onBack()
                                    },
                                )
                            }
                        }
                        "INFEASIBLE" -> {
                            item { Text("No feasible cooking plan", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                            item {
                                FoodMindSurfaceCard { Column(Modifier.padding(16.dp)) {
                                    if (value.reasons.isEmpty()) Text("Current inventory cannot satisfy the selected recipes. Adjust the inputs and try again.", color = FoodMindMuted)
                                    else value.reasons.forEach { Text("• $it", color = FoodMindMuted, modifier = Modifier.padding(top = 4.dp)) }
                                } }
                            }
                            if (value.safeAlternatives.isNotEmpty()) {
                                item { Text("Safe alternatives", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                                item { FoodMindSurfaceCard { Column(Modifier.padding(16.dp)) { value.safeAlternatives.forEach { Text("• $it", color = FoodMindMuted, modifier = Modifier.padding(top = 4.dp)) } } } }
                            }
                        }
                        else -> {
                            item { Text(if (value.errorCode == "TASK_CANCELLED") "Cooking plan cancelled" else "A plan could not be completed", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                            item {
                                FoodMindSurfaceCard { Column(Modifier.padding(16.dp)) {
                                    Text(
                                        value.errorMessage ?: if (value.errorCode == "TASK_CANCELLED") "You cancelled this generation before it finished."
                                        else "FoodMind could not finish this plan. Choose the recipes again or adjust the constraints.",
                                        color = FoodMindMuted,
                                    )
                                    TextButton(onClick = onBack) { Text("Choose recipes") }
                                } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmationForm(
    questions: List<CookingConfirmationQuestionResponse>,
    repairOptions: List<CookingRepairOptionResponse>,
    decisions: List<CookingDecisionResponse>,
    onSubmit: suspend (List<QuestionAnswerRequest>) -> Unit,
    onPurchase: suspend () -> Unit,
) {
    var choices by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var texts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val strategyQuestion = questions.firstOrNull { it.fieldPath == "repair_strategy" }
    val remainingQuestions = if (strategyQuestion == null) questions else emptyList()
    val purchasePreview = remember(repairOptions) { parsePurchasePreview(repairOptions) }
    val reduceDecision = decisions.firstOrNull { it.optionType == "reduce_servings" }
    val strategyChoice = strategyQuestion?.let { choices[it.questionId] }
    val purchaseOptionIds = decisions.filter { it.optionType == "purchase" }.map { it.optionId }.toSet()
    val selectedIsPurchase = strategyChoice in purchaseOptionIds
    val requiredMissing = (if (strategyQuestion == null) questions else listOf(strategyQuestion)).any { question ->
        question.required && when (question.responseType) {
            "CHOICE" -> choices[question.questionId].isNullOrBlank()
            else -> (texts[question.questionId] ?: question.suggestedValue.orEmpty()).isBlank()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        strategyQuestion?.let { question ->
            FoodMindSurfaceCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("INVENTORY SHORTAGE", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(question.prompt, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("Reducing portions rechecks inventory. Buying opens a shopping list that remains available after you leave the app.", color = FoodMindMuted, fontSize = 12.sp)
                question.options.forEach { option ->
                    val selected = choices[question.questionId] == option.value
                    val choose = { choices = choices + (question.questionId to option.value); submitError = null }
                    if (selected) Button(onClick = choose, modifier = Modifier.fillMaxWidth()) { Text(option.label) }
                    else OutlinedButton(onClick = choose, modifier = Modifier.fillMaxWidth()) { Text(option.label) }
                }
                if (!selectedIsPurchase) (reduceDecision?.payload as? Map<*, *>)?.get("servings")?.let { servings ->
                    val servingLabel = servings.toString().removeSuffix(".0")
                    Text("FoodMind will recheck this plan at $servingLabel ${if (servingLabel == "1") "serving" else "servings"}.", color = FoodMindMuted, fontSize = 12.sp)
                }
                Text(if (question.required) "Choose one option" else "Optional", color = FoodMindFaint, fontSize = 11.sp)
            } }
            if (selectedIsPurchase && purchasePreview.isNotEmpty()) {
                FoodMindSurfaceCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Shopping list preview", fontWeight = FontWeight.Bold)
                    purchasePreview.forEach { item ->
                        Text("${item.first} · ${item.second}", color = FoodMindMuted)
                    }
                } }
            }
        }
        remainingQuestions.forEach { question ->
            FoodMindSurfaceCard { Column(Modifier.padding(14.dp)) {
                Text(question.prompt, fontWeight = FontWeight.Bold)
                when (question.responseType) {
                    "CHOICE" -> question.options.forEach { option ->
                        val selected = choices[question.questionId] == option.value
                        Row(
                            Modifier.fillMaxWidth().clickable { choices = choices + (question.questionId to option.value) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected, { choices = choices + (question.questionId to option.value) })
                            Text(option.label, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    else -> OutlinedTextField(
                        value = texts[question.questionId] ?: question.suggestedValue.orEmpty(),
                        onValueChange = { texts = texts + (question.questionId to it) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            } }
        }
        submitError?.let { Text(it, color = FoodMindCoral) }
        Button(onClick = {
            val answers = buildList {
                questions.forEach { q ->
                    when (q.responseType) {
                        "CHOICE" -> choices[q.questionId]?.let { add(QuestionAnswerRequest(q.questionId, it)) }
                        else -> (texts[q.questionId] ?: q.suggestedValue.orEmpty()).trim().takeIf(String::isNotEmpty)?.let { add(QuestionAnswerRequest(q.questionId, it)) }
                    }
                }
            }
            scope.launch {
                submitting = true; submitError = null
                runCatching { if (selectedIsPurchase) onPurchase() else onSubmit(answers) }
                    .onFailure { submitError = friendlyCookingError(it, "Could not continue this plan. Please try again.") }
                submitting = false
            }
        }, enabled = !submitting && !requiredMissing, modifier = Modifier.fillMaxWidth()) {
            Text(if (submitting) "Continuing…" else if (selectedIsPurchase) "Open shopping list" else "Reduce portions and recheck")
        }
    }
}

private fun parsePurchasePreview(repairOptions: List<CookingRepairOptionResponse>): List<Pair<String, String>> {
    val regex = Regex("""Purchase\s+([\d.]+)\s+(\S+)\s+of\s+'([^']+)'""")
    return repairOptions
        .filter { it.optionType == "purchase" }
        .mapNotNull { option ->
            val match = regex.find(option.description) ?: return@mapNotNull null
            match.groupValues[3] to "${match.groupValues[1]} ${match.groupValues[2]}"
        }
}

@Composable
private fun AndroidSavedPlanControls(
    client: FoodMindApiClient,
    planId: String,
    execution: CookingPlanExecutionResponse?,
    finished: Boolean = false,
    onUpdated: (CookingPlanExecutionResponse) -> Unit,
) {
    var busy by remember(planId) { mutableStateOf(false) }
    var actionError by remember(planId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    when {
        execution == null -> Text("Loading saved state…", color = FoodMindMuted, fontSize = 11.sp)
        execution.savedAt == null -> OutlinedButton(
            onClick = {
                scope.launch {
                    busy = true; actionError = null
                    runCatching { client.saveCookingPlan(planId) }
                        .onSuccess(onUpdated)
                        .onFailure { actionError = friendlyCookingError(it, "Could not save this cooking plan.") }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) { Text(if (busy) "Saving…" else "Save plan") }
        else -> Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text("Saved to your FoodMind account", color = FoodMindGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            TextButton(onClick = {
                scope.launch {
                    busy = true; actionError = null
                    runCatching { client.removeSavedCookingPlan(planId, false) }
                        .onSuccess(onUpdated)
                        .onFailure { actionError = friendlyCookingError(it, "Could not remove this plan from Saved.") }
                    busy = false
                }
            }, enabled = !busy) { Text("Remove from Saved") }
            if (!finished) TextButton(onClick = {
                scope.launch {
                    busy = true; actionError = null
                    runCatching { client.removeSavedCookingPlan(planId, true) }
                        .onSuccess(onUpdated)
                        .onFailure { actionError = friendlyCookingError(it, "Could not remove and reset this plan.") }
                    busy = false
                }
            }, enabled = !busy) { Text("Remove & reset progress") }
        }
    }
    actionError?.let { Text(it, color = FoodMindCoral, fontSize = 11.sp) }
}

private fun buildAndroidExecutionTimeline(plan: CookingPlanResponse): List<CookingTimelineTaskResponse> {
    val scheduled = plan.timeline.sortedBy { it.startMinute }
    fun normalise(value: String?): String = value.orEmpty()
        .replace(Regex("^\\[(?:prep|mise en place)]\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()
    val missingPrep = plan.miseEnPlace.filter { item ->
        val candidates = listOf(item.instruction, item.ingredient).map(::normalise).filter(String::isNotBlank)
        val operation = normalise(item.operation)
        val ingredient = normalise(item.ingredient)
        scheduled.none { task ->
            val text = normalise(task.instruction)
            candidates.any { candidate -> candidate == text || (minOf(candidate.length, text.length) >= 24 && (candidate.contains(text) || text.contains(candidate))) } ||
                (operation.length >= 3 && ingredient.length >= 3 && text.contains(operation) && text.contains(ingredient))
        }
    }.mapIndexed { index, item ->
        val sequence = item.sequenceNo ?: index + 1
        CookingTimelineTaskResponse(
            taskId = "mise:$sequence",
            startMinute = 0,
            endMinute = item.durationMinutes ?: 0,
            durationMinutes = item.durationMinutes ?: 0,
            instruction = item.instruction,
            dishId = "shared",
            workMode = "ACTIVE",
            category = "preparation",
            resources = item.resources,
        )
    }
    return (missingPrep + scheduled).sortedBy { it.startMinute }
}

// The plan shape is immutable; only these account-backed step states change.

private enum class BoardState { PENDING, IN_PROGRESS, COMPLETED }

internal fun canFinishCookingPlan(total: Int, completed: Int, finishedAt: String?): Boolean =
    total > 0 && completed == total && finishedAt == null

internal fun cookAgainRecipeIds(sources: List<CookingPlanSourceResponse>): List<String> =
    sources.mapNotNull { it.sourceId?.takeIf(String::isNotBlank) }.distinct()

private data class ExecutionBoard(
    val available: List<CookingTimelineTaskResponse>,
    val inProgress: List<CookingTimelineTaskResponse>,
    val completed: List<CookingTimelineTaskResponse>,
    val blocked: List<Pair<CookingTimelineTaskResponse, String>>,
    val expectedEventId: String,
)

private fun computeExecutionBoard(
    tasks: List<CookingTimelineTaskResponse>,
    states: Map<String, BoardState>,
    eventId: Int,
): ExecutionBoard {
    val ordered = tasks.sortedBy { it.startMinute }
    val completed = ordered.filter { states[it.taskId] == BoardState.COMPLETED }
    val inProgress = ordered.filter { states[it.taskId] == BoardState.IN_PROGRESS }
    val available = mutableListOf<CookingTimelineTaskResponse>()
    val blocked = mutableListOf<Pair<CookingTimelineTaskResponse, String>>()
    var offered = false
    ordered.forEachIndexed { index, task ->
        val state = states[task.taskId]
        if (state == BoardState.COMPLETED || state == BoardState.IN_PROGRESS) return@forEachIndexed
        val depsDone = ordered.take(index).all { states[it.taskId] == BoardState.COMPLETED }
        val resourceFree = inProgress.none { active -> active.resources.any { task.resources.contains(it) } }
        if (!offered && depsDone && resourceFree) {
            available.add(task); offered = true
        } else {
            val reasons = mutableListOf<String>()
            if (!depsDone) reasons.add("Waiting for the previous step")
            if (!resourceFree) reasons.add("A shared resource is still in use")
            blocked.add(task to reasons.joinToString(" · "))
        }
    }
    return ExecutionBoard(available, inProgress, completed, blocked, "evt-$eventId")
}

@Composable
private fun BoardLane(
    title: String,
    tasks: List<CookingTimelineTaskResponse>,
    tone: Color,
    modifier: Modifier = Modifier,
    reason: (CookingTimelineTaskResponse) -> String? = { null },
    action: @Composable (CookingTimelineTaskResponse) -> Unit = {},
) {
    FoodMindSurfaceCard(modifier) { Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(tone, CircleShape))
            Text(title.uppercase(), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = .5.sp, modifier = Modifier.padding(start = 8.dp))
            Text(" ${tasks.size}", color = FoodMindFaint, fontSize = 11.sp)
        }
        tasks.forEach { task ->
            Card(
                colors = CardDefaults.cardColors(containerColor = FoodMindSurfaceRaised),
                border = BorderStroke(1.dp, FoodMindLineSoft),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.instruction, fontWeight = FontWeight.Medium)
                        Text(
                            "${task.durationMinutes} min · starts ${task.startMinute / 60}:${String.format(Locale.ROOT, "%02d", task.startMinute % 60)}${if (task.workMode == "PASSIVE") " · passive" else ""}" +
                                if (task.resources.isEmpty()) "" else " · ${task.resources.joinToString("/")}",
                            color = FoodMindMuted,
                            fontSize = 11.sp,
                        )
                        reason(task)?.let { Text(it, color = FoodMindCoral, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)) }
                    }
                    action(task)
                }
            } }
        }
    } }
}

internal fun buildCookingQuestionAnswers(
    questions: List<CookingConfirmationQuestionResponse>,
    choices: Map<String, String>,
    texts: Map<String, String>,
): List<QuestionAnswerRequest> = buildList {
    questions.forEach { question ->
        val value = when (question.responseType) {
            "CHOICE" -> choices[question.questionId]
            else -> texts[question.questionId] ?: question.suggestedValue
        }?.trim().orEmpty()
        if (value.isNotEmpty()) add(QuestionAnswerRequest(question.questionId, value))
    }
}

internal fun canSubmitCookingQuestions(
    questions: List<CookingConfirmationQuestionResponse>,
    choices: Map<String, String>,
    texts: Map<String, String>,
): Boolean {
    val answers = buildCookingQuestionAnswers(questions, choices, texts)
    val answeredIds = answers.mapTo(mutableSetOf()) { it.questionId }
    return answers.isNotEmpty() && questions.none { it.required && it.questionId !in answeredIds }
}
