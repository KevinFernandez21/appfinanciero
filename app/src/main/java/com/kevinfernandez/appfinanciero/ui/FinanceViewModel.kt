package com.kevinfernandez.appfinanciero.ui

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevinfernandez.appfinanciero.data.RoomFinanceRepository
import com.kevinfernandez.appfinanciero.data.FinanceSnapshot
import com.kevinfernandez.appfinanciero.data.FinanceDatabase
import com.kevinfernandez.appfinanciero.backup.FinanceBackupService
import com.kevinfernandez.appfinanciero.backup.BackupPreview
import com.kevinfernandez.appfinanciero.backup.EncryptedBackupCodec
import com.kevinfernandez.appfinanciero.ai.LocalLlmService
import com.kevinfernandez.appfinanciero.ai.AiReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart

class FinanceViewModel(private val database: FinanceDatabase, context: Context) : ViewModel() {
    private val repository = RoomFinanceRepository(database)
    private val mutableSnapshot = MutableStateFlow<FinanceSnapshot?>(null)
    val snapshot: StateFlow<FinanceSnapshot?> = mutableSnapshot
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError
    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy
    private val backup = FinanceBackupService(database)
    private var pendingRestoreJson: CharArray? = null
    private var pendingEncryptedImport: ByteArray? = null
    private var pendingEncryptedExport: ByteArray? = null
    private val mutableExportReady = MutableStateFlow(false)
    val exportReady: StateFlow<Boolean> = mutableExportReady
    private val mutableEncryptedPasswordRequired = MutableStateFlow(false)
    val encryptedPasswordRequired: StateFlow<Boolean> = mutableEncryptedPasswordRequired
    private val mutableLegacyRestore = MutableStateFlow(false)
    val legacyRestore: StateFlow<Boolean> = mutableLegacyRestore
    private val mutableRestorePreview = MutableStateFlow<BackupPreview?>(null)
    val restorePreview: StateFlow<BackupPreview?> = mutableRestorePreview
    private val mutableBackupNotice = MutableStateFlow<String?>(null)
    val backupNotice: StateFlow<String?> = mutableBackupNotice
    private val localAi = LocalLlmService(context.applicationContext)
    private val mutableModelInstalled = MutableStateFlow(false)
    val modelInstalled: StateFlow<Boolean> = mutableModelInstalled
    private val mutableAiReply = MutableStateFlow<AiReply?>(null)
    val aiReply: StateFlow<AiReply?> = mutableAiReply
    private val mutableAiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = mutableAiBusy
    private var aiJob: Job? = null

    init { viewModelScope.launch {
        try { repository.observeSnapshot().collect { mutableSnapshot.value = it } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutableError.value = "Could not load financial records. Please reopen the app." }
    } }
    init { viewModelScope.launch { mutableModelInstalled.value = withContext(Dispatchers.IO) { localAi.hasModel() } } }

    fun clearError() { mutableError.value = null }

    fun importModel(resolver: ContentResolver, uri: Uri) = runAi {
        val displayName = withContext(Dispatchers.IO) {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: ""
        }
        localAi.importModel(uri, displayName)
        mutableModelInstalled.value = true
        mutableAiReply.value = null
    }

    fun askAi(question: String) = runAi {
        mutableAiReply.value = null
        // Pass only the user's typed question; never include ledger facts or notes automatically.
        mutableAiReply.value = withContext(Dispatchers.IO) { localAi.reply(question) }
    }

    fun removeModel() = runAi {
        localAi.removeModel()
        mutableModelInstalled.value = false
        mutableAiReply.value = null
    }

    fun cancelAi() { aiJob?.cancel(); localAi.cancel() }

    private fun runAi(operation: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableAiBusy.value = true
        mutableError.value = null
        aiJob = viewModelScope.launch {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { mutableError.value = e.message ?: "Check the model or question." }
            catch (_: Exception) { mutableError.value = "Local AI could not complete the request. Check model compatibility or remove the model." }
            finally { mutableAiBusy.value = false; mutableBusy.value = false }
        }
    }

    fun prepareEncryptedExport(password: CharArray) = runBackup(cleanup = { password.fill('\u0000') }) {
        cancelPreparedExport()
        pendingEncryptedExport = withContext(Dispatchers.IO) { EncryptedBackupCodec.encrypt(backup.exportJson(), password) }
        mutableExportReady.value = true
    }

    fun consumeExportReady() { mutableExportReady.value = false }

