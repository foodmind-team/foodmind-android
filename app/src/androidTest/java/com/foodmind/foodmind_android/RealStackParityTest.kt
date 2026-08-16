package com.foodmind.foodmind_android

import android.content.Intent
import android.app.Activity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in, no-mock parity probe for the Compose client. The Web real-stack test
 * creates the shared account/data first; this test logs into the same backend
 * through 10.0.2.2 and verifies cross-client recipe, inventory, and Explore image visibility.
 */
@RunWith(AndroidJUnit4::class)
class RealStackParityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val scenarios = mutableListOf<ActivityScenario<*>>()

    @Before
    fun requireRealStack() {
        assumeTrue(
            "Run with -Pandroid.testInstrumentationRunnerArguments.realStack=true",
            InstrumentationRegistry.getArguments().getString("realStack") == "true",
        )
    }

    @After
    fun closeScenarios() {
        scenarios.reversed().forEach(ActivityScenario<*>::close)
    }

    @Test
    fun webCreatedDataAndMinioImageAreVisibleOnAndroid() {
        launch(LoginActivity::class.java)
        composeRule.onNodeWithText("Email").performTextInput("parity-e2e-20260811@example.test")
        composeRule.onNodeWithText("Password").performTextInput("Real-stack-password-2026")
        composeRule.onNodeWithText("Sign in").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching { composeRule.onAllNodesWithText("Email").fetchSemanticsNodes().isEmpty() }
                .getOrDefault(true)
        }

        launch(CookingInventoryActivity::class.java)
        assertLazyContentIsVisible("E2E firm tofu")

        launch(CookingHomeActivity::class.java)
        assertLazyContentIsVisible("E2E tofu bowl")

        launch(ShoppingListsActivity::class.java)
        composeRule.onNodeWithText("Shopping lists").assertIsDisplayed()

        launch(ExploreActivity::class.java)
        assertLazyContentIsVisible("E2E MinIO image meal")
        val loadedImage = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "LOADED")
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching { composeRule.onAllNodes(loadedImage).fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
        composeRule.onAllNodes(loadedImage).onFirst().assertIsDisplayed()

        launch(RecipeImportActivity::class.java)
        composeRule.onNodeWithText("Import recipes").assertIsDisplayed()
    }

    private fun launch(activity: Class<out Activity>) {
        scenarios += ActivityScenario.launch<Activity>(Intent(ApplicationProvider.getApplicationContext(), activity))
    }

    private fun assertLazyContentIsVisible(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onAllNodes(hasScrollAction()).onFirst()
                    .performScrollToNode(hasText(text, substring = true))
                true
            }.getOrDefault(false)
        }
        composeRule.onAllNodesWithText(text, substring = true).onFirst().assertIsDisplayed()
    }
}
