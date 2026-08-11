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
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.GenerateCookingPlanRequest
import com.foodmind.foodmind_android.core.network.UserRecipeResponse
import com.foodmind.foodmind_android.domain.repository.UserRecipeRepository
import kotlinx.coroutines.launch

class RecipeLibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = foodMindApiClient()
        setContent {
            FoodMindTheme {
                RecipeLibraryScreen(
                    client = client,
                    onBack = ::finish,
                    onAdd = { startActivity(Intent(this, RecipeEditorActivity::class.java)) },
                    onImport = { startActivity(Intent(this, RecipeImportActivity::class.java)) },
                    onEdit = { startActivity(RecipeEditorActivity.intent(this, it)) },
                    onOpenPlan = { startActivity(CookingPlanDetailActivity.intent(this, it)) },
                    onManual = { startActivity(Intent(this, ManualCookingActivity::class.java)) },
                    onInventory = { startActivity(Intent(this, InventoryActivity::class.java)) },
                    onShopping = { startActivity(Intent(this, ShoppingListsActivity::class.java)) },
                )
            }
        }
    }
}

@Composable
private fun RecipeLibraryScreen(
    client: FoodMindApiClient,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenPlan: (String) -> Unit,
    onManual: () -> Unit,
    onInventory: () -> Unit,
    onShopping: () -> Unit,
) {
    val repository = remember(client) { UserRecipeRepository(client) }
    val scope = rememberCoroutineScope()
    var recipes by remember { mutableStateOf<List<UserRecipeResponse>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("All") }
    var servings by remember { mutableStateOf("2") }
    var maxMinutes by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(refresh) {
        loading = true
        runCatching { repository.list() }
            .onSuccess { serverRecipes ->
                recipes = serverRecipes
                selected = selected.intersect(serverRecipes.map(UserRecipeResponse::id).toSet())
                error = null
            }
            .onFailure { error = it.message ?: "Could not load server recipes." }
        loading = false
    }

    val tags = listOf("All") + recipes.flatMap(UserRecipeResponse::tags).distinct().sorted()
    val visible = recipes.filter { recipe ->
        (tag == "All" || tag in recipe.tags) &&
            listOf(recipe.name, recipe.ingredients.joinToString(" "), recipe.tags.joinToString(" "))
                .joinToString(" ").contains(query.trim(), ignoreCase = true)
    }
    val chosen = recipes.filter { it.id in selected }
    val targetServings = servings.toIntOrNull()?.coerceIn(1, 24) ?: 2

    FoodMindDetailScaffold(
        title = "Cloud recipes",
        onBack = onBack,
        actions = {
            IconButton(onClick = onInventory) { Icon(Icons.Outlined.Inventory2, "Inventory") }
            IconButton(onClick = onShopping) { Icon(Icons.Outlined.ShoppingCart, "Shopping lists") }
            IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, "Add recipe") }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Choose what you want to cook", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Recipes are persisted by FoodMind Backend and stay consistent across Web and Android.", color = FoodMindMuted)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("Add manually") }
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import with Agent") }
                }
            }
            item {
                OutlinedTextField(query, { query = it }, label = { Text("Search recipes") }, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    tags.forEach { value -> FilterChip(tag == value, { tag = value }, label = { Text(value) }) }
                }
            }
            if (loading) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
            if (!loading && visible.isEmpty()) item { FoodMindSurfaceCard { Text("No matching server recipes. Add or import one to continue.") } }
            items(visible, key = UserRecipeResponse::id) { recipe ->
                Card(
                    onClick = { selected = if (recipe.id in selected) selected - recipe.id else selected + recipe.id },
                    colors = CardDefaults.cardColors(containerColor = if (recipe.id in selected) Color(0xFFEEF7F0) else Color.White),
                    border = BorderStroke(1.dp, if (recipe.id in selected) FoodMindGreen else FoodMindLine),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(recipe.id in selected, { checked -> selected = if (checked) selected + recipe.id else selected - recipe.id })
                        Column(Modifier.weight(1f).padding(horizontal = 7.dp)) {
                            Text(recipe.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("${recipe.servings} servings · ${recipe.ingredients.size} ingredients · ${recipe.steps.size} steps", color = FoodMindMuted, fontSize = 12.sp)
                            if (recipe.tags.isNotEmpty()) Text(recipe.tags.joinToString(" · "), color = FoodMindGreen, fontSize = 12.sp)
                        }
                        IconButton(onClick = { onEdit(recipe.id) }) { Icon(Icons.Outlined.Edit, "Edit") }
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { repository.delete(recipe.id) }
                                    .onSuccess { refresh++ }
                                    .onFailure { error = it.message ?: "Could not delete recipe." }
                            }
                        }) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
                    }
                }
            }
            item {
                FoodMindSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("${chosen.size} server recipes selected", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(servings, { servings = it.filter(Char::isDigit) }, label = { Text("Target servings") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(maxMinutes, { maxMinutes = it.filter(Char::isDigit) }, label = { Text("Time limit") }, modifier = Modifier.weight(1f))
                        }
                        error?.let { Text(it, color = FoodMindCoral) }
                        Button(
                            onClick = {
                                scope.launch {
                                    generating = true
                                    error = null
                                    runCatching {
                                        client.generateCookingPlan(
                                            GenerateCookingPlanRequest(
                                                servings = targetServings,
                                                maxMinutes = maxMinutes.toIntOrNull(),
                                                recipeIds = chosen.map(UserRecipeResponse::id),
                                                region = "SG",
                                            ),
                                        )
                                    }.onSuccess { plan ->
                                        plan.planId?.let(onOpenPlan) ?: run { error = "The backend did not return a plan ID." }
                                    }.onFailure { error = it.message ?: "Generation failed. Please try again." }
                                    generating = false
                                }
                            },
                            enabled = chosen.isNotEmpty() && !generating,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (generating) "Generating…" else "Generate plan") }
                        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("Enter ingredients manually instead") }
                    }
                }
            }
        }
    }
}

internal fun scaleIngredientLine(line: String, factor: Double): String {
    val match = Regex("^(.*?)(\\d+(?:\\.\\d+)?)\\s*([\\p{L}]+)?$").find(line.trim()) ?: return line.trim()
    val name = match.groupValues[1].trim()
    val amount = match.groupValues[2].toDoubleOrNull() ?: return line.trim()
    val unit = match.groupValues.getOrNull(3).orEmpty()
    val scaled = amount * factor
    val text = if (scaled % 1.0 == 0.0) scaled.toInt().toString() else "%.2f".format(scaled).trimEnd('0').trimEnd('.')
    return listOf(name, text, unit).filter(String::isNotBlank).joinToString(" ")
}
