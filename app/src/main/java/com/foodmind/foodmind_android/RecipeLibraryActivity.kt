package com.foodmind.foodmind_android

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.core.network.GenerateCookingPlanRequest
import com.foodmind.foodmind_android.domain.repository.RecipeDraft
import com.foodmind.foodmind_android.domain.repository.RecipeDraftStore
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class RecipeLibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this); RecipeDraftStore.initialize(this, FoodMindSession.tokenStore.userId())
        val client = foodMindApiClient()
        setContent { FoodMindTheme { RecipeLibraryScreen(client, ::finish, { startActivity(Intent(this, RecipeEditorActivity::class.java)) }, { startActivity(RecipeEditorActivity.intent(this, it)) }, { startActivity(CookingPlanDetailActivity.intent(this, it)) }, { startActivity(Intent(this, ManualCookingActivity::class.java)) }) } }
    }
}

@Composable
private fun RecipeLibraryScreen(
    client: com.foodmind.foodmind_android.core.network.FoodMindApiClient,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenPlan: (String) -> Unit,
    onManual: () -> Unit,
) {
    var recipes by remember { mutableStateOf(RecipeDraftStore.list()) }; var selected by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }; var category by remember { mutableStateOf("All") }; var servings by remember { mutableStateOf("2") }; var maxMinutes by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { recipes = RecipeDraftStore.list() }
    val categories = listOf("All") + recipes.map(RecipeDraft::category).distinct()
    val visible = recipes.filter { (category == "All" || it.category == category) && it.name.contains(query, ignoreCase = true) }
    val chosen = recipes.filter { it.id in selected }; val targetServings = servings.toIntOrNull()?.coerceIn(1, 24) ?: 2
    val ingredients = chosen.flatMap { recipe -> recipe.ingredients.map { line -> scaleIngredientLine(line, targetServings.toDouble() / recipe.servings.coerceAtLeast(1)) } }.distinct().take(31)
    FoodMindDetailScaffold("Choose recipes", onBack, actions = { IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, "Add recipe") } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Choose what you want to cook", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Text("Choose one or more local drafts. FoodMind scales their ingredients, then asks the current backend for a safe, ordered plan.", color = FoodMindMuted) }
            item { OutlinedTextField(query, { query = it }, label = { Text("Search recipes") }, modifier = Modifier.fillMaxWidth()); FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { categories.forEach { FilterChip(category == it, { category = it }, label = { Text(it) }) } } }
            if (visible.isEmpty()) item { FoodMindSurfaceCard { Text("No matching recipes.") } }
            items(visible, key = RecipeDraft::id) { recipe ->
                Card(onClick = { selected = if (recipe.id in selected) selected - recipe.id else selected + recipe.id }, colors = CardDefaults.cardColors(containerColor = if (recipe.id in selected) Color(0xFFEEF7F0) else Color.White), border = BorderStroke(1.dp, if (recipe.id in selected) FoodMindGreen else FoodMindLine)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(recipe.id in selected, { selected = if (recipe.id in selected) selected - recipe.id else selected + recipe.id }); Column(Modifier.weight(1f).padding(horizontal = 7.dp)) { Text(recipe.name, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("${recipe.servings} servings · ${recipe.minutes} minutes · ${recipe.ingredients.size} ingredients", color = FoodMindMuted, fontSize = 12.sp) }
                        IconButton(onClick = { onEdit(recipe.id) }) { Icon(Icons.Outlined.Edit, "Edit") }; IconButton(onClick = { RecipeDraftStore.delete(recipe.id); recipes = RecipeDraftStore.list(); selected = selected - recipe.id }) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
                    }
                }
            }
            item {
                FoodMindSurfaceCard { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("${chosen.size} recipes selected", fontWeight = FontWeight.Bold); Text("${ingredients.size} ingredient lines will be sent to the backend", color = FoodMindMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(servings, { servings = it.filter(Char::isDigit) }, label = { Text("Target servings") }, modifier = Modifier.weight(1f)); OutlinedTextField(maxMinutes, { maxMinutes = it.filter(Char::isDigit) }, label = { Text("Time limit") }, modifier = Modifier.weight(1f)) }
                    if (ingredients.size > 30) Text("The current API accepts at most 30 ingredient lines. Select fewer recipes.", color = FoodMindCoral)
                    error?.let { Text(it, color = FoodMindCoral) }
                    Button(onClick = { scope.launch { generating = true; error = null; runCatching { client.generateCookingPlan(GenerateCookingPlanRequest(ingredients.take(30).map { CookingIngredientRequest(it, source = "MANUAL") }, targetServings, maxMinutes.toIntOrNull())) }.onSuccess { plan -> plan.planId?.let(onOpenPlan) ?: run { error = "The backend did not return a plan ID." } }.onFailure { error = "Generation failed. Please try again later." }; generating = false } }, enabled = chosen.isNotEmpty() && ingredients.isNotEmpty() && ingredients.size <= 30 && !generating, modifier = Modifier.fillMaxWidth()) { Text(if (generating) "Generating…" else "Generate plan") }
                    OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("Enter ingredients manually instead") }
                } }
            }
        }
    }
}

internal fun scaleIngredientLine(line: String, factor: Double): String {
    val match = Regex("^(.*?)(\\d+(?:\\.\\d+)?)\\s*([\\p{L}]+)?$").find(line.trim()) ?: return line.trim()
    val name = match.groupValues[1].trim(); val amount = match.groupValues[2].toDoubleOrNull() ?: return line.trim(); val unit = match.groupValues.getOrNull(3).orEmpty()
    val scaled = amount * factor; val text = if (scaled % 1.0 == 0.0) scaled.toInt().toString() else "%.2f".format(scaled).trimEnd('0').trimEnd('.')
    return listOf(name, text, unit).filter(String::isNotBlank).joinToString(" ")
}
