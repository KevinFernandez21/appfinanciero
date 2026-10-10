package com.kevinfernandez.appfinanciero.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VoidTransactionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelNeverVoidsMovement() {
        var voids = 0
        var cancellations = 0
        compose.setContent { MaterialTheme { VoidTransactionDialog("t1", "Expense · USD 10.00", false, null,
            onDismiss = { cancellations++ }, onConfirm = { voids++ }) } }
        compose.onNodeWithText("Reason (required)").performTextInput("Duplicate entry")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, voids); assertEquals(1, cancellations) }
    }

    @Test fun confirmSubmitsTrimmedRequiredReason() {
        var reason: String? = null
        compose.setContent { MaterialTheme { VoidTransactionDialog("t1", "Expense · USD 10.00", false, null,
            onDismiss = {}, onConfirm = { reason = it }) } }
        compose.onNodeWithText("Reason (required)").performTextInput("  Duplicate entry  ")
        compose.runOnIdle { assertEquals(null, reason) }
        compose.onNodeWithText("Confirm void").performClick()
        compose.runOnIdle { assertEquals("Duplicate entry", reason) }
    }

    @Test fun blankReasonCannotBeConfirmed() {
        compose.setContent { MaterialTheme { VoidTransactionDialog("t1", "Expense · USD 10.00", false, null,
            onDismiss = {}, onConfirm = {}) } }
        compose.onNodeWithText("Confirm void").assertIsNotEnabled()
        compose.onNodeWithText("Reason (required)").performTextInput("   ")
        compose.onNodeWithText("Confirm void").assertIsNotEnabled()
    }

    @Test fun busyDialogBlocksRepeatedConfirmation() {
        compose.setContent { MaterialTheme { VoidTransactionDialog("t1", "Expense · USD 10.00", true, null,
            onDismiss = {}, onConfirm = {}) } }
        compose.onNodeWithText("Voiding…").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("Reason (required)").assertIsNotEnabled()
    }

    @Test fun voidedMovementRemainsVisibleWithoutRepeatAction() {
        compose.setContent { MaterialTheme { TransactionHistoryCard("Expense · USD 10.00", "Bank · 2026-10-09",
            "Duplicate entry · 2026-10-10", false, onVoid = {}) } }
        compose.onNodeWithText("VOIDED — excluded from balances and cash flow").assertIsDisplayed()
        compose.onNodeWithText("Duplicate entry · 2026-10-10").assertIsDisplayed()
        compose.onNodeWithText("Void / correct").assertDoesNotExist()
    }
}
