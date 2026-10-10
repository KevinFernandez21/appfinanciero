package com.kevinfernandez.appfinanciero.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kevinfernandez.appfinanciero.backup.EncryptedBackupCodec

/** Password fields deliberately never enter saved instance state or preferences. */
@Composable
internal fun BackupPasswordDialog(forExport: Boolean, busy: Boolean, error: String?, onDismiss: () -> Unit, onPassword: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    fun clear() { password = ""; confirmation = "" }
    DisposableEffect(Unit) { onDispose { clear() } }
    val valid = if (forExport) password.length >= EncryptedBackupCodec.MIN_PASSWORD_LENGTH && password.isNotBlank() && password == confirmation else password.isNotEmpty()
    AlertDialog(onDismissRequest = { if (!busy) { clear(); onDismiss() } },
        title = { Text(if (forExport) "Encrypt backup" else "Open encrypted backup") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (forExport) "Use a strong password of at least ${EncryptedBackupCodec.MIN_PASSWORD_LENGTH} characters. Keep it safe: the app cannot recover a forgotten password." else "Enter this backup's password. Decryption creates only a validated preview; restoring still requires your explicit confirmation.")
                OutlinedTextField(password, onValueChange = { password = it }, enabled = !busy, label = { Text("Backup password") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (forExport) {
                    OutlinedTextField(confirmation, onValueChange = { confirmation = it }, enabled = !busy, label = { Text("Confirm password") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (confirmation.isNotEmpty() && password != confirmation) Text("Passwords do not match.", color = MaterialTheme.colorScheme.error)
                }
                Text("Passwords are not saved. Closing this dialog or recreating the screen clears the fields.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { val secret = password.toCharArray(); clear(); onPassword(secret) }, enabled = !busy && valid) {
            Text(if (busy) "Processing…" else if (forExport) "Encrypt and choose file" else "Decrypt preview")
        } },
        dismissButton = { TextButton(onClick = { clear(); onDismiss() }, enabled = !busy) { Text("Cancel") } })
}
