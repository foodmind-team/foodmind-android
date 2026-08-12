package com.foodmind.foodmind_android.domain.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class RecipeDraft(
    val id: String,
    val name: String,
    val servings: Int,
    val minutes: Int,
    val category: String = "Home cooking",
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val allergenHints: List<String> = emptyList(),
    val imageUrl: String? = null,
    val text: String = "",
)

/**
 * Account-scoped, device-local recipe storage.
 *
 * The backend OpenAPI contract has no recipe CRUD endpoints. Keeping drafts here mirrors the web
 * client and prevents the UI from claiming server persistence that does not exist.
 */
object RecipeDraftStore {
    private val gson = Gson()
    private var preferences: android.content.SharedPreferences? = null
    private var storageKey = "recipes:guest"
    private var drafts = seedDrafts().toMutableList()

    @Synchronized
    fun initialize(context: Context, userId: String?) {
        preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        storageKey = "recipes:${userId?.takeIf(String::isNotBlank) ?: "guest"}"
        val json = preferences?.getString(storageKey, null)
        drafts = if (json.isNullOrBlank()) {
            seedDrafts().toMutableList().also(::persist)
        } else {
            runCatching {
                gson.fromJson<List<RecipeDraft>>(json, object : TypeToken<List<RecipeDraft>>() {}.type)
                    .orEmpty().toMutableList()
            }.getOrElse { seedDrafts().toMutableList() }
        }
    }

    @Synchronized fun list(): List<RecipeDraft> = drafts.toList()
    @Synchronized fun find(id: String): RecipeDraft? = drafts.firstOrNull { it.id == id }

    @Synchronized
    fun save(
        id: String?,
        name: String,
        servings: Int,
        minutes: Int,
        category: String = "Home cooking",
        ingredients: List<String>? = null,
        steps: List<String>? = null,
        tags: List<String>? = null,
        allergenHints: List<String>? = null,
        imageUrl: String? = null,
        text: String? = null,
    ): RecipeDraft {
        val previous = id?.let(::find)
        val draft = RecipeDraft(
            id = id ?: UUID.randomUUID().toString(),
            name = name,
            servings = servings,
            minutes = minutes,
            category = category,
            ingredients = ingredients ?: previous?.ingredients.orEmpty(),
            steps = steps ?: previous?.steps.orEmpty(),
            tags = tags ?: previous?.tags.orEmpty(),
            allergenHints = allergenHints ?: previous?.allergenHints.orEmpty(),
            imageUrl = imageUrl ?: previous?.imageUrl,
            text = text ?: previous?.text.orEmpty(),
        )
        val index = drafts.indexOfFirst { it.id == draft.id }
        if (index >= 0) drafts[index] = draft else drafts.add(draft)
        persist(drafts)
        return draft
    }

    @Synchronized
    fun delete(id: String) {
        drafts.removeAll { it.id == id }
        persist(drafts)
    }

    private fun persist(value: List<RecipeDraft>) {
        preferences?.edit()?.putString(storageKey, gson.toJson(value))?.apply()
    }

