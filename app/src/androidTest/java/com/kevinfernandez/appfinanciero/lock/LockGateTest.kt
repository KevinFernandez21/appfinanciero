package com.kevinfernandez.appfinanciero.lock

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LockGateTest {
    @get:Rule val compose = createComposeRule()
    @Test fun financeIsNeverComposedBeforeFakeAuthenticationAndRelocks() {
        val visible = mutableStateOf(false)
        var compositions = 0
        var requests = 0
        compose.setContent {
            MaterialTheme {
                LockGate(visible.value, false, null, { requests++ }) {
                    compositions++
                    Text("Private ledger")
                }
            }
        }
        compose.onNodeWithText("Private ledger").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, compositions) }
        compose.onNodeWithText("Unlock").performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithText("Private ledger").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText("Private ledger").assertIsDisplayed()
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithText("Private ledger").assertDoesNotExist()
    }
    @Test fun cancellationShowsRetryWithoutFinancialContent() {
        var retries = 0
        compose.setContent {
            MaterialTheme { LockGate(false, false, "Authentication canceled. Try again.", { retries++ }) { Text("Private ledger") } }
        }
        compose.onNodeWithText("Authentication canceled. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Unlock").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("Private ledger").assertDoesNotExist()
    }
    @Test fun failedOptInShowsSetupExplanationInUnlockedUi() {
        compose.setContent { MaterialTheme { LockGate(true, false, "Configure a device screen lock, then retry.", {}) { Text("Private ledger") } } }
        compose.onNodeWithText("Configure a device screen lock, then retry.").assertIsDisplayed()
        compose.onNodeWithText("Private ledger").assertIsDisplayed()
    }
    @Test fun pendingAuthenticationCannotLaunchDuplicatePrompt() {
        compose.setContent { MaterialTheme { LockGate(false, true, null, {}) { Text("Private ledger") } } }
        compose.onNodeWithText("Unlock").assertIsNotEnabled()
        compose.onNodeWithText("Private ledger").assertDoesNotExist()
    }
}
