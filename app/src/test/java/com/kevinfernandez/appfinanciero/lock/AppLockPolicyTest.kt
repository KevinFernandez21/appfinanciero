package com.kevinfernandez.appfinanciero.lock

import org.junit.Assert.*
import org.junit.Test

class AppLockPolicyTest {
    @Test fun coldStartHonorsOptIn() {
        assertTrue(AppLockPolicy(false).canShowFinance)
        assertFalse(AppLockPolicy(true).canShowFinance)
    }
    @Test fun enableRequiresSuccessfulCurrentAuthentication() {
        val lock = AppLockPolicy(false)
        val request = lock.beginAuthentication(enable = true)!!
        assertFalse(lock.enabled)
        assertFalse(lock.canShowFinance)
        assertTrue(lock.complete(request, true))
        assertTrue(lock.enabled)
        assertTrue(lock.canShowFinance)
    }
    @Test fun cancelEnablingDoesNotEnable() {
        val lock = AppLockPolicy(false)
        lock.complete(lock.beginAuthentication(true)!!, false)
        assertFalse(lock.enabled)
        assertTrue(lock.canShowFinance)
    }
    @Test fun cancellationFailsClosedAndAllowsRetry() {
        val lock = AppLockPolicy(true)
        lock.complete(lock.beginAuthentication()!!, false)
        assertFalse(lock.canShowFinance)
        assertNotNull(lock.beginAuthentication())
    }
    @Test fun backgroundInvalidatesStaleSuccess() {
        val lock = AppLockPolicy(true)
        val request = lock.beginAuthentication()!!
        lock.background()
        assertFalse(lock.complete(request, true))
        assertFalse(lock.canShowFinance)
    }
    @Test fun backgroundRelocksUnlockedSession() {
        val lock = AppLockPolicy(true)
        lock.complete(lock.beginAuthentication()!!, true)
        lock.background()
        assertFalse(lock.canShowFinance)
    }
    @Test fun rotationPreservesUnlockedButInvalidatesPendingRequest() {
        val lock = AppLockPolicy(true)
        val request = lock.beginAuthentication()!!
        lock.cancelPending()
        assertFalse(lock.complete(request, true))
        lock.complete(lock.beginAuthentication()!!, true)
        lock.cancelPending()
        assertTrue(lock.canShowFinance)
    }
    @Test fun duplicateAndOldCallbacksCannotCompleteNewRequest() {
        val lock = AppLockPolicy(true)
        val old = lock.beginAuthentication()!!
        assertNull(lock.beginAuthentication())
        lock.complete(old, false)
        val current = lock.beginAuthentication()!!
        assertFalse(lock.complete(old, true))
        assertFalse(lock.canShowFinance)
        assertTrue(lock.complete(current, true))
        assertFalse(lock.complete(current, true))
    }
    @Test fun disableRequiresUnlockedSession() {
        val lock = AppLockPolicy(true)
        assertFalse(lock.disable())
        lock.complete(lock.beginAuthentication()!!, true)
        assertTrue(lock.disable())
        lock.background()
        assertTrue(lock.canShowFinance)
    }
}
