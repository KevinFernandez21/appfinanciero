package com.kevinfernandez.appfinanciero.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReleaseReservationDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelDoesNotRelease() {
        var releases = 0
        var cancellations = 0
        compose.setContent { MaterialTheme {
            ReleaseReservationDialog("Emergency", "Bank", "USD 20.00", false, null,
                onDismiss = { cancellations++ }, onConfirm = { releases++ })
        } }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, releases); assertEquals(1, cancellations) }
    }

    @Test fun confirmExplicitlyReleases() {
        var releases = 0
        compose.setContent { MaterialTheme {
            ReleaseReservationDialog("Emergency", "Bank", "USD 20.00", false, null,
                onDismiss = {}, onConfirm = { releases++ })
        } }
        compose.onNodeWithText("Confirm release").performClick()
        compose.runOnIdle { assertEquals(1, releases) }
    }

    @Test fun busyDialogPreventsRepeatedRelease() {
        compose.setContent { MaterialTheme {
            ReleaseReservationDialog("Emergency", "Bank", "USD 20.00", true, null,
                onDismiss = {}, onConfirm = {})
        } }
        compose.onNodeWithText("Releasing…").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }
}
