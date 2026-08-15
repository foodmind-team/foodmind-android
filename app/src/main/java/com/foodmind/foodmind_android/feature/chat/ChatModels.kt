package com.foodmind.foodmind_android.feature.chat

import com.foodmind.foodmind_android.core.network.ChatMessageResponse
import com.foodmind.foodmind_android.core.network.ChatMessageSourceResponse
import com.foodmind.foodmind_android.core.network.ChatReferenceResponse
import com.foodmind.foodmind_android.core.network.ChatSessionResponse
import com.foodmind.foodmind_android.core.network.ExploreItemResponse

const val CHAT_MESSAGE_LIMIT = 12_000

data class PendingChatSource(
    val sourceType: String,
    val sourceId: String,
)

enum class OutgoingMessageStatus {
    SENDING,
    FAILED,
}

data class OutgoingChatMessage(
    val localId: String,
    val idempotencyKey: String,
    val content: String,
    val referenceIds: List<String>,
    val referenceTitles: List<String>,
    val status: OutgoingMessageStatus,
)

enum class ChatAnswerMode(val label: String) {
    GROUNDED("Grounded in FoodMind"),
    GENERAL("General information"),
    NAVIGATION("Navigation guidance"),
    FALLBACK("Fallback response"),
    SCOPE_BOUNDARY("Scope boundary"),
}

enum class ChatDestination(val label: String) {
    INVENTORY("Inventory"),
    SHOPPING_LISTS("Shopping lists"),
    SAVED_RECIPES("Saved recipes"),
    COOKING_PLANS("Cooking plans"),
    RECOMMENDATIONS("Recommendations"),
    EXPLORE("Explore"),
}

enum class ChatSourceFilter(
    val label: String,
    val apiValue: String?,
) {
    ALL("All", null),
    FOOD_RECORD("My records", "FOOD_RECORD"),
    FOOD_PRODUCT("Products", "FOOD_PRODUCT"),
    PLACE("Places", "PLACE"),
}

data class ChatUiState(
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val isLoadingMore: Boolean = false,
    val sessionId: String? = null,
    val title: String = "FoodMind Chat",
    val messages: List<ChatMessageResponse> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val draft: String = "",
    val attachedReferences: List<ChatReferenceResponse> = emptyList(),
    val searchQuery: String = "",
    val sourceFilter: ChatSourceFilter = ChatSourceFilter.ALL,
    val searchResults: List<ExploreItemResponse> = emptyList(),
    val outgoingMessage: OutgoingChatMessage? = null,
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val requiresLogin: Boolean = false,
)

data class ChatListUiState(
    val isLoading: Boolean = true,
    val isArchiving: Boolean = false,
    val sessions: List<ChatSessionResponse> = emptyList(),
    val query: String = "",
    val archiveCandidate: ChatSessionResponse? = null,
    val errorMessage: String? = null,
)

val CHAT_STARTER_PROMPTS = listOf(
    "Summarise my recent food records.",
    "Compare the FoodMind sources I attach.",
    "Find the place I recorded recently.",
    "Explain how to read a nutrition label.",
)

fun normaliseChatSourceType(sourceType: String): String? = when (sourceType) {
    "PLACE", "CURATED_PLACE" -> "PLACE"
    "FOOD_PRODUCT", "CURATED_PRODUCT" -> "FOOD_PRODUCT"
    "FOOD_RECORD", "GROUP_RECORD" -> "FOOD_RECORD"
    else -> null
}

fun ChatListUiState.visibleSessions(): List<ChatSessionResponse> {
    val term = query.trim()
    if (term.isEmpty()) return sessions
    return sessions.filter { session ->
        session.title.orEmpty().contains(term, ignoreCase = true) ||
            session.status.orEmpty().contains(term, ignoreCase = true)
    }
}

fun ChatUiState.conversationSources(): List<ChatMessageSourceResponse> =
    messages.asSequence()
        .filter(::isGroundedChatAnswer)
        .flatMap { it.sources.asSequence() }
        .filter { !it.sourceType.isNullOrBlank() && !it.sourceId.isNullOrBlank() }
        .distinctBy { "${it.sourceType}:${it.sourceId}" }
        .toList()

fun isGroundedChatAnswer(message: ChatMessageResponse): Boolean =
    message.role == "ASSISTANT" &&
        message.responseStatus in setOf("SUCCEEDED", "FALLBACK_SUCCEEDED") &&
        message.route in setOf("SEARCH", "SUMMARY", "COMPARE") &&
        message.sources.any { !it.sourceType.isNullOrBlank() && !it.sourceId.isNullOrBlank() }

fun chatAnswerMode(message: ChatMessageResponse): ChatAnswerMode? {
    if (message.role != "ASSISTANT") return null
    return when {
        message.route in setOf("OUT_OF_SCOPE", "UNSUPPORTED") -> ChatAnswerMode.SCOPE_BOUNDARY
        message.route == "NAVIGATION" -> ChatAnswerMode.NAVIGATION
        message.responseStatus == "FALLBACK_SUCCEEDED" -> ChatAnswerMode.FALLBACK
        isGroundedChatAnswer(message) -> ChatAnswerMode.GROUNDED
        else -> ChatAnswerMode.GENERAL
    }
}

fun suggestedQuestionsFor(message: ChatMessageResponse): List<String> =
    message.suggestedQuestions
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .take(3)
        .toList()

fun quickActionsFor(message: ChatMessageResponse): List<ChatDestination> {
    val dynamic = message.suggestedDestinations
        .mapNotNull { value ->
            runCatching { ChatDestination.valueOf(value.trim().uppercase()) }.getOrNull()
        }
        .distinct()
        .take(3)
    if (dynamic.isNotEmpty()) return dynamic
    return when (message.route) {
        "NAVIGATION" -> listOf(
            ChatDestination.INVENTORY,
            ChatDestination.SHOPPING_LISTS,
            ChatDestination.SAVED_RECIPES,
        )
        "OUT_OF_SCOPE", "UNSUPPORTED" -> listOf(
            ChatDestination.RECOMMENDATIONS,
            ChatDestination.COOKING_PLANS,
            ChatDestination.EXPLORE,
        )
        else -> emptyList()
    }
}

fun chatSourceTypeLabel(sourceType: String?): String = when (sourceType) {
    "FOOD_RECORD" -> "Food record"
    "FOOD_PRODUCT" -> "Food product"
    "PLACE" -> "Place"
    else -> sourceType.orEmpty().lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
