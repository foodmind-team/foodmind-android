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
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.GenerateCookingPlanRequest
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
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { client.cookingPlanHistory().items }.onSuccess { history = it } }
    FoodMindDetailScaffold("手动食材", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("用你确定拥有的食材做饭", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Text("每行一种食材，最多 30 行。FoodMind 不会推断你的库存。", color = FoodMindMuted) }
            item { OutlinedTextField(ingredients, { ingredients = it }, label = { Text("食材，每行一种") }, minLines = 7, modifier = Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(servings, { servings = it.filter(Char::isDigit) }, label = { Text("份数") }, modifier = Modifier.weight(1f)); OutlinedTextField(maxMinutes, { maxMinutes = it.filter(Char::isDigit) }, label = { Text("最多分钟") }, modifier = Modifier.weight(1f)) } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(budget, { budget = it }, label = { Text("额外预算") }, modifier = Modifier.weight(1f)); OutlinedTextField(currency, { currency = it.uppercase().take(3) }, label = { Text("币种") }, modifier = Modifier.weight(1f)) } }
            item {
                error?.let { Text(it, color = FoodMindCoral) }
                Button(onClick = { scope.launch {
                    val lines = ingredients.lines().map(String::trim).filter(String::isNotBlank)
                    if (lines.isEmpty() || lines.size > 30) { error = "请输入 1–30 种食材。"; return@launch }
                    generating = true; error = null
                    runCatching { client.generateCookingPlan(GenerateCookingPlanRequest(
                        ingredients = lines.map { CookingIngredientRequest(it, source = "MANUAL") },
                        servings = servings.toIntOrNull() ?: 2,
                        maxMinutes = maxMinutes.toIntOrNull(),
                        maxBudget = budget.toDoubleOrNull(),
                        currency = currency.takeIf { budget.isNotBlank() },
                    )) }
                        .onSuccess { plan -> plan.planId?.let(onOpenPlan) ?: run { error = "后端没有返回计划 ID。" } }.onFailure { error = "烹饪计划生成失败，请检查约束后重试。" }
                    generating = false
                } }, enabled = !generating, modifier = Modifier.fillMaxWidth()) { Text(if (generating) "生成中…" else "生成烹饪计划") }
            }
            item { Text("最近计划", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp)) }
            if (history.isEmpty()) item { Text("还没有计划。", color = FoodMindMuted) }
            items(history, key = { it.planId.orEmpty() }) { plan -> Card(onClick = { plan.planId?.let(onOpenPlan) }, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) { Column(Modifier.fillMaxWidth().padding(14.dp)) { Text("${plan.inputCount} 种食材 · ${plan.stepCount} 个步骤", fontWeight = FontWeight.Bold); Text("${plan.status} · ${plan.createdAt.orEmpty()}", color = FoodMindMuted, fontSize = 12.sp) } } }
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
    LaunchedEffect(planId, refresh) { runCatching { client.cookingPlan(planId) }.onSuccess { plan = it; error = null }.onFailure { error = "计划加载失败。" } }
    FoodMindDetailScaffold("烹饪计划", onBack) { padding ->
        val value = plan
        when {
            value == null && error == null -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            value == null -> Column(Modifier.padding(padding).padding(24.dp)) { Text(error.orEmpty(), color = FoodMindCoral); TextButton(onClick = { refresh++ }) { Text("重试") } }
            value.status == "NO_VALID_RECIPE" || value.status == "FAILED" -> Column(Modifier.padding(padding).padding(24.dp)) { Text("没有可执行计划", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text("饮食与过敏原约束仍保持生效。调整食材后再试。", color = FoodMindMuted) }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Text(value.status.replace('_', ' '), color = FoodMindGreen, fontWeight = FontWeight.Bold); Text("你的 FoodMind 烹饪计划", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Text("${value.ingredients.size} 种食材 · ${value.steps.size} 个有序步骤", color = FoodMindMuted) }
                item { FoodMindSurfaceCard { Column { Text("进度 ${completed.size}/${value.steps.size}", fontWeight = FontWeight.Bold); LinearProgressIndicator(progress = { if (value.steps.isEmpty()) 0f else completed.size.toFloat() / value.steps.size }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)); Text("进度只保存在当前界面，不会声明后端已完成或库存已改变。", color = FoodMindMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp)) } } }
                item { Text("食材计划", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                items(value.ingredients, key = { it.sequenceNo ?: it.ingredientName.hashCode() }) { ingredient -> FoodMindSurfaceCard { Row(Modifier.fillMaxWidth()) { Column(Modifier.weight(1f)) { Text(ingredient.ingredientName, fontWeight = FontWeight.Bold); Text(listOfNotNull(ingredient.quantity?.toString(), ingredient.unit).joinToString(" "), color = FoodMindMuted) }; Text(ingredient.availability?.replace('_', ' ') ?: "", color = FoodMindGreen) } } }
                item { Text("按顺序烹饪", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                items(value.steps.sortedBy { it.stepNo }, key = { it.stepNo ?: it.instruction.hashCode() }) { step ->
                    val number = step.stepNo ?: 0; Card(onClick = { completed = if (number in completed) completed - number else completed + number }, colors = CardDefaults.cardColors(containerColor = if (number in completed) Color(0xFFEEF7F0) else Color.White), border = BorderStroke(1.dp, FoodMindLine)) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(number in completed, { completed = if (number in completed) completed - number else completed + number }); Column(Modifier.padding(start = 6.dp)) { Text("第 $number 步", color = FoodMindGreen, fontWeight = FontWeight.Bold); Text(step.instruction) } } }
                }
                if (value.warnings.isNotEmpty()) item { FoodMindSurfaceCard { Column { Text("计划提示", fontWeight = FontWeight.Bold); value.warnings.forEach { Text("• ${it.message ?: it.warningCode}", color = FoodMindCoral, modifier = Modifier.padding(top = 5.dp)) } } } }
                item { Text("后端当前不返回菜谱标题、总时长或营养总结，因此这里不会自行补写。", color = FoodMindMuted, fontSize = 11.sp) }
            }
        }
    }
}
