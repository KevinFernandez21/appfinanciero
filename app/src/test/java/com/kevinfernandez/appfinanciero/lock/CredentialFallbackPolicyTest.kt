package com.kevinfernandez.appfinanciero.lock

import org.junit.Assert.*
import org.junit.Test

class CredentialFallbackPolicyTest {
    @Test fun credentialHandoffRejectsOldBiometricCallbacks() {
        assertTrue(acceptsBiometricCallback(7, 7, false))
        assertFalse(acceptsBiometricCallback(7, 7, true))
        assertFalse(acceptsBiometricCallback(6, 7, false))
        assertFalse(acceptsBiometricCallback(7, null, false))
    }
    @Test fun lockoutsOnLegacyAndroidRetainCredentialAccess() {
        for (api in 26..29) {
            assertTrue(shouldUseCredentialFallback(api, true, AuthenticationError.LOCKOUT))
            assertTrue(shouldUseCredentialFallback(api, true, AuthenticationError.PERMANENT_LOCKOUT))
        }
    }
    @Test fun negativeButtonRequiresConfiguredSecureCredential() {
        assertTrue(shouldUseCredentialFallback(29, true, AuthenticationError.NEGATIVE_BUTTON))
        assertFalse(shouldUseCredentialFallback(29, false, AuthenticationError.NEGATIVE_BUTTON))
    }
    @Test fun modernPromptAndCancellationDoNotLaunchLegacyCredential() {
        assertFalse(shouldUseCredentialFallback(30, true, AuthenticationError.LOCKOUT))
        assertFalse(shouldUseCredentialFallback(29, true, AuthenticationError.OTHER))
    }
}
