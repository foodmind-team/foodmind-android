package com.foodmind.foodmind_android

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class RecommendationContextActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RecommendationContextActivity>()

    @Test
    fun locationUsesDeviceActionWithoutManualCoordinateOrDuplicatePreferenceFields() {
        composeRule.onNodeWithText("Use current location").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Maximum distance (km)").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Latitude").assertCountEquals(0)
        composeRule.onAllNodesWithText("Longitude").assertCountEquals(0)
        composeRule.onAllNodesWithText("Dietary requirements").assertCountEquals(0)
        composeRule.onAllNodesWithText("Allergens to avoid").assertCountEquals(0)
    }
}
