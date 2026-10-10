package com.kevinfernandez.appfinanciero.ui

import android.net.Uri
import androidx.room.Room
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.kevinfernandez.appfinanciero.backup.EncryptedBackupCodec
import com.kevinfernandez.appfinanciero.backup.FinanceBackupService
import com.kevinfernandez.appfinanciero.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class BackupWorkflowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var source: FinanceDatabase
    private lateinit var target: FinanceDatabase
    private lateinit var model: FinanceViewModel
    private val store = ViewModelStore()
    private val files = mutableListOf<File>()
    private val password = "Test-only password 123"

    @Before fun setup() {
        source = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        runBlocking { source.financeDao().insertAccount(AccountEntity("cash", "Cash", 12345, "USD")) }
        main { model = FinanceViewModel(target, context); store.put("backup", model) }
        awaitIdle()
    }
    @After fun cleanup() { main { store.clear() }; source.close(); files.forEach { it.delete() } }
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun awaitIdle() {
        val end = System.currentTimeMillis() + 30_000
        while (model.busy.value && System.currentTimeMillis() < end) Thread.sleep(20)
        assertFalse("Backup operation timed out", model.busy.value)
    }
    private fun file(bytes: ByteArray): Uri = Uri.fromFile(File.createTempFile("backup-workflow", ".afbk", context.cacheDir).also { files.add(it); it.writeBytes(bytes) })
    private fun encrypted(): Uri = file(EncryptedBackupCodec.encrypt(runBlocking { FinanceBackupService(source).exportJson() }, password.toCharArray()))
    private fun assertEmpty() = runBlocking { assertTrue(target.financeDao().getAccounts().isEmpty()) }

    @Test fun wrongPasswordRetriesSameDocumentThenPreviewRequiresConfirmation() {
        val uri = encrypted()
        main { model.previewBackup(context.contentResolver, uri) }; awaitIdle()
        assertTrue(model.encryptedPasswordRequired.value)
        val wrong = "wrong".toCharArray()
        main { model.decryptBackup(wrong) }; awaitIdle()
        assertTrue(wrong.all { it == '\u0000' })
        assertEquals("Invalid encrypted backup or password", model.error.value)
        assertTrue(model.encryptedPasswordRequired.value)
        assertNull(model.restorePreview.value); assertEmpty()
        val correct = password.toCharArray()
        main { model.decryptBackup(correct) }; awaitIdle()
        assertTrue(correct.all { it == '\u0000' })
        assertNull(model.error.value)
        assertEquals(1, model.restorePreview.value?.recordCount)
        assertFalse(model.encryptedPasswordRequired.value); assertEmpty()
        main { model.confirmRestore() }; awaitIdle()
        runBlocking { assertEquals(source.financeDao().getAccounts(), target.financeDao().getAccounts()) }
        assertNull(model.restorePreview.value)
        main { model.confirmRestore() }; awaitIdle()
        runBlocking { assertEquals(1, target.financeDao().getAccounts().size) }
    }

    @Test fun cancellingEncryptedImportAndLegacyPreviewNeverWrites() {
        val uri = encrypted()
        main { model.previewBackup(context.contentResolver, uri) }; awaitIdle()
        main { model.cancelRestore() }
        assertFalse(model.encryptedPasswordRequired.value)
        main { model.decryptBackup(password.toCharArray()) }; awaitIdle()
        assertEquals("Select an encrypted backup first", model.error.value)
        val legacy = file(runBlocking { FinanceBackupService(source).exportJson() }.toByteArray())
        main { model.previewBackup(context.contentResolver, legacy) }; awaitIdle()
        assertTrue(model.legacyRestore.value); assertEquals(1, model.restorePreview.value?.recordCount)
        assertEmpty()
        main { model.cancelRestore(); model.confirmRestore() }; awaitIdle()
        assertNull(model.restorePreview.value); assertEmpty()
    }

    @Test fun pickerResultWithoutPreparedExportExplainsProcessDeathRecovery() {
        val destination = file(byteArrayOf())
        main { model.exportPreparedBackup(context.contentResolver, destination) }; awaitIdle()
        assertEquals("Backup preparation was lost. Start a new encrypted export; no password was saved.", model.error.value)
        assertEquals(0L, File(destination.path!!).length())
    }
}
