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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.RecipeImportAnswerRequest
import com.foodmind.foodmind_android.core.network.RecipeImportDraft
import com.foodmind.foodmind_android.core.network.RecipeImportResponse
import kotlinx.coroutines.launch

class RecipeImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoodMindTheme {
                RecipeImportStartScreen(foodMindApiClient(), ::finish) {
                    startActivity(RecipeImportSessionActivity.intent(this, it))
                }
            }
        }
    }
}

class RecipeImportSessionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = requireNotNull(intent.getStringExtra(EXTRA_IMPORT_ID))
        setContent {
            FoodMindTheme {
                RecipeImportSessionScreen(
                    client = foodMindApiClient(),
                    importId = id,
                    onBack = ::finish,
                    onOpenRecipes = { startActivity(Intent(this, RecipeLibraryActivity::class.java)) },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_IMPORT_ID = "recipe_import_id"
        fun intent(context: Context, id: String) = Intent(context, RecipeImportSessionActivity::class.java)
            .putExtra(EXTRA_IMPORT_ID, id)
    }
}

@Composable
private fun RecipeImportStartScreen(client: FoodMindApiClient, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    FoodMindDetailScaffold("Import recipes", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item {
                Text("Describe the recipes you want to add", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Write in any language. FoodMind asks only for missing required details and saves confirmed recipes to Backend.", color = FoodMindMuted)
            }
            item { OutlinedTextField(text, { text = it }, label = { Text("Recipe text") }, minLines = 12, modifier = Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = FoodMindCoral) } }
            item {
                Button(
                    onClick = {
                        if (text.isBlank()) {
                            error = "Enter at least one recipe."
                            return@Button
                        }
                        scope.launch {
                            submitting = true
                            runCatching { client.createRecipeImport(text.trim()) }
                                .onSuccess { onCreated(it.importId) }
                                .onFailure { error = it.message ?: "Could not start recipe import." }
                            submitting = false
                        }
                    },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (submitting) "Parsing…" else "Parse recipes") }
            }
        }
    }
}

@Composable
private fun RecipeImportSessionScreen(
    client: FoodMindApiClient,
    importId: String,
    onBack: () -> Unit,
    onOpenRecipes: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<RecipeImportResponse?>(null) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(importId, refresh) {
        loading = true
        runCatching { client.recipeImport(importId) }
            .onSuccess { value ->
                session = value
                answers = value.questions.associate { question ->
                    question.questionId to (value.answers.firstOrNull { it.questionId == question.questionId }?.value
                        ?: question.suggestedValue.orEmpty())
                }
                error = null
            }
            .onFailure { error = it.message ?: "Could not load recipe import." }
        loading = false
    }
    FoodMindDetailScaffold(
        title = "Recipe import",
        onBack = onBack,
        actions = { IconButton(onClick = { refresh++ }) { Icon(Icons.Outlined.Refresh, "Refresh recipe import") } },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loading) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
            error?.let { item { Text(it, color = FoodMindCoral) } }
            session?.let { value ->
                item {
                    Text(value.status.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Import progress is persisted by Backend and can be resumed on Web or Android.", color = FoodMindMuted)
                }
                items(value.drafts, key = RecipeImportDraft::draftId) { draft ->
                    FoodMindSurfaceCard {
                        Column {
                            Text(draft.name ?: "Dish name needed", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${draft.servings ?: "?"} servings · ${draft.ingredients.size} ingredients · ${draft.steps.size} steps", color = FoodMindMuted)
                            if (draft.ingredients.isNotEmpty()) Text(draft.ingredients.take(3).joinToString(" · "), color = FoodMindMuted, fontSize = 12.sp)
                        }
                    }
                }
                if (value.status == "NEEDS_CLARIFICATION") {
                    items(value.questions, key = { it.questionId }) { question ->
                        OutlinedTextField(
                            value = answers[question.questionId].orEmpty(),
                            onValueChange = { answers = answers + (question.questionId to it) },
                            label = { Text(question.prompt) },
                            minLines = if (question.fieldPath in listOf("ingredients", "steps")) 4 else 1,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                val payload = value.questions.map { RecipeImportAnswerRequest(it.questionId, answers[it.questionId].orEmpty().trim()) }
                                if (payload.any { it.value.isBlank() }) {
                                    error = "Answer every required question."
                                    return@Button
                                }
                                scope.launch {
                                    submitting = true
                                    runCatching { client.answerRecipeImport(value.importId, value.version, payload) }
                                        .onSuccess { session = it; error = null }
                                        .onFailure { error = it.message ?: "Could not submit answers." }
                                    submitting = false
                                }
                            },
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (submitting) "Checking…" else "Submit answers") }
                    }
                }
                if (value.status == "READY") item {
                    Button(
                        onClick = {
                            scope.launch {
                                submitting = true
                                runCatching { client.confirmRecipeImport(value.importId, value.version) }
                                    .onSuccess { session = it; error = null }
                                    .onFailure { error = it.message ?: "Could not confirm recipes." }
                                submitting = false
                            }
                        },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (submitting) "Saving…" else "Confirm and save recipes") }
                }
                if (value.status == "COMPLETED") item {
                    FoodMindSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${value.createdRecipes.size} recipes saved to Backend.", fontWeight = FontWeight.Bold)
                            Button(onClick = onOpenRecipes, modifier = Modifier.fillMaxWidth()) { Text("Open cloud recipes") }
                        }
                    }
                }
                if (value.status == "FAILED") item { Text(value.failureMessage ?: "Recipe import failed.", color = FoodMindCoral) }
                if (value.status == "PROCESSING") item { OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) { Text("Refresh processing status") } }
            }
        }
    }
}
