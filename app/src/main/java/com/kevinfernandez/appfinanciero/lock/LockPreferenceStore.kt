package com.kevinfernandez.appfinanciero.lock

interface LockPreferenceStore {
    fun read(): Boolean
    fun write(enabled: Boolean): Boolean
}
