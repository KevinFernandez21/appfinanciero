package com.kevinfernandez.appfinanciero.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kevinfernandez.appfinanciero.MainActivity
import org.junit.Rule
import org.junit.Test

class FinanceScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun accountDraftCanBeCancelledWithoutSaving() {
        compose.onNodeWithText("Accounts").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Add account")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add account").performClick()
        compose.onNodeWithText("Amount is the opening balance, before recorded movements.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Add account").assertIsDisplayed()
    }

    @Test fun selectedPageSurvivesActivityRecreation() {
        compose.onNodeWithText("Accounts").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Add account")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add account").assertIsDisplayed()
    }

    @Test fun backupSettingsDefaultToEncryptionAndWarnAboutLegacyFiles() {
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("LEGACY JSON BACKUPS ARE NOT ENCRYPTED")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Password-encrypted backups by default").assertIsDisplayed()
        compose.onNodeWithText("LEGACY JSON BACKUPS ARE NOT ENCRYPTED").assertIsDisplayed()
    }

    @Test fun optionalAiKeepsQuestionSeparateFromLedger() {
        compose.onNodeWithText("AI").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Optional local AI")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Optional local AI").assertIsDisplayed()
        compose.onNodeWithText("The ledger and computed insights work without a model. AI has no ledger access and cannot record movements.").assertIsDisplayed()
    }
}
