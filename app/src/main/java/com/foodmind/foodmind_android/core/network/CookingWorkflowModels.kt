package com.foodmind.foodmind_android.core.network

data class CreateRecipeImportRequest(val text: String)
data class RecipeImportAnswerRequest(val questionId: String, val value: String)
data class RecipeImportAnswersRequest(val answers: List<RecipeImportAnswerRequest>)
data class RecipeImportDraft(
    val draftId: String,
    val name: String? = null,
    val servings: Int? = null,
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
)
data class RecipeImportQuestion(
    val questionId: String,
    val draftId: String,
    val fieldPath: String,
    val prompt: String,
    val responseType: String = "TEXT",
    val required: Boolean = true,
    val suggestedValue: String? = null,
)
data class RecipeImportAnswer(val questionId: String, val value: String)
data class RecipeImportResponse(
    val importId: String,
    val text: String,
    val status: String,
    val drafts: List<RecipeImportDraft> = emptyList(),
    val questions: List<RecipeImportQuestion> = emptyList(),
    val answers: List<RecipeImportAnswer> = emptyList(),
    val createdRecipes: List<UserRecipeResponse> = emptyList(),
    val failureCode: String? = null,
    val failureMessage: String? = null,
    val version: Long = 0,
)

data class ShoppingListItemResponse(
    val itemId: String,
    val sequenceNo: Int = 0,
    val ingredientName: String,
    val requiredQuantity: Double,
    val purchasedQuantity: Double? = null,
    val unit: String,
    val expiryDate: String? = null,
    val checked: Boolean = false,
    val version: Long = 0,
)
data class ShoppingListResponse(
    val shoppingListId: String,
    val sourcePlanId: String,
    val rootPlanId: String,
    val originalServings: Int,
    val continuationPlanId: String? = null,
    val status: String,
    val checkedItemCount: Int = 0,
    val totalItemCount: Int = 0,
    val version: Long = 0,
    val items: List<ShoppingListItemResponse> = emptyList(),
)
data class ShoppingListPageResponse(
    val items: List<ShoppingListResponse> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
)
data class UpdateShoppingListItemRequest(
    val checked: Boolean,
    val purchasedQuantity: Double,
    val unit: String,
    val expiryDate: String? = null,
)

data class InventoryLotRequest(
    val ingredientName: String,
    val quantity: Double,
    val unit: String,
    val expiryDate: String? = null,
)
data class InventoryLotResponse(
    val lotId: String,
    val ingredientName: String,
    val quantity: Double,
    val reserved: Double = 0.0,
    val available: Double = 0.0,
    val unit: String,
    val expiryDate: String? = null,
    val purchasedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val version: Long = 0,
)
data class InventoryLotPageResponse(
    val items: List<InventoryLotResponse> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
)
