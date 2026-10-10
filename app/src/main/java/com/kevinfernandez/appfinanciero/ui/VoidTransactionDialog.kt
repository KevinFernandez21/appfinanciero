package com.kevinfernandez.appfinanciero.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun VoidTransactionDialog(
    transactionId: String,
    title: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by rememberSaveable(transactionId) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Void movement?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title)
                Text("This preserves the original movement and records your reason in its audit history. The original effect on balances and cash flow is reversed.")
                Text("To correct an amount or account, confirm this void, then record a new movement separately. A replacement is a separate save, not part of this action.")
                OutlinedTextField(reason, onValueChange = { reason = it }, enabled = !busy,
                    label = { Text("Reason (required)") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason.trim()) }, enabled = !busy && reason.isNotBlank()) {
            Text(if (busy) "Voiding…" else "Confirm void")
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
internal fun TransactionHistoryCard(title: String, detail: String, voidDetails: String?, busy: Boolean, onVoid: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail)
            if (voidDetails != null) {
                Text("VOIDED — excluded from balances and cash flow", color = MaterialTheme.colorScheme.error)
                Text(voidDetails)
            } else {
                OutlinedButton(onClick = onVoid, enabled = !busy) { Text("Void / correct") }
            }
        }
    }
}
