package com.kevinfernandez.appfinanciero.lock

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.kevinfernandez.appfinanciero.MainActivity
import org.junit.*
import org.junit.Assert.*

class LockActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val application get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun enableLock() { application.getSharedPreferences("app_lock", 0).edit().putBoolean("enabled", true).commit() }
    @After fun clearLock() { application.getSharedPreferences("app_lock", 0).edit().clear().commit() }

    @Test fun corruptFlagStartsLocked() {
        application.getSharedPreferences("app_lock", 0).edit().putString("enabled", "invalid").commit()
        assertFalse(AppLockViewModel(application).policy.canShowFinance)
    }
    @Test fun missingFlagStartsLocked() {
        clearLock()
        assertFalse(AppLockViewModel(application).policy.canShowFinance)
    }
    @Test fun failedDisableRestoresCacheEvenWhenRollbackCommitFails() {
        val store = object : LockPreferenceStore {
            var cached = true
            override fun read() = cached
            override fun write(enabled: Boolean): Boolean { cached = enabled; return false }
        }
        val lock = AppLockViewModel(application, store)
        lock.complete(lock.request(false)!!, true)
        lock.disable()
        assertTrue(lock.policy.enabled)
        assertFalse(AppLockViewModel(application, store).policy.canShowFinance)
    }
    @Test fun coldStartAndRecreationNeverExposeFinance() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText("AppAhorro is locked").assertIsDisplayed()
            compose.onNodeWithText("Dashboard").assertDoesNotExist()
            scenario.recreate()
            compose.onNodeWithText("AppAhorro is locked").assertIsDisplayed()
            compose.onNodeWithText("Dashboard").assertDoesNotExist()
        }
    }
    @Test fun fakeSuccessfulAuthSurvivesRotationButBackgroundRelocks() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val lock = ViewModelProvider(it)[AppLockViewModel::class.java]
                lock.complete(lock.request(false)!!, true)
            }
            compose.onNodeWithText("Dashboard").assertIsDisplayed()
            scenario.recreate()
            compose.onNodeWithText("Dashboard").assertIsDisplayed()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithText("AppAhorro is locked").assertIsDisplayed()
            compose.onNodeWithText("Dashboard").assertDoesNotExist()
        }
    }
    @Test fun enablingPersistsOnlyAfterSuccessAndColdSessionIsLocked() {
        application.getSharedPreferences("app_lock", 0).edit().putBoolean("enabled", false).commit()
        val first = AppLockViewModel(application)
        val canceled = first.request(true)!!
        first.complete(canceled, false)
        assertFalse(AppLockViewModel(application).policy.enabled)
        val stale = first.request(true)!!
        first.stopped(false)
        first.complete(stale, true)
        assertFalse(AppLockViewModel(application).policy.enabled)
        first.complete(first.request(true)!!, true)
        assertTrue(first.policy.canShowFinance)
        val cold = AppLockViewModel(application)
        assertTrue(cold.policy.enabled)
        assertFalse(cold.policy.canShowFinance)
        assertFalse(cold.policy.disable())
    }
}
