package com.foodmind.foodmind_android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeRendersAndCookingModeKeepsRecipeManagementInMyRecipes() {
        composeRule.onNodeWithText("FoodMind").assertIsDisplayed()
        composeRule.onNodeWithText("烹饪").performClick()
        composeRule.onNodeWithText("开始选择菜谱").assertIsDisplayed()
        composeRule.onAllNodesWithText("添加菜谱").assertCountEquals(0)
    }

    @Test
    fun recommendationModeShowsPrimaryAction() {
        composeRule.onNodeWithText("生成推荐").assertIsDisplayed()
    }
}
