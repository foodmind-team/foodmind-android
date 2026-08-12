package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.core.network.UserRecipeRequest
import com.foodmind.foodmind_android.core.network.UserRecipeResponse
import kotlinx.coroutines.launch

class CookingRecipeEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        val id = intent.getStringExtra(EXTRA_RECIPE_ID).orEmpty()
        val done = { setResult(RESULT_OK); finish() }
        setContent { FoodMindTheme { CookingRecipeEditorScreen(foodMindApiClient(), id, ::finish, done) } }
    }

    companion object {
        private const val EXTRA_RECIPE_ID = "recipe_id"
        fun intent(context: Context, recipeId: String) =
            Intent(context, CookingRecipeEditorActivity::class.java).putExtra(EXTRA_RECIPE_ID, recipeId)
    }
}

@Composable
private fun CookingRecipeEditorScreen(client: FoodMindApiClient, recipeId: String, onBack: () -> Unit, onDone: () -> Unit) {
    var recipe by remember { mutableStateOf<UserRecipeResponse?>(null) }
    var name by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("2") }
    var imageUrl by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var allergens by remember { mutableStateOf("") }
    var ingredients by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(recipeId) {
        runCatching { client.userRecipe(recipeId) }
            .onSuccess {
                recipe = it; name = it.name; servings = it.servings.toString(); imageUrl = it.imageUrl.orEmpty()
                tags = it.tags.joinToString("\n"); allergens = it.allergenHints.joinToString("\n")
                ingredients = it.ingredients.joinToString("\n"); steps = it.steps.joinToString("\n")
                error = null
            }
            .onFailure { error = friendlyCookingError(it, "Could not load this recipe.") }
        loading = false
    }

    fun lines(value: String) = value.lines().map(String::trim).filter(String::isNotBlank)
    val valid = name.isNotBlank() && servings.toIntOrNull()?.let { it in 1..50 } == true && lines(ingredients).isNotEmpty() && lines(steps).isNotEmpty()
    FoodMindDetailScaffold("Edit recipe", onBack) { padding ->
        if (loading) CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
        else LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("BACKEND RECIPE", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Edit recipe.", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Changes are saved to your account and used by future Cooking Plans.", color = FoodMindMuted)
            }
            item { OutlinedTextField(name, { name = it.take(160) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(servings, { servings = it.filter(Char::isDigit) }, label = { Text("Base servings") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(imageUrl, { imageUrl = it }, label = { Text("Image URL (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(tags, { tags = it }, label = { Text("Tags, one per line") }, minLines = 3, modifier = Modifier.weight(1f))
                OutlinedTextField(allergens, { allergens = it }, label = { Text("Allergen hints") }, minLines = 3, modifier = Modifier.weight(1f))
            } }
            item { OutlinedTextField(ingredients, { ingredients = it }, label = { Text("Ingredients, one per line") }, minLines = 7, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(steps, { steps = it }, label = { Text("Steps, one per line") }, minLines = 7, modifier = Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = FoodMindCoral) } }
            item {
                Button(onClick = {
                    val current = recipe ?: return@Button
                    scope.launch {
                        busy = true; error = null
                        val request = UserRecipeRequest(
                            name = name.trim(), servings = servings.toInt(), imageUrl = imageUrl.trim().ifBlank { null },
                            tags = lines(tags), allergenHints = lines(allergens), ingredients = lines(ingredients), steps = lines(steps),
                        )
                        runCatching { client.updateUserRecipe(current.id, current.version, request) }
                            .onSuccess { onDone() }
                            .onFailure { error = friendlyCookingError(it, "Could not save this recipe. Reload and try again.") }
                        busy = false
                    }
                }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Saving…" else "Save recipe") }
            }
            item {
                OutlinedButton(onClick = {
                    scope.launch {
                        busy = true; error = null
                        runCatching { client.deleteUserRecipe(recipeId) }
                            .onSuccess { onDone() }
                            .onFailure { error = friendlyCookingError(it, "Could not delete this recipe.") }
                        busy = false
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Delete recipe") }
            }
        }
    }
}
