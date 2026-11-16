package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `mainActivity composes and switches all bottom navigation tabs cleanly`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_memos").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_notepad").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_finance").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_memos").performClick()
        composeRule.waitForIdle()
    }
}
