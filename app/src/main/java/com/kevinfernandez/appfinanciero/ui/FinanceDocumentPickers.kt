package com.kevinfernandez.appfinanciero.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel

interface DocumentPickerActions {
    fun exportBackup(name: String)
    fun openBackup()
    fun openModel()
}
enum class DocumentKind { EXPORT_BACKUP, IMPORT_BACKUP, IMPORT_MODEL }
data class DocumentResult(val kind: DocumentKind, val uri: Uri?)

/** Activity-owned keys stay registered while the lock gate removes financial content. */
class FinanceDocumentPickers(registry: ActivityResultRegistry, receive: (DocumentResult) -> Unit) : DocumentPickerActions {
    private val export = registry.register("finance.backup.export", ActivityResultContracts.CreateDocument("application/octet-stream")) {
        receive(DocumentResult(DocumentKind.EXPORT_BACKUP, it))
    }
    private val backup = registry.register("finance.backup.import", ActivityResultContracts.OpenDocument()) {
        receive(DocumentResult(DocumentKind.IMPORT_BACKUP, it))
    }
    private val model = registry.register("finance.model.import", ActivityResultContracts.OpenDocument()) {
        receive(DocumentResult(DocumentKind.IMPORT_MODEL, it))
    }
    override fun exportBackup(name: String) = export.launch(name)
    override fun openBackup() = backup.launch(arrayOf("*/*"))
    override fun openModel() = model.launch(arrayOf("*/*"))
    fun close() { export.unregister(); backup.unregister(); model.unregister() }
}

/** URI capabilities remain in memory across rotation, awaiting an unlocked handler. */
class PendingDocumentResults : ViewModel() {
    var pending by mutableStateOf<List<DocumentResult>>(emptyList())
        private set
    fun receive(result: DocumentResult) { pending = pending + result }
    fun deliverNext(accept: (DocumentResult) -> Boolean): Boolean {
        val result = pending.firstOrNull() ?: return false
        if (!accept(result)) return false
        pending = pending.drop(1)
        return true
    }
}

fun handleDocumentResult(model: FinanceViewModel, resolver: ContentResolver, result: DocumentResult): Boolean {
    if (model.busy.value) return false
    when (result.kind) {
        DocumentKind.EXPORT_BACKUP -> if (result.uri == null) model.cancelPreparedExport() else model.exportPreparedBackup(resolver, result.uri)
        DocumentKind.IMPORT_BACKUP -> result.uri?.let { model.previewBackup(resolver, it) }
        DocumentKind.IMPORT_MODEL -> result.uri?.let { model.importModel(resolver, it) }
    }
    return true
}

/** Standalone screen hosts (including UI tests) without an app-lock boundary. */
@Composable
internal fun rememberFinanceDocumentPickers(model: FinanceViewModel): DocumentPickerActions {
    val registry = requireNotNull(LocalActivityResultRegistryOwner.current).activityResultRegistry
    val resolver = LocalContext.current.applicationContext.contentResolver
    val pickers = remember(registry, model) { FinanceDocumentPickers(registry) { handleDocumentResult(model, resolver, it) } }
    DisposableEffect(pickers) { onDispose { pickers.close() } }
    return pickers
}
