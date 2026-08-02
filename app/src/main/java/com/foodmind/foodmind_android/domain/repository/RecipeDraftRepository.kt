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
    )

    private const val PREFERENCES = "foodmind.recipe-drafts.v1"
}
