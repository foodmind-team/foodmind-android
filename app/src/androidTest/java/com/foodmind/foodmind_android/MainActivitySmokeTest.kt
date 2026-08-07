package com.foodmind.foodmind_android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
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
    fun homeRendersAndCookingModeKeepsRecipeManagementInMyRecipes() {
        composeRule.onNodeWithText("FoodMind").assertIsDisplayed()
        composeRule.onNodeWithText("Cooking").performClick()
        composeRule.onNodeWithText("Start from local recipes").assertIsDisplayed()
        composeRule.onAllNodesWithText("Add recipe").assertCountEquals(0)
    }

    @Test
    fun recommendationModeShowsPrimaryAction() {
        composeRule.onNodeWithText("Generate recommendations").assertIsDisplayed()
    }
}
