package com.kevinfernandez.appfinanciero.ui

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kevinfernandez.appfinanciero.backup.EncryptedBackupCodec
import com.kevinfernandez.appfinanciero.data.FinanceDatabase
import com.kevinfernandez.appfinanciero.data.AccountEntity
import com.kevinfernandez.appfinanciero.backup.FinanceBackupService
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class BackupPickerRestorationTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ViewModelStore()
    private lateinit var model: FinanceViewModel
    private val destination by lazy { File.createTempFile("picker-result", ".afbk", context.cacheDir) }
    private val registry = object : ActivityResultRegistry() {
        var request: Int? = null
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) { request = requestCode }
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; destination.delete() }

    @Test fun exportPickerCancellationAndResultSurviveCompositionRestoration() {
        compose.runOnIdle {
            model = FinanceViewModel(Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build(), context)
            store.put("model", model)
        }
        val restoration = StateRestorationTester(compose)
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { MaterialTheme { FinanceScreen(model) } }
        }
        compose.waitUntil(10_000) { model.snapshot.value != null }
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        fun prepare() {
            compose.onNodeWithText("Export encrypted backup").performScrollTo().performClick()
            compose.onNodeWithText("Backup password").performTextInput("Test-only password 123")
            compose.onNodeWithText("Confirm password").performTextInput("Test-only password 123")
            compose.onNodeWithText("Encrypt and choose file").performClick()
            compose.waitUntil(30_000) { registry.request != null && !model.busy.value }
        }
        prepare()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { registry.dispatchResult<Uri?>(registry.request!!, null); registry.request = null }
        assertFalse(model.exportReady.value)
        assertNull(model.backupNotice.value)
        prepare()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { registry.dispatchResult(registry.request!!, Uri.fromFile(destination)) }
        compose.waitUntil(30_000) { !model.busy.value && model.backupNotice.value != null }
        val bytes = destination.readBytes()
        assertTrue(EncryptedBackupCodec.isEncrypted(bytes))
        assertTrue(EncryptedBackupCodec.decrypt(bytes, "Test-only password 123".toCharArray()).contains("formatVersion"))
        compose.onNodeWithText("Backup password").assertDoesNotExist()
    }

    @Test fun importedResultAfterRestorationRequiresPasswordThenExplicitConfirmButton() {
        val source = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        val json = try { runBlocking {
            source.financeDao().insertAccount(AccountEntity("cash", "Cash", 12345, "USD"))
            FinanceBackupService(source).exportJson()
        } } finally { source.close() }
        destination.writeBytes(EncryptedBackupCodec.encrypt(json, "Test-only password 123".toCharArray()))
        val target = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        compose.runOnIdle { model = FinanceViewModel(target, context); store.put("model", model) }
        val restoration = StateRestorationTester(compose)
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { MaterialTheme { FinanceScreen(model) } }
        }
        compose.waitUntil(10_000) { model.snapshot.value != null }
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Preview backup restore").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { registry.dispatchResult(registry.request!!, Uri.fromFile(destination)) }
        compose.waitUntil(10_000) { model.encryptedPasswordRequired.value && !model.busy.value }
        compose.onNodeWithText("Backup password").performTextInput("wrong")
        compose.onNodeWithText("Decrypt preview").performClick()
        compose.waitUntil(30_000) { model.error.value != null && !model.busy.value }
        assertTrue(model.encryptedPasswordRequired.value)
        compose.onNodeWithText("Backup password").performTextInput("Test-only password 123")
        compose.onNodeWithText("Decrypt preview").performClick()
        compose.waitUntil(30_000) { model.restorePreview.value != null && !model.busy.value }
        runBlocking { assertTrue(target.financeDao().getAccounts().isEmpty()) }
        compose.onNodeWithText("Confirm restore").assertIsDisplayed()
        compose.onNodeWithText("Encrypted backup authenticated and validated. Its password is not retained.").assertIsDisplayed()
        compose.onNodeWithText("Confirm import").performClick()
        compose.waitUntil(10_000) { model.restorePreview.value == null && !model.busy.value }
        runBlocking { assertEquals(12345L, target.financeDao().getAccounts().single().openingBalanceMinor) }
        compose.onNodeWithText("Backup password").assertDoesNotExist()
    }
}
