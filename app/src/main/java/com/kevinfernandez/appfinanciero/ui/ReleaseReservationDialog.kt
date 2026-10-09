package com.kevinfernandez.appfinanciero.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun ReleaseReservationDialog(
    goalName: String,
    accountName: String,
    amount: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Release reservation?") },
        text = {
            Column {
                Text("Release $amount reserved for $goalName from $accountName?")
                Text("This removes only the virtual earmark. Account balances and transaction history stay unchanged; the release is recorded in the reservation audit.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy) { Text(if (busy) "Releasing…" else "Confirm release") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
