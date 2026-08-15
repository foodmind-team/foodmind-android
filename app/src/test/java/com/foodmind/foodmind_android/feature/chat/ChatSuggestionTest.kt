package com.foodmind.foodmind_android.feature.chat

import com.foodmind.foodmind_android.core.network.ChatMessageResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSuggestionTest {

    @Test
    fun agentDestinationsOverrideStaticRouteDefaults() {
        val message = ChatMessageResponse(
            role = "ASSISTANT",
            route = "NAVIGATION",
            suggestedDestinations = listOf("COOKING_PLANS", "INVENTORY"),
        )

        assertEquals(
            listOf(ChatDestination.COOKING_PLANS, ChatDestination.INVENTORY),
            quickActionsFor(message),
        )
    }

    @Test
    fun suggestedQuestionsAreTrimmedDeduplicatedAndBounded() {
        val message = ChatMessageResponse(
            role = "ASSISTANT",
            suggestedQuestions = listOf("  Compare these. ", "Compare these.", "Explain this.", "One more."),
        )

        assertEquals(
            listOf("Compare these.", "Explain this.", "One more."),
            suggestedQuestionsFor(message),
        )
    }
}
