package com.kevinfernandez.appfinanciero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kevinfernandez.appfinanciero.voice.VoiceTransactionDraft

@Composable
internal fun VoiceDraftPreview(transcript: String, currency: String, enabled: Boolean,
    onApply: (VoiceTransactionDraft) -> Unit, onDiscard: () -> Unit,
) {
    val draft = VoiceTransactionDraft.parse(transcript, currency)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Recognized speech: $transcript")
            if (draft == null) {
                Text("Could not safely interpret this command for $currency. Type the movement instead; nothing changed.",
                    color = MaterialTheme.colorScheme.error)
            } else {
                Text("Draft: ${draft.type.lowercase().replaceFirstChar(Char::uppercase)} · $currency ${draft.amount}")
                if (draft.note.isNotBlank()) Text("Note: ${draft.note}")
                Text("Account, category, and date are unchanged. Check every field before saving.")
            }
            Row {
                if (draft != null) TextButton(onClick = { onApply(draft) }, enabled = enabled) { Text("Apply voice draft") }
                TextButton(onClick = onDiscard, enabled = enabled) { Text("Discard transcript") }
            }
        }
    }
}
