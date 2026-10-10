package com.kevinfernandez.appfinanciero.lock

/** In-memory session only; never restore an unlocked state from saved instance state. */
class AppLockPolicy(enabled: Boolean) {
    var enabled = enabled
        private set
    private var unlocked = !enabled
    private var generation = 0L
    private var pending: Long? = null
    private var enabling = false
    val authenticating get() = pending != null
    val canShowFinance get() = (!enabled || unlocked) && !authenticating

    fun beginAuthentication(enable: Boolean = false): Long? {
        if (authenticating) return null
        enabling = enable
        return (++generation).also { pending = it }
    }
    fun complete(request: Long, success: Boolean): Boolean {
        if (request != pending) return false
        pending = null
        if (success) {
            if (enabling) enabled = true
            unlocked = true
        }
        enabling = false
        return true
    }
    fun cancelPending() { pending = null; enabling = false; generation++ }
    fun background() { cancelPending(); unlocked = !enabled }
    fun disable(): Boolean {
        if (!canShowFinance) return false
        enabled = false
        unlocked = true
        return true
    }
}
