package com.kevinfernandez.appfinanciero.lock

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LockGate(visible: Boolean, busy: Boolean, message: String?, authenticate: () -> Unit, content: @Composable () -> Unit) {
    if (visible) {
        Column(Modifier.fillMaxSize()) {
            message?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            Box(Modifier.weight(1f)) { content() }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
            Text("AppAhorro is locked", style = MaterialTheme.typography.headlineSmall)
            Text("Authenticate with your device screen lock or a strong biometric to continue.")
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = authenticate, enabled = !busy) { Text("Unlock") }
        }
    }
}

@Composable
fun LockSettings(enabled: Boolean, change: () -> Unit) {
    Text("App lock", style = MaterialTheme.typography.titleLarge)
    Text("App lock is on by default. Authenticate before disabling it.")
    Text("${if (enabled) "On" else "Off"} · Require device authentication when returning to the app.")
    Text("This protects access to the app UI, not the database files. It does not encrypt your ledger or protect a compromised device.")
    OutlinedButton(onClick = change) { Text(if (enabled) "Disable app lock" else "Enable app lock") }
}
