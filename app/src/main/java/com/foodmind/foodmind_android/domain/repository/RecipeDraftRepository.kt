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
    val category: String = "家常",
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
        category: String = "家常",
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
            name = "姜味味噌三文鱼饭",
            servings = 4,
            minutes = 28,
            category = "晚餐",
            ingredients = listOf("三文鱼 500 g", "米饭 4 碗", "味噌 2 汤匙", "生姜 15 g"),
            steps = listOf("调匀味噌与姜末。", "煎熟三文鱼并刷上酱汁。", "与热米饭一起装盘。"),
            tags = listOf("高蛋白", "快捷"),
        ),
        RecipeDraft(
            id = "noodle",
            name = "姜葱豆腐拌面",
            servings = 2,
            minutes = 20,
            category = "面食",
            ingredients = listOf("面条 220 g", "硬豆腐 300 g", "姜 10 g", "青葱 3 根"),
            steps = listOf("煮面并沥干。", "煎香豆腐、姜与青葱。", "拌匀后立即享用。"),
            tags = listOf("素食"),
        ),
        RecipeDraft(
            id = "shakshuka",
            name = "番茄扁豆北非蛋",
            servings = 4,
            minutes = 30,
            category = "早午餐",
            ingredients = listOf("番茄 600 g", "熟扁豆 250 g", "鸡蛋 4 个"),
            steps = listOf("把番茄与扁豆煮至浓稠。", "打入鸡蛋并加盖焖熟。"),
            tags = listOf("一锅料理"),
        ),
    )

    private const val PREFERENCES = "foodmind.recipe-drafts.v1"
}
