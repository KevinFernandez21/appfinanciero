package com.kevinfernandez.appfinanciero.backup

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class EncryptedBackupCodecTest {
    private val password = "a long unique backup passphrase".toCharArray()
    private val json = """{"formatVersion":2,"note":"Private: café 東京 🔒","amountMinor":"9007199254740993"}"""

    @Test fun decryptsIndependentPortableReferenceWithUnicodePassword() {
        // Python hashlib.pbkdf2_hmac + cryptography AESGCM reference, fixed salt/nonce for this fixture only.
        val fixture = Base64.getDecoder().decode("QUZCSwEBAQAJJ8AAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobAAAASqxpTfGHluuePXXHRzBpITZfIS6U+/iepznx0rklyPogvvhyLZPHDqFOWfJKqPMmQXs/WubFvUp+JbeNLHB6s9pR0C8Ko+/rxVv+tOaE5AzFTkRKltoXoOt4KQ==")
        val expected = """{"formatVersion":2,"note":"café 東京","amountMinor":"9007199254740993"}"""
        assertEquals(expected, EncryptedBackupCodec.decrypt(fixture, "portable private passphrase 🔒".toCharArray()))
    }

    @Test fun roundTripPreservesUnicodeJsonAndDoesNotMutatePassword() {
        val before = password.copyOf()
        val encrypted = EncryptedBackupCodec.encrypt(json, password)
        assertEquals(json, EncryptedBackupCodec.decrypt(encrypted, password))
        assertArrayEquals(before, password)
        assertTrue(EncryptedBackupCodec.isEncrypted(encrypted))
        assertFalse(EncryptedBackupCodec.isEncrypted(json.toByteArray()))
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("Private"))
    }

    @Test fun wrongPasswordTamperAndTruncationFailWithSameSafeMessage() {
        val encrypted = EncryptedBackupCodec.encrypt(json, password)
        rejected { EncryptedBackupCodec.decrypt(encrypted, "wrong password".toCharArray()) }
        for (index in listOf(0, 4, 7, 11, 27, 39, 43, encrypted.lastIndex)) {
            val changed = encrypted.copyOf().apply { this[index] = (this[index].toInt() xor 1).toByte() }
            rejected { EncryptedBackupCodec.decrypt(changed, password) }
        }
        rejected { EncryptedBackupCodec.decrypt(encrypted.copyOf(encrypted.size - 1), password) }
        rejected { EncryptedBackupCodec.decrypt(encrypted + byteArrayOf(0), password) }
    }

    @Test fun everyExportUsesFreshSaltNonceAndCiphertext() {
        val first = EncryptedBackupCodec.encrypt(json, password)
        val second = EncryptedBackupCodec.encrypt(json, password)
        assertFalse(first.contentEquals(second))
        assertFalse(first.copyOfRange(11, 27).contentEquals(second.copyOfRange(11, 27)))
        assertFalse(first.copyOfRange(27, 39).contentEquals(second.copyOfRange(27, 39)))
        assertEquals(json, EncryptedBackupCodec.decrypt(first, password))
        assertEquals(json, EncryptedBackupCodec.decrypt(second, password))
    }

    @Test fun sizeAndPasswordBoundsAreCheckedBeforeExpensiveCrypto() {
        assertThrows(IllegalArgumentException::class.java) { EncryptedBackupCodec.encrypt(json, charArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { EncryptedBackupCodec.encrypt(json, "short".toCharArray()) }
        assertThrows(IllegalArgumentException::class.java) { EncryptedBackupCodec.encrypt(json, CharArray(1025) { 'x' }) }
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackupCodec.encrypt("x".repeat(FinanceBackupService.MAX_JSON_CHARACTERS + 1), password)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackupCodec.encrypt("€".repeat(FinanceBackupService.MAX_JSON_CHARACTERS + 1), password)
        }
        assertThrows(IllegalArgumentException::class.java) { EncryptedBackupCodec.encrypt("\uD800", password) }
        rejected { EncryptedBackupCodec.decrypt(ByteArray(EncryptedBackupCodec.MAX_ENVELOPE_BYTES + 1), password) }
        rejected { EncryptedBackupCodec.decrypt(byteArrayOf(), password) }
    }

    private fun rejected(block: () -> Unit) {
        val error = assertThrows(IllegalArgumentException::class.java, block)
        assertEquals("Invalid encrypted backup or password", error.message)
        assertNull(error.cause)
    }
}
