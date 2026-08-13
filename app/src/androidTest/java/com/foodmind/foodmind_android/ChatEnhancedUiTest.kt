package com.foodmind.foodmind_android

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foodmind.foodmind_android.core.network.ChatMessageResponse
import com.foodmind.foodmind_android.core.network.ChatMessageSourceResponse
import com.foodmind.foodmind_android.core.network.ChatSessionResponse
import com.foodmind.foodmind_android.core.network.ExploreItemResponse
import com.foodmind.foodmind_android.feature.chat.ChatDestination
import com.foodmind.foodmind_android.feature.chat.ChatListUiState
import com.foodmind.foodmind_android.feature.chat.ChatUiState
import com.foodmind.foodmind_android.feature.chat.OutgoingChatMessage
import com.foodmind.foodmind_android.feature.chat.OutgoingMessageStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatEnhancedUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun groundedAnswerShowsModeAndSourceButGeneralAnswerDoesNot() {
        val source = ChatMessageSourceResponse(
            referenceId = "reference-1",
            sourceType = "PLACE",
            sourceId = "place-1",
            title = "Grounded cafe",
        )
        composeRule.setContent {
            FoodMindTheme {
                EnhancedChatScreen(
                    ChatUiState(
                        isLoading = false,
                        sessionId = "session-1",
                        messages = listOf(
                            ChatMessageResponse(
                                id = "m1",
                                role = "ASSISTANT",
                                content = "Grounded answer",
                                route = "SEARCH",
                                responseStatus = "SUCCEEDED",
                                sources = listOf(source),
                            ),
                            ChatMessageResponse(
                                id = "m2",
                                role = "ASSISTANT",
                                content = "General answer",
                                route = "GENERAL",
                                responseStatus = "SUCCEEDED",
                                sources = listOf(source.copy(title = "Hidden source")),
                            ),
                            ChatMessageResponse(
                                id = "m3",
                                role = "ASSISTANT",
                                content = "Fallback grounded answer",
                                route = "SUMMARY",
                                responseStatus = "FALLBACK_SUCCEEDED",
                                sources = listOf(source.copy(sourceId = "place-2", title = "Fallback cafe")),
                            ),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("answer_mode_grounded").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Grounded cafe").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("answer_mode_general").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("answer_mode_fallback").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Fallback cafe").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("source_context_toggle").performScrollTo().performClick()
        composeRule.onAllNodesWithText("Hidden source").assertCountEquals(0)
    }

    @Test
    fun failedMessageCanRetryAndBoundaryRouteNavigatesWithoutExecutingAction() {
        val retried = AtomicBoolean(false)
        val destination = AtomicReference<ChatDestination>()
        composeRule.setContent {
            FoodMindTheme {
                EnhancedChatScreen(
                    state = ChatUiState(
                        isLoading = false,
                        sessionId = "session-1",
                        messages = listOf(
                            ChatMessageResponse(
                                id = "m1",
                                role = "ASSISTANT",
                                content = "Use a FoodMind tool for this action.",
                                route = "OUT_OF_SCOPE",
                                responseStatus = "SUCCEEDED",
                            ),
                        ),
                        outgoingMessage = OutgoingChatMessage(
                            localId = "local-1",
                            content = "Please retry",
                            referenceIds = emptyList(),
                            referenceTitles = emptyList(),
                            status = OutgoingMessageStatus.FAILED,
                        ),
                    ),
                    onRetryMessage = { retried.set(true) },
                    onDestination = destination::set,
                )
            }
        }

        composeRule.onNodeWithTag("quick_action_recommendations").performScrollTo().performClick()
        assertEquals(ChatDestination.RECOMMENDATIONS, destination.get())
        composeRule.onNodeWithTag("retry_failed").performScrollTo().performClick()
        assertTrue(retried.get())
    }

    @Test
    fun sourcePickerShowsUsefulPreviewMetadata() {
        composeRule.setContent {
            FoodMindTheme {
                EnhancedChatScreen(
                    ChatUiState(
                        isLoading = false,
                        sessionId = "session-1",
                        searchQuery = "cafe",
                        searchResults = listOf(
                            ExploreItemResponse(
                                sourceType = "PLACE",
                                sourceId = "place-2",
                                title = "Shared cafe",
                                subtitle = "Tiong Bahru",
                                snippet = "Quiet breakfast place",
                                visibility = "GROUP",
                                occurredAt = "2026-08-12T08:30:00Z",
                            ),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Attach authorised source").performClick()
        composeRule.onNodeWithTag("source_result_place-2").assertIsDisplayed()
        composeRule.onNodeWithText("Shared cafe").assertIsDisplayed()
        composeRule.onNodeWithText("Tiong Bahru").assertIsDisplayed()
        composeRule.onNodeWithText("Quiet breakfast place").assertIsDisplayed()
    }

    @Test
    fun conversationSearchRendersOnlyMatchingSessions() {
        composeRule.setContent {
            FoodMindTheme {
                EnhancedChatListScreen(
                    ChatListUiState(
                        isLoading = false,
                        query = "dinner",
                        sessions = listOf(
                            ChatSessionResponse(id = "s1", title = "Breakfast notes"),
                            ChatSessionResponse(id = "s2", title = "Dinner ideas"),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Dinner ideas").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Breakfast notes").assertCountEquals(0)
    }
}
