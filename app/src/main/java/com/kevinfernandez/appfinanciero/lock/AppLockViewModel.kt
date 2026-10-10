package com.kevinfernandez.appfinanciero.lock

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

/** Only the lock setting goes to disk; no authenticated session is saved. */
class AppLockViewModel(application: Application, private val store: LockPreferenceStore) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, object : LockPreferenceStore {
        private val preferences = application.getSharedPreferences("app_lock", 0)
        override fun read() = preferences.getBoolean("enabled", true)
        override fun write(enabled: Boolean) = preferences.edit().putBoolean("enabled", enabled).commit()
    })
    val policy = AppLockPolicy(runCatching { store.read() }.getOrDefault(true))
    var revision by mutableStateOf(0)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private var enabling = false

    private fun begin(enable: Boolean): Long? {
        val request = policy.beginAuthentication(enable) ?: return null
        enabling = enable
        message = null
        revision++
        return request
    }
    fun complete(request: Long, success: Boolean, error: String? = null) {
        // Check the current request before writing the lock setting.
        if (!policy.authenticating || request != currentRequest) return
        val stored = !success || !enabling || write(true)
        policy.complete(request, success && stored)
        currentRequest = null
        enabling = false
        message = if (!stored) "Could not save app lock. It has not been enabled." else error
        revision++
    }
    var currentRequest: Long? = null
        private set
    fun request(enable: Boolean): Long? = begin(enable)?.also { currentRequest = it }
    fun stopped(rotation: Boolean) {
        if (rotation) policy.cancelPending() else policy.background()
        currentRequest = null
        revision++
    }
    private fun write(enabled: Boolean) = runCatching { store.write(enabled) }.getOrDefault(false)
    fun disable() {
        if (!policy.canShowFinance) return
        if (write(false)) policy.disable()
        else {
            // commit() updates the preference cache before disk I/O. Restore it even
            // if the compensating disk write fails; fresh ViewModels must stay locked.
            write(true)
            message = "Could not save the setting. App lock remains enabled."
        }
        revision++
    }
}
