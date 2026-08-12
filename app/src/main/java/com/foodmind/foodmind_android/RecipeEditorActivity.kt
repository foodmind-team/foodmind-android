package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.UserRecipeRequest
import com.foodmind.foodmind_android.core.network.UserRecipeResponse
import com.foodmind.foodmind_android.domain.repository.UserRecipeRepository
import kotlinx.coroutines.launch

class RecipeEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_RECIPE_ID)
        val repository = UserRecipeRepository(foodMindApiClient())
        setContent { FoodMindTheme { RecipeEditorRoute(repository, id, ::finish) } }
    }

    companion object {
        private const val EXTRA_RECIPE_ID = "recipe_id"
        fun intent(context: Context, id: String) = Intent(context, RecipeEditorActivity::class.java)
            .putExtra(EXTRA_RECIPE_ID, id)
    }
}

@Composable
private fun RecipeEditorRoute(repository: UserRecipeRepository, id: String?, onBack: () -> Unit) {
    var existing by remember { mutableStateOf<UserRecipeResponse?>(null) }
    var loading by remember { mutableStateOf(id != null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        if (id != null) {
            runCatching { repository.get(id) }
                .onSuccess { existing = it }
                .onFailure { loadError = it.message ?: "Could not load recipe." }
            loading = false
        }
    }
    FoodMindDetailScaffold(if (id == null) "Add recipe" else "Edit recipe", onBack) { padding ->
        when {
            loading -> Row(Modifier.fillMaxWidth().padding(padding).padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            loadError != null -> FoodMindSurfaceCard(Modifier.padding(padding).padding(20.dp)) { Text(loadError!!, color = FoodMindCoral) }
            else -> RecipeEditorForm(existing, repository, onBack, padding)
        }
    }
}

@Composable
private fun RecipeEditorForm(
    existing: UserRecipeResponse?,
    repository: UserRecipeRepository,
    onSaved: () -> Unit,
    padding: PaddingValues,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var servings by remember(existing?.id) { mutableStateOf(existing?.servings?.toString() ?: "2") }
    var ingredients by remember(existing?.id) { mutableStateOf(existing?.ingredients?.joinToString("\n").orEmpty()) }
    var steps by remember(existing?.id) { mutableStateOf(existing?.steps?.joinToString("\n").orEmpty()) }
    var tags by remember(existing?.id) { mutableStateOf(existing?.tags?.joinToString(", ").orEmpty()) }
    var allergens by remember(existing?.id) { mutableStateOf(existing?.allergenHints?.joinToString(", ").orEmpty()) }
    var imageUrl by remember(existing?.id) { mutableStateOf(existing?.imageUrl.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val servingValue = servings.toIntOrNull()
    val ingredientLines = ingredients.lines().map(String::trim).filter(String::isNotBlank)
    val stepLines = steps.lines().map(String::trim).filter(String::isNotBlank)
    val valid = name.isNotBlank() && servingValue != null && servingValue in 1..50 && ingredientLines.isNotEmpty() && stepLines.isNotEmpty()

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            Text("Save one recipe for every FoodMind device", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text("The recipe is persisted by Backend. Enter one ingredient or step per line.", color = FoodMindMuted)
        }
        item { OutlinedTextField(name, { name = it }, label = { Text("Recipe name") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(servings, { servings = it.filter(Char::isDigit) }, label = { Text("Servings") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(ingredients, { ingredients = it }, label = { Text("Ingredients (one per line)") }, minLines = 6, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(steps, { steps = it }, label = { Text("Steps (one per line)") }, minLines = 6, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma-separated)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(allergens, { allergens = it }, label = { Text("Allergen notes (comma-separated)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(imageUrl, { imageUrl = it }, label = { Text("Image URL (optional)") }, modifier = Modifier.fillMaxWidth()) }
        error?.let { item { Text(it, color = FoodMindCoral) } }
        item {
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        val request = UserRecipeRequest(
                            name = name.trim(),
                            servings = servingValue ?: 2,
                            imageUrl = imageUrl.trim().ifBlank { null },
                            tags = tags.split(',').map(String::trim).filter(String::isNotBlank),
                            allergenHints = allergens.split(',').map(String::trim).filter(String::isNotBlank),
                            ingredients = ingredientLines,
                            steps = stepLines,
                        )
                        runCatching { repository.save(existing, request) }
                            .onSuccess { onSaved() }
                            .onFailure { error = it.message ?: "Could not save recipe." }
                        saving = false
                    }
                },
                enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Saving…" else "Save recipe") }
        }
    }
}
