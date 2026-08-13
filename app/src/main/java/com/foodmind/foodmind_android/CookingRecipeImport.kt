package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.core.network.RecipeImportAnswerRequest
import com.foodmind.foodmind_android.core.network.RecipeImportResponse
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RecipeImportComposerScreen(
    client: FoodMindApiClient,
    onBack: () -> Unit,
    onImportCreated: (String) -> Unit,
) {
    val sourceText = rememberTextFieldState()
    val sourceScrollState = rememberScrollState()
    var activeImportId by rememberSaveable { mutableStateOf<String?>(null) }
    var submittedText by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    FoodMindDetailScaffold("Add recipes", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("RECIPES · AGENT", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Describe the recipes you want to add.", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Write in any language. FoodMind converts structured recipe fields to English and saves them to your account only after confirmation.",
                    color = FoodMindMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            item {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        state = sourceText,
                        label = { Text("One or more recipes") },
                        placeholder = { Text("Paste a full recipe in English, Chinese, or another language…") },
                        lineLimits = TextFieldLineLimits.MultiLine(
                            minHeightInLines = 8,
                            maxHeightInLines = 12,
                        ),
                        scrollState = sourceScrollState,
                        supportingText = {
                            Text(
                                if (sourceScrollState.maxValue > 0) "Scroll to review · ${sourceText.text.length} characters"
                                else "${sourceText.text.length} characters",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RecipeInputScrollbar(
                        scrollState = sourceScrollState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 22.dp, bottom = 36.dp, end = 6.dp),
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        val value = sourceText.text.toString().trim()
                        if (value.isBlank()) {
                            error = "Enter at least one recipe before continuing."
                        } else if (canResumeRecipeImport(activeImportId, submittedText, value)) {
                            onImportCreated(activeImportId!!)
                        } else {
                            scope.launch {
                                busy = true
                                error = null
                                runCatching { client.createRecipeImport(value) }
                                    .onSuccess {
                                        activeImportId = it.importId
                                        submittedText = value
                                        onImportCreated(it.importId)
                                    }
                                    .onFailure { error = friendlyRecipeImportError(it) }
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val canResume = canResumeRecipeImport(activeImportId, submittedText, sourceText.text.toString())
                    Text(if (busy) "Creating import…" else if (canResume) "Return to review" else "Continue to review")
                }
            }
            error?.let { item { Text(it, color = FoodMindCoral) } }
        }
    }
}

@Composable
internal fun RecipeImportSessionScreen(
    client: FoodMindApiClient,
    importId: String,
    onBack: () -> Unit,
    onCompleted: (List<String>) -> Unit,
) {
    var session by remember { mutableStateOf<RecipeImportResponse?>(null) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(importId, reload) {
        error = null
        runCatching { client.recipeImport(importId) }
            .onSuccess { session = it }
            .onFailure { error = friendlyCookingError(it, "Could not load this recipe import.") }
    }

    LaunchedEffect(session?.importId, session?.version, session?.status) {
        val current = session ?: return@LaunchedEffect
        answers = current.questions.associate { question ->
            question.questionId to (current.answers.firstOrNull { it.questionId == question.questionId }?.value
                ?: question.suggestedValue.orEmpty())
        }
        if (current.status == "PROCESSING") {
            delay(1200)
            runCatching { client.recipeImport(current.importId) }
                .onSuccess { session = it }
                .onFailure { error = friendlyCookingError(it, "Could not refresh this import.") }
        }
    }

    fun runAction(action: suspend () -> RecipeImportResponse) {
        scope.launch {
            busy = true
            error = null
            runCatching { action() }
                .onSuccess { updated ->
                    session = updated
                    if (updated.status == "COMPLETED") onCompleted(updated.createdRecipes.map { it.id })
                }
                .onFailure { error = friendlyRecipeImportError(it) }
            busy = false
        }
    }

    FoodMindDetailScaffold("Review import", onBack) { padding ->
        val current = session
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("STEP 2 OF 2 · AGENT REVIEW", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(recipeImportSessionHeading(current?.status), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Review the structured recipes, answer anything still missing, then confirm before FoodMind saves them.",
                    color = FoodMindMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (current == null && error == null) {
                item { CircularProgressIndicator() }
            }
            if (current == null && error != null) {
                item { Text(error.orEmpty(), color = FoodMindCoral) }
                item { Button(onClick = { reload++ }, modifier = Modifier.fillMaxWidth()) { Text("Try again") } }
                item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to recipe text") } }
            }
            current?.let { value ->
                if (value.status == "PROCESSING") item {
                    FoodMindSurfaceCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator()
                            Text("FoodMind is structuring your recipes. This page updates automatically.", color = FoodMindMuted)
                        }
                    }
                }
                items(value.drafts, key = { it.draftId }) { draft ->
                    FoodMindSurfaceCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(draft.name ?: "Dish name needed", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(draft.servings?.let { "$it servings" }, "${draft.ingredients.size} ingredients", "${draft.steps.size} steps").joinToString(" · "),
                                color = FoodMindGreen,
                                fontSize = 12.sp,
                            )
                            Text(draft.ingredients.take(3).joinToString(" · ").ifBlank { "Ingredients needed" }, color = FoodMindMuted, fontSize = 12.sp)
                        }
                    }
                }
                if (value.status == "NEEDS_CLARIFICATION") {
                    items(value.questions, key = { it.questionId }) { question ->
                        OutlinedTextField(
                            value = answers[question.questionId].orEmpty(),
                            onValueChange = { answers = answers + (question.questionId to it) },
                            label = { Text(question.prompt) },
                            minLines = if (question.fieldPath in setOf("ingredients", "steps")) 4 else 1,
                            maxLines = if (question.fieldPath in setOf("ingredients", "steps")) 8 else 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                val values = value.questions.map { RecipeImportAnswerRequest(it.questionId, answers[it.questionId].orEmpty().trim()) }
                                if (values.any { it.value.isBlank() }) error = "Answer every required question before continuing."
                                else runAction { client.answerRecipeImport(value.importId, value.version, values) }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (busy) "Checking answers…" else "Continue") }
                    }
                }
                if (value.status == "READY") item {
                    Button(
                        onClick = { runAction { client.confirmRecipeImport(value.importId, value.version) } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "Saving recipes…" else "Save recipes and choose for cooking") }
                }
                if (value.status == "FAILED") {
                    item { Text(value.failureMessage ?: "This recipe import could not be completed.", color = FoodMindCoral) }
                    item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Edit recipe text and start again") } }
                }
            }
            if (current != null) error?.let { item { Text(it, color = FoodMindCoral) } }
        }
    }
}

