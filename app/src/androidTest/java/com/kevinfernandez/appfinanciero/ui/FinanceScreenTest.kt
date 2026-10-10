package com.kevinfernandez.appfinanciero.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kevinfernandez.appfinanciero.MainActivity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Rule
import org.junit.Test

class FinanceScreenTest {
    val compose = createAndroidComposeRule<MainActivity>()
    // These tests exercise finance UI, not the authentication boundary.
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        private val preferences get() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("app_lock", 0)
        override fun before() { preferences.edit().putBoolean("enabled", false).commit() }
        override fun after() { preferences.edit().clear().commit() }
    }).around(compose)

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
        compose.onNodeWithText("Password-encrypted backups by default").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("LEGACY JSON BACKUPS ARE NOT ENCRYPTED").performScrollTo().assertIsDisplayed()
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
