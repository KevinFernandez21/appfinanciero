package com.kevinfernandez.appfinanciero.lock

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Platform adapter. Callback tokens belong to one Activity instance only. */
class DeviceAuthenticator(private val activity: FragmentActivity, private val result: (Long, Boolean, String?) -> Unit) {
    private val manager = BiometricManager.from(activity)
    private val keyguard = activity.getSystemService(KeyguardManager::class.java)
    private var prompt: BiometricPrompt? = null
    private var activeRequest: Long? = null
    private var credentialRequest: Long? = null
    val handingOffCredential get() = credentialRequest != null
    private val credential = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val request = credentialRequest ?: return@registerForActivityResult
        credentialRequest = null
        activeRequest = null
        result(request, it.resultCode == Activity.RESULT_OK,
            if (it.resultCode == Activity.RESULT_OK) null else "Authentication canceled. Try again.")
    }

    fun authenticate(request: Long) {
        activeRequest = request
        if (Build.VERSION.SDK_INT >= 30) {
            if (manager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
                unavailable(request)
                return
            }
            biometric(request, BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        } else if (manager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            biometric(request, BIOMETRIC_STRONG)
        } else screenLock(request)
    }
    private fun biometric(request: Long, authenticators: Int) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(authentication: BiometricPrompt.AuthenticationResult) {
                if (!acceptsBiometricCallback(request, activeRequest, handingOffCredential)) return
                activeRequest = null
                result(request, true, null)
            }
            override fun onAuthenticationError(code: Int, text: CharSequence) {
                if (!acceptsBiometricCallback(request, activeRequest, handingOffCredential)) return
                val error = when (code) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> AuthenticationError.NEGATIVE_BUTTON
                    BiometricPrompt.ERROR_LOCKOUT -> AuthenticationError.LOCKOUT
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> AuthenticationError.PERMANENT_LOCKOUT
                    else -> AuthenticationError.OTHER
                }
                if (shouldUseCredentialFallback(Build.VERSION.SDK_INT, keyguard.isDeviceSecure, error)) {
                    screenLock(request)
                } else {
                    activeRequest = null
                    result(request, false, "Authentication canceled or unavailable. Try again.")
                }
            }
            // Failed recognition keeps the platform prompt open; never grants access.
        }
        prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock AppAhorro")
            .setSubtitle("Confirm your identity to access your ledger")
            .setAllowedAuthenticators(authenticators)
        if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText(if (keyguard.isDeviceSecure) "Use screen lock" else "Cancel")
        runCatching { prompt!!.authenticate(info.build()) }.onFailure { unavailable(request) }
    }
    @Suppress("DEPRECATION")
    private fun screenLock(request: Long) {
        val intent = if (keyguard.isDeviceSecure) keyguard.createConfirmDeviceCredentialIntent("Unlock AppAhorro", "Confirm your device screen lock") else null
        if (intent == null) { unavailable(request); return }
        credentialRequest = request
        runCatching { credential.launch(intent) }.onFailure {
            credentialRequest = null
            unavailable(request)
        }
    }
    private fun unavailable(request: Long) {
        activeRequest = null
        result(request, false, "Authentication is unavailable. Configure a PIN, pattern, password or strong biometric in Android Settings, then retry. App lock is never bypassed.")
    }
    fun cancel() {
        credentialRequest = null
        activeRequest = null
        prompt?.cancelAuthentication()
        prompt = null
    }
}