    fun cancelPreparedExport() {
        pendingEncryptedExport?.fill(0)
        pendingEncryptedExport = null
        mutableExportReady.value = false
    }

    fun exportPreparedBackup(resolver: ContentResolver, uri: Uri) = runBackup {
        val bytes = requireNotNull(pendingEncryptedExport) { "Backup preparation was lost. Start a new encrypted export; no password was saved." }
        pendingEncryptedExport = null
        mutableExportReady.value = false
        try {
            withContext(Dispatchers.IO) {
                requireNotNull(resolver.openOutputStream(uri, "wt")) { "Could not open the selected document" }.use { it.write(bytes) }
            }
            mutableBackupNotice.value = "Encrypted backup exported. Keep its password safe; the app cannot recover it."
        } finally { bytes.fill(0) }
    }

    fun previewBackup(resolver: ContentResolver, uri: Uri) = runBackup {
        clearRestoreData()
        val bytes = withContext(Dispatchers.IO) {
            requireNotNull(resolver.openInputStream(uri)) { "Could not open the selected document" }
                .use { BackupDocumentBytes.read(it, EncryptedBackupCodec.MAX_ENVELOPE_BYTES) }
        }
        if (EncryptedBackupCodec.isEncrypted(bytes)) {
            pendingEncryptedImport = bytes
            mutableEncryptedPasswordRequired.value = true
        } else {
            try {
                val (characters, preview) = withContext(Dispatchers.IO) {
                    val json = BackupDocumentBytes.decodeUtf8(bytes)
                    val validated = backup.previewJson(json)
                    json.toCharArray() to validated
                }
                pendingRestoreJson = characters
                mutableRestorePreview.value = preview
                mutableLegacyRestore.value = true
            } finally { bytes.fill(0) }
        }
    }

    fun decryptBackup(password: CharArray) = runBackup(cleanup = { password.fill('\u0000') }) {
        val envelope = requireNotNull(pendingEncryptedImport) { "Select an encrypted backup first" }
        val (characters, preview) = withContext(Dispatchers.IO) {
            val json = EncryptedBackupCodec.decrypt(envelope, password)
            val validated = backup.previewJson(json)
            json.toCharArray() to validated
        }
        pendingRestoreJson = characters
        mutableRestorePreview.value = preview
        mutableLegacyRestore.value = false
        pendingEncryptedImport?.fill(0)
        pendingEncryptedImport = null
        mutableEncryptedPasswordRequired.value = false
    }

    private fun clearRestoreData() {
        pendingRestoreJson?.fill('\u0000')
        pendingRestoreJson = null
        pendingEncryptedImport?.fill(0)
        pendingEncryptedImport = null
        mutableRestorePreview.value = null
        mutableEncryptedPasswordRequired.value = false
        mutableLegacyRestore.value = false
    }

    fun cancelRestore() {
        if (!mutableBusy.value) {
            clearRestoreData()
            mutableError.value = null
        }
    }

    fun confirmRestore() {
        val json = pendingRestoreJson ?: return
        runBackup {
            withContext(Dispatchers.IO) { backup.restoreJson(String(json), confirmed = true) }
            clearRestoreData()
            mutableBackupNotice.value = "Backup restored successfully."
        }
    }

    private fun runBackup(cleanup: () -> Unit = {}, operation: suspend () -> Unit) {
        if (mutableBusy.value) { cleanup(); return }
        mutableBusy.value = true
        mutableError.value = null
        mutableBackupNotice.value = null
        val job = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { mutableError.value = e.message ?: "Invalid or unsupported backup." }
            catch (_: Exception) { mutableError.value = "Could not access or process the backup document. Please try again." }
            finally { cleanup(); mutableBusy.value = false }
        }
        // Also clear arrays if scope cancellation prevents the coroutine from entering its body.
        job.invokeOnCompletion { cleanup() }
    }

    fun save(operation: suspend RoomFinanceRepository.() -> Unit, onSuccess: () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try {
                repository.operation()
                mutableError.value = null
                onSuccess()
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { mutableError.value = e.message ?: "Please check the entered values." }
            catch (_: ArithmeticException) { mutableError.value = "Amount has too many decimal places or exceeds the supported range." }
            catch (_: Exception) { mutableError.value = "Could not save. Check the date and values, then try again." }
            finally { mutableBusy.value = false }
        }
    }

    override fun onCleared() { cancelPreparedExport(); clearRestoreData(); localAi.close(); database.close() }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FinanceViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return FinanceViewModel(FinanceDatabase.open(applicationContext), applicationContext) as T
        }
    }
}
