package com.foodmind.foodmind_android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import android.content.Intent

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launch() {
        scenario = ActivityScenario.launch(
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_BYPASS_AUTH_FOR_TEST, true),
        )
    }

    @After
    fun close() { scenario.close() }

    @Test
    fun cookingModeOpensRecipeSelectionAndKitchenMenuDirectly() {
        composeRule.onNodeWithText("FoodMind").assertIsDisplayed()
        composeRule.onNodeWithText("Cooking").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("What do you want to cook tonight?")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("What do you want to cook tonight?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Choose a cooking starting point").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Kitchen").performClick()
        composeRule.onNodeWithText("Shopping lists").assertIsDisplayed()
        composeRule.onNodeWithText("Inventory").assertIsDisplayed()
        composeRule.onNodeWithText("Plan history").assertIsDisplayed()
    }

    @Test
    fun recommendationModeShowsPrimaryAction() {
        composeRule.onNodeWithText("Generate recommendations").assertIsDisplayed()
    }
}
