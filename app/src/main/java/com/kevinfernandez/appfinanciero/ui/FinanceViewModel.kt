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
import com.kevinfernandez.appfinanciero.ai.LocalLlmService
import com.kevinfernandez.appfinanciero.ai.AiReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job

class FinanceViewModel(private val database: FinanceDatabase, context: Context) : ViewModel() {
    private val repository = RoomFinanceRepository(database)
    private val mutableSnapshot = MutableStateFlow<FinanceSnapshot?>(null)
    val snapshot: StateFlow<FinanceSnapshot?> = mutableSnapshot
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError
    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy
    private val backup = FinanceBackupService(database)
    private var pendingRestoreJson: String? = null
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

    fun exportBackup(resolver: ContentResolver, uri: Uri) = runBackup {
        withContext(Dispatchers.IO) {
            val json = backup.exportJson()
            requireNotNull(resolver.openOutputStream(uri, "wt")) { "Could not open the selected document" }
                .bufferedWriter(Charsets.UTF_8).use { it.write(json) }
        }
        mutableBackupNotice.value = "Backup exported. This file is NOT encrypted; store it securely."
    }

    fun previewBackup(resolver: ContentResolver, uri: Uri) = runBackup {
        val (json, preview) = withContext(Dispatchers.IO) {
            val text = requireNotNull(resolver.openInputStream(uri)) { "Could not open the selected document" }
                .bufferedReader(Charsets.UTF_8).use { BackupDocumentText.read(it, FinanceBackupService.MAX_JSON_CHARACTERS) }
            text to backup.previewJson(text)
        }
        pendingRestoreJson = json
        mutableRestorePreview.value = preview
    }

    fun cancelRestore() {
        if (!mutableBusy.value) {
            pendingRestoreJson = null
            mutableRestorePreview.value = null
        }
    }

    fun confirmRestore() {
        val json = pendingRestoreJson ?: return
        runBackup {
            withContext(Dispatchers.IO) { backup.restoreJson(json, confirmed = true) }
            pendingRestoreJson = null
            mutableRestorePreview.value = null
            mutableBackupNotice.value = "Backup restored successfully."
        }
    }

    private fun runBackup(operation: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableError.value = null
        mutableBackupNotice.value = null
        viewModelScope.launch {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { mutableError.value = e.message ?: "Invalid or unsupported backup." }
            catch (_: Exception) { mutableError.value = "Could not access or process the backup document. Please try again." }
            finally { mutableBusy.value = false }
        }
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

    override fun onCleared() { localAi.close(); database.close() }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FinanceViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return FinanceViewModel(FinanceDatabase.open(applicationContext), applicationContext) as T
        }
    }
}
