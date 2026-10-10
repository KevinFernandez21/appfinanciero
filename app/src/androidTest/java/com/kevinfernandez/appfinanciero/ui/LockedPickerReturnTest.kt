package com.kevinfernandez.appfinanciero.ui

import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.flow.first
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kevinfernandez.appfinanciero.backup.EncryptedBackupCodec
import com.kevinfernandez.appfinanciero.backup.FinanceBackupService
import com.kevinfernandez.appfinanciero.data.FinanceDatabase
import com.kevinfernandez.appfinanciero.lock.AppLockPolicy
import com.kevinfernandez.appfinanciero.lock.LockGate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class LockedPickerReturnTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ViewModelStore()
    private val lock = AppLockPolicy(true)
    private val revision = mutableIntStateOf(0)
    private lateinit var model: FinanceViewModel
    private lateinit var restoration: StateRestorationTester
    private val document by lazy { File.createTempFile("locked-picker", ".afbk", context.cacheDir) }
    private class RecordingRegistry : ActivityResultRegistry() {
        var request: Int? = null
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) { request = requestCode }
    }
    private var registry = RecordingRegistry()
    private lateinit var pickers: FinanceDocumentPickers
    private val pending = PendingDocumentResults()
    @After fun cleanup() { compose.runOnIdle { pickers.close(); store.clear() }; document.delete() }
    private fun start() {
        compose.runOnIdle {
            model = FinanceViewModel(Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build(), context)
            store.put("finance", model)
            lock.complete(lock.beginAuthentication()!!, true)
        }
        pickers = FinanceDocumentPickers(registry, pending::receive)
        restoration = StateRestorationTester(compose)
        restoration.setContent {
            revision.intValue
            LaunchedEffect(lock.canShowFinance, pending.pending) {
                if (lock.canShowFinance && pending.pending.isNotEmpty()) {
                    model.busy.first { !it }
                    pending.deliverNext { lock.canShowFinance && handleDocumentResult(model, context.contentResolver, it) }
                }
            }
            MaterialTheme { LockGate(lock.canShowFinance, lock.authenticating, null, {}) {
                FinanceScreen(model, pickers)
            } }
        }
        compose.waitUntil(10_000) { model.snapshot.value != null }
    }
    private fun returnWhileLockedThenUnlock() {
        val request = requireNotNull(registry.request)
        compose.runOnIdle { lock.background(); revision.intValue++ }
        compose.onNodeWithText("Dashboard").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            val state = Bundle()
            registry.onSaveInstanceState(state)
            pickers.close()
            registry = RecordingRegistry().also { it.onRestoreInstanceState(state) }
            pickers = FinanceDocumentPickers(registry, pending::receive)
            registry.dispatchResult(request, Uri.fromFile(document))
        }
        compose.runOnIdle {
            assertNull(model.backupNotice.value)
            assertNull(model.error.value)
            assertFalse(model.encryptedPasswordRequired.value)
            lock.complete(lock.beginAuthentication()!!, true)
            revision.intValue++
        }
        compose.onNodeWithText("Dashboard").assertIsDisplayed()
    }
    private fun encryptedDocument() {
        val source = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        val json = try { runBlocking { FinanceBackupService(source).exportJson() } } finally { source.close() }
        document.writeBytes(EncryptedBackupCodec.encrypt(json, "Test-only password 123".toCharArray()))
    }
    @Test fun busyBeforeUnlockDoesNotDiscardReturnedUri() {
        start()
        encryptedDocument()
        compose.runOnIdle {
            pickers.openBackup()
            val request = registry.request!!
            lock.background(); revision.intValue++
            registry.dispatchResult(request, Uri.fromFile(document))
            model.prepareEncryptedExport("Test-only password 123".toCharArray())
            assertTrue(model.busy.value)
            lock.complete(lock.beginAuthentication()!!, true); revision.intValue++
        }
        compose.waitUntil(30_000) { model.encryptedPasswordRequired.value && !model.busy.value }
    }
    @Test fun twoQueuedResultsAreProcessedWithoutDroppingTheBusySecondHandler() {
        start()
        encryptedDocument()
        compose.runOnIdle {
            pickers.openBackup(); val backup = registry.request!!
            pickers.openModel(); val ai = registry.request!!
            lock.background(); revision.intValue++
            registry.dispatchResult(backup, Uri.fromFile(document))
            registry.dispatchResult(ai, Uri.fromFile(document))
            lock.complete(lock.beginAuthentication()!!, true); revision.intValue++
        }
        compose.waitUntil(10_000) { model.encryptedPasswordRequired.value && model.error.value != null && !model.busy.value }
    }
    @Test fun exportResultSurvivesLockAndRecreation() {
        start()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Export encrypted backup").performScrollTo().performClick()
        compose.onNodeWithText("Backup password").performTextInput("Test-only password 123")
        compose.onNodeWithText("Confirm password").performTextInput("Test-only password 123")
        compose.onNodeWithText("Encrypt and choose file").performClick()
        compose.waitUntil(30_000) { registry.request != null && !model.busy.value }
        returnWhileLockedThenUnlock()
        compose.waitUntil(10_000) { model.backupNotice.value != null && !model.busy.value }
        assertTrue(EncryptedBackupCodec.isEncrypted(document.readBytes()))
    }
    @Test fun importResultSurvivesLockAndRecreationWithoutReadingWhileLocked() {
        start()
        val source = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        val json = try { runBlocking { FinanceBackupService(source).exportJson() } } finally { source.close() }
        document.writeBytes(EncryptedBackupCodec.encrypt(json, "Test-only password 123".toCharArray()))
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Preview backup restore").performScrollTo().performClick()
        returnWhileLockedThenUnlock()
        compose.waitUntil(10_000) { model.encryptedPasswordRequired.value && !model.busy.value }
    }
    @Test fun modelPickerResultSurvivesLockAndRecreationWithoutReadingWhileLocked() {
        start()
        compose.onNodeWithText("AI").performScrollTo().performClick()
        compose.onNodeWithText("Import local model").performScrollTo().performClick()
        returnWhileLockedThenUnlock()
        // Invalid test document must reach model validation, rather than be silently lost.
        compose.waitUntil(10_000) { model.error.value != null && !model.busy.value }
        assertFalse(model.modelInstalled.value)
    }
}
