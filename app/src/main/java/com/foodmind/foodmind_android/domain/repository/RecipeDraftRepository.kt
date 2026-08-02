package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.UserRecipeRequest
import java.util.UUID

data class RecipeDraft(
    val id: String,
    val name: String,
    val servings: Int,
    val minutes: Int,
    val version: Long = 0,
    val ingredients: List<String> = listOf("请补充食材"),
    val steps: List<String> = listOf("请补充步骤"),
    val tags: List<String> = emptyList(),
    val allergenHints: List<String> = emptyList(),
    val imageUrl: String? = null,
)

/** Local fixture boundary until backend contract C-08 exposes owner-scoped recipe CRUD. */
object RecipeDraftStore {
    private val drafts = mutableListOf(
        RecipeDraft("salmon", "姜味味噌三文鱼饭", 4, 28),
        RecipeDraft("noodle", "姜葱豆腐拌面", 2, 20),
        RecipeDraft("shakshuka", "番茄扁豆北非蛋", 4, 30),
    )

    @Synchronized fun list(): List<RecipeDraft> = drafts.toList()

    @Synchronized fun find(id: String): RecipeDraft? = drafts.firstOrNull { it.id == id }

    @Synchronized fun save(id: String?, name: String, servings: Int, minutes: Int): RecipeDraft {
        val previous = id?.let(::find)
        val draft = RecipeDraft(
            id = id ?: UUID.randomUUID().toString(),
            name = name,
            servings = servings,
            minutes = minutes,
            version = previous?.version ?: 0,
            ingredients = previous?.ingredients ?: listOf("请补充食材"),
            steps = previous?.steps ?: listOf("请补充步骤"),
            tags = previous?.tags ?: emptyList(),
            allergenHints = previous?.allergenHints ?: emptyList(),
            imageUrl = previous?.imageUrl,
        )
        val index = drafts.indexOfFirst { it.id == draft.id }
        if (index >= 0) drafts[index] = draft else drafts.add(draft)
        return draft
    }

    @Synchronized fun delete(id: String) { drafts.removeAll { it.id == id } }

    @Synchronized fun replaceFromRemote(remote: List<RecipeDraft>) {
        if (remote.isNotEmpty()) {
            drafts.clear()
            drafts.addAll(remote)
        }
    }
}

/** C-08 client adapter. UI can fall back to the local draft store when no backend is configured. */
class UserRecipeRepository(private val client: FoodMindApiClient) {
    suspend fun list(): List<RecipeDraft> = client.recipes().items.map { it.toDraft() }

    suspend fun create(draft: RecipeDraft): RecipeDraft = client.createRecipe(draft.toRequest()).toDraft()

    suspend fun update(draft: RecipeDraft): RecipeDraft = client.updateRecipe(draft.id, draft.version, draft.toRequest()).toDraft()

    suspend fun delete(id: String) { client.deleteRecipe(id) }
}

private fun com.foodmind.foodmind_android.core.network.UserRecipeResponse.toDraft() = RecipeDraft(
    id = id ?: error("Recipe API returned an id-less recipe"),
    name = name,
    servings = servings,
    minutes = 30,
    version = version,
    ingredients = ingredients,
    steps = steps,
    tags = tags,
    allergenHints = allergenHints,
    imageUrl = imageUrl,
)

private fun RecipeDraft.toRequest() = UserRecipeRequest(
    name = name,
    servings = servings,
    imageUrl = imageUrl,
    tags = tags,
    allergenHints = allergenHints,
    ingredients = ingredients.filter { it.isNotBlank() },
    steps = steps.filter { it.isNotBlank() },
)
