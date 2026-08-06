package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.CookingIngredientRequest
import com.foodmind.foodmind_android.core.network.CookingPlanResponse
import com.foodmind.foodmind_android.core.network.CookingPlanSummary
import com.foodmind.foodmind_android.core.network.CookingPlanTaskResponse
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.GenerateCookingPlanRequest
import com.foodmind.foodmind_android.domain.repository.AsyncSubmitResult
import com.foodmind.foodmind_android.domain.repository.CookingPlanTaskRepository
import kotlinx.coroutines.launch

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
                                asyncError = "后台生成失败（${plan.failureCode ?: plan.status}）。请调整约束后重试。"
                            else -> plan.planId?.let(onOpenPlan) ?: run { asyncError = "后台生成完成，但未返回计划 ID。" }
                        }
                    }
                    .onFailure { asyncError = "后台生成未能在超时前完成，请稍后在计划详情中查看。" }
            } else {
                val failedStatus = (accepted as? AsyncSubmitResult.TerminalFailed)?.status ?: "未知"
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
            items(history, key = { it.planId.orEmpty() }) { plan -> Card(onClick = { plan.planId?.let(onOpenPlan) }, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) { Column(Modifier.fillMaxWidth().padding(14.dp)) { Text("${plan.inputCount} ingredients · ${plan.stepCount} steps", fontWeight = FontWeight.Bold); Text("${plan.status} · ${formatFoodMindTimestamp(plan.createdAt)}", color = FoodMindMuted, fontSize = 12.sp) } } }
        }
    }
}

class CookingPlanDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_PLAN_ID).orEmpty(); val client = foodMindApiClient()
        setContent { FoodMindTheme { CookingPlanDetailScreen(client, id, ::finish) } }
    }
    companion object { private const val EXTRA_PLAN_ID = "plan_id"; fun intent(context: Context, planId: String) = Intent(context, CookingPlanDetailActivity::class.java).putExtra(EXTRA_PLAN_ID, planId) }
}

@Composable
private fun CookingPlanDetailScreen(client: FoodMindApiClient, planId: String, onBack: () -> Unit) {
    var plan by remember { mutableStateOf<CookingPlanResponse?>(null) }; var error by remember { mutableStateOf<String?>(null) }; var completed by remember { mutableStateOf(setOf<Int>()) }; var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(planId, refresh) { runCatching { client.cookingPlan(planId) }.onSuccess { plan = it; error = null }.onFailure { error = "Could not load the plan." } }
    FoodMindDetailScaffold("Cooking plan", onBack) { padding ->
        val value = plan
        when {
            value == null && error == null -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            value == null -> Column(Modifier.padding(padding).padding(24.dp)) { Text(error.orEmpty(), color = FoodMindCoral); TextButton(onClick = { refresh++ }) { Text("Try again") } }
            value.status == "NO_VALID_RECIPE" || value.status == "FAILED" -> Column(Modifier.padding(padding).padding(24.dp)) { Text("No actionable plan", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text("Dietary and allergen constraints remain active. Adjust ingredients and try again.", color = FoodMindMuted) }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Text(value.status.replace('_', ' '), color = FoodMindGreen, fontWeight = FontWeight.Bold); Text("Your FoodMind cooking plan", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Text("${value.ingredients.size} ingredients · ${value.steps.size} ordered steps", color = FoodMindMuted) }
                item { FoodMindSurfaceCard { Column { Text("Progress ${completed.size}/${value.steps.size}", fontWeight = FontWeight.Bold); LinearProgressIndicator(progress = { if (value.steps.isEmpty()) 0f else completed.size.toFloat() / value.steps.size }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)); Text("Progress is stored only on this screen; it does not claim the backend is complete or inventory changed.", color = FoodMindMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp)) } } }
                item { Text("Ingredient plan", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                items(value.ingredients, key = { it.sequenceNo ?: it.ingredientName.hashCode() }) { ingredient -> FoodMindSurfaceCard { Row(Modifier.fillMaxWidth()) { Column(Modifier.weight(1f)) { Text(ingredient.ingredientName, fontWeight = FontWeight.Bold); Text(listOfNotNull(ingredient.quantity?.toString(), ingredient.unit).joinToString(" "), color = FoodMindMuted) }; Text(ingredient.availability?.replace('_', ' ') ?: "", color = FoodMindGreen) } } }
                item { Text("Cook in order", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                items(value.steps.sortedBy { it.stepNo }, key = { it.stepNo ?: it.instruction.hashCode() }) { step ->
                    val number = step.stepNo ?: 0; Card(onClick = { completed = if (number in completed) completed - number else completed + number }, colors = CardDefaults.cardColors(containerColor = if (number in completed) Color(0xFFEEF7F0) else Color.White), border = BorderStroke(1.dp, FoodMindLine)) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(number in completed, { completed = if (number in completed) completed - number else completed + number }); Column(Modifier.padding(start = 6.dp)) { Text("Step $number", color = FoodMindGreen, fontWeight = FontWeight.Bold); Text(step.instruction) } } }
                }
                if (value.warnings.isNotEmpty()) item { FoodMindSurfaceCard { Column { Text("Plan notes", fontWeight = FontWeight.Bold); value.warnings.forEach { Text("• ${it.message ?: it.warningCode}", color = FoodMindCoral, modifier = Modifier.padding(top = 5.dp)) } } } }
                item { Text("The backend does not currently return a recipe title, total time, or nutrition summary, so this screen does not invent them.", color = FoodMindMuted, fontSize = 11.sp) }
            }
        }
    }
}
