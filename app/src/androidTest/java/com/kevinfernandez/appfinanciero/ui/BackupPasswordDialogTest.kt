package com.kevinfernandez.appfinanciero.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BackupPasswordDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelClearsPasswordWithoutExporting() {
        var exports = 0
        var cancels = 0
        compose.setContent { MaterialTheme { BackupPasswordDialog(true, false, null,
            onDismiss = { cancels++ }, onPassword = { exports++; it.fill('\u0000') }) } }
        compose.onNodeWithText("Backup password").performTextInput("public-test-password")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Backup password").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.runOnIdle { assertEquals(0, exports); assertEquals(1, cancels) }
    }

    @Test fun exportRejectsShortAndMismatchedPasswords() {
        compose.setContent { MaterialTheme { BackupPasswordDialog(true, false, null,
            onDismiss = {}, onPassword = { it.fill('\u0000') }) } }
        compose.onNodeWithText("Backup password").performTextInput("short")
        compose.onNodeWithText("Confirm password").performTextInput("short")
        compose.onNodeWithText("Encrypt and choose file").assertIsNotEnabled()
        compose.onNodeWithText("Backup password").performTextReplacement("public-test-password")
        compose.onNodeWithText("Confirm password").performTextReplacement("different-test-password")
        compose.onNodeWithText("Passwords do not match.").assertIsDisplayed()
        compose.onNodeWithText("Encrypt and choose file").assertIsNotEnabled()
    }

    @Test fun passwordIsNotRestoredInSavedInstanceState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { BackupPasswordDialog(true, false, null,
            onDismiss = {}, onPassword = { it.fill('\u0000') }) } }
        compose.onNodeWithText("Backup password").performTextInput("public-test-password")
        compose.onNodeWithText("Confirm password").performTextInput("public-test-password")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Backup password").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("Confirm password").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test fun failedDecryptionShowsSanitizedErrorAndClearsPassword() {
        val error = mutableStateOf<String?>(null)
        compose.setContent { MaterialTheme { BackupPasswordDialog(false, false, error.value,
            onDismiss = {}, onPassword = { it.fill('\u0000'); error.value = "Invalid encrypted backup or password" }) } }
        compose.onNodeWithText("Backup password").performTextInput("wrong-test-password")
        compose.onNodeWithText("Decrypt preview").performClick()
        compose.onNodeWithText("Invalid encrypted backup or password").assertIsDisplayed()
        compose.onNodeWithText("Backup password").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test fun confirmedExportClearsBothFieldsAndBusyBlocksRepeat() {
        var submissions = 0
        val busy = mutableStateOf(false)
        compose.setContent { MaterialTheme { BackupPasswordDialog(true, busy.value, null,
            onDismiss = {}, onPassword = { submissions++; it.fill('\u0000'); busy.value = true }) } }
        compose.onNodeWithText("Backup password").performTextInput("public-test-password")
        compose.onNodeWithText("Confirm password").performTextInput("public-test-password")
        compose.onNodeWithText("Encrypt and choose file").performClick()
        compose.runOnIdle { assertEquals(1, submissions) }
        compose.onNodeWithText("Backup password").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("Confirm password").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("Processing…").assertIsNotEnabled()
    }
}
