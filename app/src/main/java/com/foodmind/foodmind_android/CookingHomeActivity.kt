package com.foodmind.foodmind_android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.core.network.GenerateCookingPlanRequest
import com.foodmind.foodmind_android.core.network.UserRecipeResponse
import com.foodmind.foodmind_android.domain.repository.AsyncSubmitResult
import com.foodmind.foodmind_android.domain.repository.CookingPlanTaskRepository

/**
 * Cook mode selection home — ported from foodmind-cooking-app's CookingHomeScreen.
 * Photo cards + search / category + a bottom generate dock; generation sends
 * scaled structured ingredient lines to the real /cooking-plans backend.
 */
class CookingHomeActivity : ComponentActivity() {
    private val addRecipeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringArrayListExtra(EXTRA_SELECTED_RECIPE_IDS)?.let {
                intent.putStringArrayListExtra(EXTRA_SELECTED_RECIPE_IDS, it)
            }
            recreate()
        }
    }
    private val editRecipeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) recreate()
    }
    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) recreate() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        if (FoodMindSession.tokenStore.accessToken().isNullOrBlank() && FoodMindSession.tokenStore.refreshToken().isNullOrBlank()) {
            authLauncher.launch(Intent(this, LoginActivity::class.java))
        }
        val client = foodMindApiClient()
        setContent { FoodMindTheme {
            CookingHomeScreen(
                client, ::finish,
                preselectedIds = intent.getStringArrayListExtra(EXTRA_SELECTED_RECIPE_IDS).orEmpty().toSet(),
                onOpenPlan = { startActivity(CookingPlanDetailActivity.intent(this, it)) },
                onAdd = { addRecipeLauncher.launch(Intent(this, CookingAddRecipeActivity::class.java)) },
                onEditRecipe = { editRecipeLauncher.launch(CookingRecipeEditorActivity.intent(this, it)) },
                onShopping = { startActivity(Intent(this, ShoppingListsActivity::class.java)) },
                onInventory = { startActivity(Intent(this, CookingInventoryActivity::class.java)) },
                onPlans = { startActivity(Intent(this, CookingPlansActivity::class.java)) },
                onSettings = { startActivity(Intent(this, CookingSettingsActivity::class.java)) },
                onAuthRequired = { authLauncher.launch(Intent(this, LoginActivity::class.java)) },
            )
        } }
    }

    companion object { const val EXTRA_SELECTED_RECIPE_IDS = "selected_recipe_ids" }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CookingHomeScreen(
    client: FoodMindApiClient,
    onBack: () -> Unit,
    preselectedIds: Set<String>,
    onOpenPlan: (String) -> Unit,
    onAdd: () -> Unit,
    onEditRecipe: (String) -> Unit,
    onShopping: () -> Unit,
    onInventory: () -> Unit,
    onPlans: () -> Unit,
    onSettings: () -> Unit,
    onAuthRequired: () -> Unit,
) {
    var recipes by remember { mutableStateOf<List<UserRecipeResponse>>(emptyList()) }
    var loadingRecipes by remember { mutableStateOf(true) }
    var recipeLoadError by remember { mutableStateOf<String?>(null) }
    var recipeReload by remember { mutableStateOf(0) }
    val context = LocalContext.current
    var selected by remember(preselectedIds) { mutableStateOf(preselectedIds) }
    var query by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("2") }
    var servingsTouched by remember { mutableStateOf(false) }
    var maxMinutes by remember { mutableStateOf("") }
    var showPlanOptions by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var asyncRunning by remember { mutableStateOf(false) }
    var asyncError by remember { mutableStateOf<String?>(null) }
    var asyncToken by remember { mutableStateOf(0) }
    val taskRepo = remember(client) {
        CookingPlanTaskRepository(
            submitAsync = { client.generateCookingPlanAsync(it) },
            getTask = { client.cookingPlanTask(it) },
            readPlan = { client.cookingPlan(it) },
            cancelTask = { client.cancelCookingPlanTask(it) },
        )
    }

    LaunchedEffect(recipeReload) {
        loadingRecipes = true
        recipeLoadError = null
        runCatching { client.userRecipes().items }
            .onSuccess { loaded ->
                recipes = loaded
                selected = selected.intersect(loaded.map(UserRecipeResponse::id).toSet())
            }
            .onFailure {
                recipeLoadError = friendlyCookingError(it, "Could not load recipes from the backend.")
                if ((it as? retrofit2.HttpException)?.code() == 401) onAuthRequired()
            }
        loadingRecipes = false
    }

    val visible = recipes.filter { recipe ->
        listOf(recipe.name, recipe.ingredients.joinToString(" "), recipe.tags.joinToString(" "))
            .joinToString(" ").contains(query.trim(), ignoreCase = true)
    }
    val chosen = recipes.filter { it.id in selected }
    val targetServings = servings.toIntOrNull()?.coerceIn(1, 24) ?: 2
    LaunchedEffect(selected, recipes, servingsTouched) {
        if (!servingsTouched) servings = (chosen.maxOfOrNull(UserRecipeResponse::servings) ?: 2).toString()
    }

    LaunchedEffect(asyncToken) {
        if (asyncToken == 0) return@LaunchedEffect
        asyncRunning = true; asyncError = null
        val preferences = CookingPreferencesStore.load(context)
        taskRepo.generateAsync(GenerateCookingPlanRequest(
            recipeIds = chosen.map(UserRecipeResponse::id),
            servings = targetServings,
            maxMinutes = maxMinutes.toIntOrNull(),
            region = preferences.region,
            requiredDietaryTagCodes = preferences.requiredDietaryTagCodes.toList(),
            avoidAllergenCodes = preferences.avoidAllergenCodes.toList(),
        )).onSuccess { accepted ->
            val planId = accepted.planId
            if (accepted is AsyncSubmitResult.Accepted && planId != null) {
                onOpenPlan(planId)
            } else {
                val failedStatus = (accepted as? AsyncSubmitResult.TerminalFailed)?.status ?: "unknown"
                asyncError = "Background submission failed ($failedStatus). Adjust the selection and retry."
            }
        }.onFailure {
            asyncError = friendlyCookingError(it, "Could not submit background generation. Check your network.")
            if ((it as? retrofit2.HttpException)?.code() == 401) onAuthRequired()
        }
        asyncRunning = false
    }

    val toggle = { id: String -> selected = if (id in selected) selected - id else selected + id }

    if (showPlanOptions) {
        ModalBottomSheet(onDismissRequest = { showPlanOptions = false }) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Plan options", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "These apply to the whole cooking plan. FoodMind uses each recipe's base servings when you have not changed them.",
                    color = FoodMindMuted,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    servings,
                    { servings = it.filter(Char::isDigit); servingsTouched = true },
                    label = { Text("Target servings") },
                    supportingText = { Text("1–24 servings") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    maxMinutes,
                    { maxMinutes = it.filter(Char::isDigit) },
                    label = { Text("Time limit (optional)") },
                    supportingText = { Text("Leave blank when there is no deadline") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(onClick = { showPlanOptions = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }

    FoodMindDetailScaffold("Cooking", onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = if (chosen.isEmpty()) 24.dp else 125.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Column {
                Text("COOK MODE · RECIPE SELECTION", color = FoodMindGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("What do you want to cook tonight?", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Choose account recipes. FoodMind checks current inventory before building one safe, ordered plan.", color = FoodMindMuted, modifier = Modifier.padding(top = 6.dp))
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Add recipes with Agent")
                }
                CookingPathTabs(onShopping, onInventory, onPlans, onSettings)
                OutlinedTextField(
                    query,
                    { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("Search recipes") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true,
                )
                } }
            if (loadingRecipes) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("Loading recipes…", color = FoodMindMuted, modifier = Modifier.padding(vertical = 20.dp)) }
            } else if (recipeLoadError != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { Column(Modifier.padding(vertical = 20.dp)) {
                    Text(recipeLoadError.orEmpty(), color = FoodMindCoral)
                    OutlinedButton(onClick = { recipeReload++ }) { Text("Try again") }
                } }
            } else if (visible.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Column(Modifier.padding(vertical = 20.dp)) {
                    Text("No recipes available", fontWeight = FontWeight.Bold)
                    Text("Import a recipe to your account before generating a Cooking Plan.", color = FoodMindMuted, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = onAdd, modifier = Modifier.padding(top = 12.dp)) { Text("Add recipe") }
                } }
            }
                items(visible, key = UserRecipeResponse::id) { recipe ->
                    RecipePhotoCard(recipe, recipe.id in selected, { toggle(recipe.id) }, { onEditRecipe(recipe.id) })
                }
            }
            if (chosen.isNotEmpty()) {
                FoodMindSurfaceCard(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${chosen.size} dishes selected", fontWeight = FontWeight.Bold)
                                Text(
                                    "$targetServings servings · ${maxMinutes.toIntOrNull()?.let { "$it min limit" } ?: "no time limit"}",
                                    color = FoodMindMuted,
                                    fontSize = 11.sp,
                                )
                            }
                            TextButton(onClick = { showPlanOptions = true }) { Text("Plan options") }
                        }
                        error?.let { Text(it, color = FoodMindCoral) }
                        asyncError?.let { Text(it, color = FoodMindCoral) }
                        Button(onClick = {
                            error = null; asyncToken++
                        }, enabled = !asyncRunning, modifier = Modifier.fillMaxWidth()) {
                            Text(if (asyncRunning) "Submitting to Cooking Agent…" else "Generate plan  →")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipePhotoCard(recipe: UserRecipeResponse, checked: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) {
    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (checked) Color(0xFF192118) else FoodMindSurface),
        border = BorderStroke(if (checked) 2.dp else 1.dp, if (checked) FoodMindLime else FoodMindLineSoft),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(104.dp).background(Color(0xFF202A20)), contentAlignment = Alignment.Center) {
                if (!recipe.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = recipe.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Outlined.RestaurantMenu, null, tint = FoodMindGreen, modifier = Modifier.size(34.dp))
                }
                Text(
                    recipe.tags.firstOrNull() ?: "Recipe",
                    color = Color(0xFF11170F),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(9.dp)
                        .background(Color(0xDDF4F6F2), RoundedCornerShape(99.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
                if (checked) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(9.dp).size(32.dp).background(FoodMindLime, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Check, null, tint = Color(0xFF11170F), modifier = Modifier.size(17.dp)) }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text("${recipe.ingredients.size} INGREDIENTS · ${recipe.steps.size} STEPS", color = FoodMindGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                Text(recipe.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                Text("Base ${recipe.servings} servings · Backend recipe", color = FoodMindMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                Text(recipe.ingredients.take(3).joinToString(" · "), color = FoodMindFaint, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit") }
            }
        }
    }
}

@Composable
private fun CookingPathTabs(onShopping: () -> Unit, onInventory: () -> Unit, onPlans: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp)
            .background(FoodMindSurface, RoundedCornerShape(14.dp)).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = {}, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                Text("Choose recipes", fontSize = 11.sp)
            }
            OutlinedButton(onClick = onShopping, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                Text("Shopping lists", fontSize = 11.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = onInventory, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                Text("Inventory", fontSize = 11.sp)
            }
            OutlinedButton(onClick = onPlans, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                Text("History", fontSize = 11.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                Icon(Icons.Outlined.Tune, null, Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Settings", fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Agent-backed multilingual recipe import, matching the Web workflow. */
class CookingAddRecipeActivity : ComponentActivity() {
    private val reviewLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK, result.data)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        val client = foodMindApiClient()
        setContent {
            FoodMindTheme {
                RecipeImportComposerScreen(
                    client = client,
                    onBack = ::finish,
                    onImportCreated = { importId ->
                        reviewLauncher.launch(CookingRecipeImportActivity.intent(this, importId))
                    },
                )
            }
        }
    }
}
