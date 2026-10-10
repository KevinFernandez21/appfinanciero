# On-device app lock

App lock is **on by default**, including when the preference is missing or corrupt.
Configure an Android screen lock or strong biometric to unlock. You can explicitly
disable it in Settings only after authentication. Re-enabling requires a successful device challenge;
canceling or failing does not enable the setting. Disable it only from an unlocked
session. No password, biometric data or unlocked state is stored by the app.

When enabled, cold launches are locked. Leaving the Activity relocks immediately,
including external document pickers. Rotation retains an already authenticated
in-memory session, but cancels an in-flight challenge; retry after rotation.
Credential confirmation is an intentional system handoff while finance content is
removed. Late, duplicate and canceled callbacks cannot unlock a newer session.
Authentication failure or cancellation leaves the gate closed, with explicit retry.
If authentication becomes unavailable, configure authentication in Android Settings
and retry; there is no fallback that silently bypasses the lock.

Android 30+ uses AndroidX BiometricPrompt 1.1.0 with `BIOMETRIC_STRONG |
DEVICE_CREDENTIAL`. Android 26–29 uses strong biometrics when available, with an
explicit screen-lock fallback via KeyguardManager for user choice or biometric lockout; it never passes unsupported
strong-plus-credential masks on Android 28–29. Android itself owns credential UI.

Only the lock-enabled Boolean is synchronously committed to private SharedPreferences,
so the gate has no asynchronous loading window. Failed disabling restores the
enabled cache with a compensating commit, even if that disk write fails too. A fresh ViewModel always starts
locked if enabled. FinanceScreen and its ViewModel are not created before unlock.
FLAG_SECURE prevents app-window screenshots and Recents snapshots.
Backup export/import and model picker callbacks use stable Activity-owned registry
keys. Returned URIs are queued in memory across rotation and processed only after
unlock; financial content stays removed while locked. Queued results wait for the
finance operation to become idle and are acknowledged one at a time only after
the handler accepts them; busy handlers never discard a returned URI. A process death after a URI
has already been received may require selecting the document again. Prepared export
bytes are never restored across process death; restart export without reusing a saved password.

**This is a UI access gate, NOT database encryption.** Room files remain unencrypted.
It does not defend against root, malware, a compromised OS, filesystem extraction,
or a person who knows the device credential. Encrypted backup files have a separate
password and protection model. No network or model is needed for authentication.

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

Policy unit tests cover opt-in, enable/cancel, background locking, rotation policy,
retry and stale callbacks. Compose tests use fake authentication and verify private
content is never composed while locked. Activity tests cover cold start, recreation,
fake successful authentication, background locking and persisted opt-in.

Real biometric success, PIN/pattern/password confirmation, credential handoff,
enrollment changes, lockout, process death during authentication, and API 26–30
compatibility require device-matrix verification; fake tests do not prove them.

References: [AndroidX Biometric releases](https://developer.android.com/jetpack/androidx/releases/biometric),
[PromptInfo authenticators](https://developer.android.com/reference/androidx/biometric/BiometricPrompt.PromptInfo.Builder),
[KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager).
