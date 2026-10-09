package com.kevinfernandez.appfinanciero.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kevinfernandez.appfinanciero.ai.AiReply
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AiReplyCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun speechRequiresExplicitAction() {
        var requests = 0
        compose.setContent { MaterialTheme { AiReplyCard(AiReply("General budgeting explanation", true)) { requests++; true } } }
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText("Speak reply (offline Spanish voice)").performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithText("AI-GENERATED — MAY BE WRONG").assertIsDisplayed()
    }

    @Test fun unavailableVoiceShowsFallbackAndRetainsWarning() {
        compose.setContent { MaterialTheme { AiReplyCard(AiReply("General budgeting explanation", true)) { false } } }
        compose.onNodeWithText("Speak reply (offline Spanish voice)").performClick()
        compose.onNodeWithText("No offline Spanish voice is ready. Install an offline Spanish voice in device TTS settings or retry after initialization. No network voice will be used.").assertIsDisplayed()
        compose.onNodeWithText("AI-GENERATED — MAY BE WRONG").assertIsDisplayed()
    }
}
