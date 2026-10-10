package com.kevinfernandez.appfinanciero.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

/** Runs against the device's PBKDF2-SHA256 and AES-GCM providers (minimum API 26). */
@RunWith(AndroidJUnit4::class)
class EncryptedBackupCodecAndroidTest {
    @Test fun platformDecryptsIndependentPortableUnicodeReference() {
        // Generated independently with Python hashlib + AESGCM; never reuse this test salt/nonce in production.
        val fixture = Base64.getDecoder().decode("QUZCSwEBAQAJJ8AAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobAAAASqxpTfGHluuePXXHRzBpITZfIS6U+/iepznx0rklyPogvvhyLZPHDqFOWfJKqPMmQXs/WubFvUp+JbeNLHB6s9pR0C8Ko+/rxVv+tOaE5AzFTkRKltoXoOt4KQ==")
        val password = "portable private passphrase 🔒".toCharArray()
        try {
            assertEquals("""{"formatVersion":2,"note":"café 東京","amountMinor":"9007199254740993"}""",
                EncryptedBackupCodec.decrypt(fixture, password))
        } finally { password.fill('\u0000') }
    }

    @Test fun platformCryptoProvidersRoundTripPortableEnvelope() {
        val password = "portable private passphrase 🔒".toCharArray()
        try {
            val json = """{"formatVersion":2,"note":"café 東京","amountMinor":"9007199254740993"}"""
            assertEquals(json, EncryptedBackupCodec.decrypt(EncryptedBackupCodec.encrypt(json, password), password))
        } finally { password.fill('\u0000') }
    }
}
