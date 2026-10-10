package com.kevinfernandez.appfinanciero.lock

enum class AuthenticationError { NEGATIVE_BUTTON, LOCKOUT, PERMANENT_LOCKOUT, OTHER }
fun shouldUseCredentialFallback(api: Int, deviceSecure: Boolean, error: AuthenticationError) =
    api < 30 && deviceSecure && error in setOf(
        AuthenticationError.NEGATIVE_BUTTON, AuthenticationError.LOCKOUT, AuthenticationError.PERMANENT_LOCKOUT)


fun acceptsBiometricCallback(request: Long, active: Long?, credentialHandoff: Boolean) = request == active && !credentialHandoff
