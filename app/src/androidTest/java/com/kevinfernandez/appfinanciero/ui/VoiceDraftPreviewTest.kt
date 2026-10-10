package com.kevinfernandez.appfinanciero.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kevinfernandez.appfinanciero.voice.VoiceTransactionDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class VoiceDraftPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun interpretedSpeechRequiresExplicitApply() {
        var applied: VoiceTransactionDraft? = null
        compose.setContent {
            MaterialTheme {
                VoiceDraftPreview("Gasto 12,50 en café", "USD", true, { applied = it }, {})
            }
        }
        compose.onNodeWithText("Gasto 12,50 en café", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText("Apply voice draft").performClick()
        compose.runOnIdle { assertEquals(VoiceTransactionDraft("EXPENSE", "12.50", "café"), applied) }
    }

    @Test fun unsupportedSpeechCannotChangeFields() {
        var discarded = false
        compose.setContent {
            MaterialTheme {
                VoiceDraftPreview("transferencia 50", "USD", true, {}, { discarded = true })
            }
        }
        compose.onNodeWithText("Apply voice draft").assertDoesNotExist()
        compose.onNodeWithText("Could not safely interpret this command", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Discard transcript").performClick()
        compose.runOnIdle { assertEquals(true, discarded) }
    }
}
