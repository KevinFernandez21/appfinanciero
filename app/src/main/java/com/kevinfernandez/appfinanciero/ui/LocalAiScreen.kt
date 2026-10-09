package com.kevinfernandez.appfinanciero.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinfernandez.appfinanciero.ai.AiReply
import com.kevinfernandez.appfinanciero.voice.OfflineSpeaker

@Composable
internal fun LocalAiScreen(viewModel: FinanceViewModel) {
    val installed by viewModel.modelInstalled.collectAsStateWithLifecycle()
    val reply by viewModel.aiReply.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val aiBusy by viewModel.aiBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val speaker = remember(context) { OfflineSpeaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.close() } }
    // Typed questions stay only in memory, not the Activity's persisted state bundle.
    var question by remember { mutableStateOf("") }
    var removeConfirmation by rememberSaveable { mutableStateOf(false) }
    val resolver = LocalContext.current.applicationContext.contentResolver
    val importDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importModel(resolver, it) }
    }
    Text("Optional local AI", style = MaterialTheme.typography.titleLarge)
    Text("The ledger and computed insights work without a model. AI has no ledger access and cannot record movements.")
    Text(if (installed) "Model imported (compatibility checked on first request)." else "No model installed.")
    Text("Import a trusted, CPU-compatible .litertlm model (maximum 1 GiB). Models are not bundled or downloaded automatically.")
    Button(onClick = { importDocument.launch(arrayOf("*/*")) }, enabled = !busy) { Text(if (installed) "Replace local model" else "Import local model") }
    if (installed) OutlinedButton(onClick = { removeConfirmation = true }, enabled = !busy) { Text("Remove local model") }
    Text("Only what you type below is sent to the on-device model. Do not include private financial notes unnecessarily.")
    OutlinedTextField(question, onValueChange = { question = it }, label = { Text("General budgeting question") },
        supportingText = { Text("Maximum 2000 characters; prompts are not saved to financial history.") },
        enabled = !busy, modifier = Modifier.fillMaxWidth(), minLines = 3)
    Button(onClick = { viewModel.askAi(question) }, enabled = !busy && question.isNotBlank()) { Text("Ask on-device") }
    if (aiBusy) {
        CircularProgressIndicator()
        TextButton(onClick = viewModel::cancelAi) { Text("Cancel AI request") }
    }
    reply?.let { answer ->
        AiReplyCard(answer) {
            // Read only the displayed answer after an explicit tap, never ledger notes.
            speaker.isReady && runCatching { speaker.speak(answer.text) }.getOrDefault(false)
        }
    }
    if (removeConfirmation) AlertDialog(onDismissRequest = { removeConfirmation = false },
        title = { Text("Remove local model?") }, text = { Text("Financial records are unaffected. You can import a model again later.") },
        confirmButton = { TextButton(onClick = { removeConfirmation = false; viewModel.removeModel() }, enabled = !busy) { Text("Remove model") } },
        dismissButton = { TextButton(onClick = { removeConfirmation = false }) { Text("Cancel") } })
}

@Composable
internal fun AiReplyCard(answer: AiReply, onSpeak: () -> Boolean) {
    var speechUnavailable by remember(answer) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (answer.generated) "AI-GENERATED — MAY BE WRONG" else "App information", style = MaterialTheme.typography.titleMedium)
            if (answer.generated) Text("Not financial facts or advice. Verify independently; this text never changes your ledger.", color = MaterialTheme.colorScheme.error)
            Text(answer.text)
            TextButton(onClick = { speechUnavailable = !onSpeak() }) { Text("Speak reply (offline Spanish voice)") }
            Text("Reads only this displayed reply. The installed offline Spanish voice may pronounce other languages differently.", style = MaterialTheme.typography.bodySmall)
            if (speechUnavailable) Text("No offline Spanish voice is ready. Install an offline Spanish voice in device TTS settings or retry after initialization. No network voice will be used.", color = MaterialTheme.colorScheme.error)
        }
    }
}
