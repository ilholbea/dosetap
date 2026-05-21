package com.dosetap

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun welcomeScreen_isDisplayed_onFirstLaunch() {
        composeTestRule
            .onNodeWithText("Welcome to DoseTap", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun welcomeScreen_nextButton_navigatesToPermissions() {
        composeTestRule
            .onNodeWithText("Next")
            .performClick()

        composeTestRule
            .onNodeWithText("Allow Notifications", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