    private fun seedDrafts() = listOf(
        RecipeDraft(
            id = "salmon",
            name = "Ginger miso salmon bowl",
            servings = 4,
            minutes = 28,
            category = "Dinner",
            ingredients = listOf("Salmon 500 g", "Cooked rice 4 bowls", "Miso 2 tbsp", "Ginger 15 g"),
            steps = listOf("Mix the miso and grated ginger.", "Pan-sear the salmon and brush with sauce.", "Serve with warm rice."),
            tags = listOf("High protein", "Quick"),
        ),
        RecipeDraft(
            id = "noodle",
            name = "Ginger scallion tofu noodles",
            servings = 2,
            minutes = 20,
            category = "Noodles",
            ingredients = listOf("Noodles 220 g", "Firm tofu 300 g", "Ginger 10 g", "Spring onions 3"),
            steps = listOf("Cook and drain the noodles.", "Sauté the tofu, ginger, and spring onions.", "Toss together and serve immediately."),
            tags = listOf("Vegetarian"),
        ),
        RecipeDraft(
            id = "shakshuka",
            name = "Tomato lentil shakshuka",
            servings = 4,
            minutes = 30,
            category = "Brunch",
            ingredients = listOf("Tomatoes 600 g", "Cooked lentils 250 g", "Eggs 4"),
            steps = listOf("Cook the tomatoes and lentils until thick.", "Crack in the eggs, cover, and cook through."),
            tags = listOf("One pot"),
        ),
        // The four Cook-mode dishes ported from foodmind-cooking-app (with photos).
        RecipeDraft(
            id = "r-tomato-eggs",
            name = "Scrambled Eggs with Tomato",
            servings = 2,
            minutes = 15,
            category = "Quick",
            ingredients = listOf("tomatoes 2", "eggs 3", "scallion 1", "sugar 0.5 tsp", "cooking oil 2 tbsp"),
            steps = listOf("Cut tomatoes into wedges and beat the eggs.", "Scramble the eggs on high heat, then remove.", "Stir-fry the tomatoes until juicy and season.", "Return the eggs, toss quickly and serve."),
            tags = listOf("Quick", "Stir-fry"),
            imageUrl = "file:///android_asset/recipes/tomato-eggs.png",
            text = "Scrambled Eggs with Tomato, 2 servings\n\nIngredients:\n2 tomatoes, 3 eggs, 1 scallion, salt to taste, half tsp sugar, 2 tbsp cooking oil.\n\nSteps:\n1. Cut tomatoes into wedges, beat eggs with a pinch of salt, chop the scallion.\n2. Heat the wok with oil, scramble the eggs on high heat until set, then remove.\n3. Add a little oil, stir-fry the tomatoes until juicy, season with salt and sugar.\n4. Return the eggs, toss quickly, sprinkle scallion and serve.",
        ),
        RecipeDraft(
            id = "r-garlic-prawn",
            name = "Garlic Prawns",
            servings = 2,
            minutes = 18,
            category = "Home-style",
            ingredients = listOf("prawns 300 g", "minced garlic 20 g", "butter 15 g", "olive oil 10 ml"),
            steps = listOf("Devein the prawns, pat dry, and season.", "Melt the butter with olive oil and fry the garlic.", "Pan-fry the prawns until curled and pink.", "Sprinkle parsley before serving."),
            tags = listOf("Pan-fry", "Main"),
            imageUrl = "file:///android_asset/recipes/garlic-prawn.png",
            text = "Garlic Prawns, 2 servings\n\nIngredients: 300g prawns, 20g minced garlic, 15g butter, 10ml olive oil, a pinch of parsley, black pepper and sea salt to taste.\n\nSteps:\n1. Devein the prawns, pat dry, and season with salt and black pepper.\n2. Melt the butter with olive oil in the pan, gently fry the garlic on low heat.\n3. Turn to medium heat and pan-fry the prawns until curled and pink.\n4. Sprinkle parsley before serving.",
        ),
        RecipeDraft(
            id = "r-corn-rib-soup",
            name = "Corn & Rib Soup",
            servings = 4,
            minutes = 55,
            category = "Soup",
            ingredients = listOf("pork ribs 500 g", "sweet corn 2", "carrot 1", "ginger 3 slices", "cooking wine 1 tbsp"),
            steps = listOf("Blanch the ribs in cold water and drain.", "Simmer the ribs with ginger and wine.", "Add the corn and carrot after 30 minutes.", "Simmer another 20 minutes and season."),
            tags = listOf("Slow-cooked", "Soup"),
            imageUrl = "file:///android_asset/recipes/corn-rib-soup.png",
            text = "Corn & Rib Soup, 4 servings\n\nIngredients: 500g pork ribs, 2 sweet corns, 1 carrot, 3 slices ginger, 1 tbsp cooking wine, salt to taste.\n\nSteps:\n1. Blanch the ribs in cold water, skim the foam and drain.\n2. Add plenty of hot water to the pot with ribs, ginger and wine; bring to a boil, then simmer.\n3. Cut the corn into chunks and the carrot into chunks; add after 30 minutes.\n4. Simmer for another 20 minutes, season with salt.",
        ),
        RecipeDraft(
            id = "r-torn-cabbage",
            name = "Torn Cabbage Stir-fry",
            servings = 2,
            minutes = 12,
            category = "Quick",
            ingredients = listOf("cabbage 400 g", "dried chilies 4", "garlic 3 cloves", "light soy sauce 1 tbsp", "vinegar 1 tsp"),
            steps = listOf("Tear the cabbage and slice the garlic.", "Bloom the garlic and chili on high heat.", "Stir-fry the cabbage until just tender.", "Splash soy sauce and vinegar, season and serve."),
            tags = listOf("High-heat", "Side"),
            imageUrl = "file:///android_asset/recipes/torn-cabbage.png",
            text = "Torn Cabbage Stir-fry, 2 servings\n\nIngredients: 400g cabbage, 4 dried chilies, 3 garlic cloves, 1 tbsp light soy sauce, 1 tsp vinegar, salt to taste, 2 tbsp cooking oil.\n\nSteps:\n1. Tear the cabbage into large pieces, slice the garlic, snip the chilies.\n2. Heat the wok on high with oil, bloom the garlic and chili.\n3. Add the cabbage and stir-fry on high heat until just tender.\n4. Splash soy sauce and vinegar along the edge, season and serve.",
        ),
    )

    private const val PREFERENCES = "foodmind.recipe-drafts.v1"
}
