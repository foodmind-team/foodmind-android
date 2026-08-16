package com.foodmind.foodmind_android

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthorisedImageUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingAuthorisedUrlShowsStablePlaceholder() {
        composeRule.setContent {
            FoodMindTheme {
                AuthorisedImage(
                    model = null,
                    contentDescription = "Record image",
                    modifier = Modifier.size(160.dp),
                )
            }
        }

        composeRule.onNodeWithText("No image").assertIsDisplayed()
    }
}
