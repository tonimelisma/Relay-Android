package net.melisma.relay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class PermissionUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun permissionButtonAndStatusVisible() {
        composeRule.onNodeWithText("Permission status:", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Request SMS/MMS Permissions").assertIsDisplayed()
    }
}