internal fun recipeImportSessionHeading(status: String?): String = when (status) {
    "NEEDS_CLARIFICATION" -> "A few details will finish these recipes."
    "READY" -> "Review recipes before saving."
    "COMPLETED" -> "Recipes saved."
    "FAILED" -> "This import needs another try."
    else -> "Structuring your recipes."
}

internal fun canResumeRecipeImport(activeImportId: String?, submittedText: String?, currentText: String): Boolean =
    !activeImportId.isNullOrBlank() && submittedText == currentText.trim()

class CookingRecipeImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        val importId = intent.getStringExtra(EXTRA_IMPORT_ID).orEmpty()
        if (importId.isBlank()) {
            finish()
            return
        }
        val client = foodMindApiClient()
        setContent {
            FoodMindTheme {
                RecipeImportSessionScreen(
                    client = client,
                    importId = importId,
                    onBack = ::finish,
                    onCompleted = { recipeIds ->
                        setResult(
                            RESULT_OK,
                            Intent().putStringArrayListExtra(
                                CookingHomeActivity.EXTRA_SELECTED_RECIPE_IDS,
                                ArrayList(recipeIds),
                            ),
                        )
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_IMPORT_ID = "recipe_import_id"

        fun intent(context: Context, importId: String): Intent =
            Intent(context, CookingRecipeImportActivity::class.java).putExtra(EXTRA_IMPORT_ID, importId)
    }
}

@Composable
private fun RecipeInputScrollbar(scrollState: ScrollState, modifier: Modifier = Modifier) {
    if (scrollState.maxValue <= 0) return
    Canvas(modifier.size(width = 3.dp, height = 180.dp)) {
        val viewport = scrollState.viewportSize.coerceAtLeast(1)
        val content = viewport + scrollState.maxValue
        val thumbHeight = (size.height * viewport / content).coerceAtLeast(28.dp.toPx())
        val travel = (size.height - thumbHeight).coerceAtLeast(0f)
        val thumbTop = travel * scrollState.value / scrollState.maxValue.coerceAtLeast(1)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.12f),
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f),
        )
        drawRoundRect(
            color = FoodMindGreen,
            topLeft = Offset(0f, thumbTop),
            size = Size(size.width, thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f),
        )
    }
}

internal fun friendlyCookingError(failure: Throwable, fallback: String): String {
    val code = (failure as? HttpException)?.code()
    return when (code) {
        401 -> "Your session has expired. Sign in again to continue."
        409 -> "This item changed on another screen. Reload it and try again."
        422 -> "The Agent could not structure this recipe. Keep the dish name, ingredients, and steps in the text, then try again."
        502, 503 -> "The Cooking Agent is temporarily unavailable. Your recipe text is still here—try again in a moment."
        504 -> "The Cooking Agent took too long to respond. Your recipe text is still here—please try again."
        else -> fallback
    }
}

/**
 * Keeps recipe-import failures actionable without exposing server internals.
 * The composer deliberately keeps the entered text in place so retrying is
 * safe after either network or server-side failures.
 */
internal fun friendlyRecipeImportError(failure: Throwable): String {
    val sharedMessage = friendlyCookingError(failure, "")
    if (sharedMessage.isNotBlank()) return sharedMessage

    return when (failure) {
        is IOException -> "FoodMind could not reach the recipe service. Your text is still here—check your connection and try again."
        is HttpException -> "FoodMind could not import this recipe right now (server error ${failure.code()}). Your text is still here—please try again."
        else -> "The recipe import stopped before it could finish. Your text is still here—please try again."
    }
}
