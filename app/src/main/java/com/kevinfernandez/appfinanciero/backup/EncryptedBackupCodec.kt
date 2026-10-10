package com.kevinfernandez.appfinanciero.backup

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable password encryption, independent of device Keystore and Android framework classes. */
object EncryptedBackupCodec {
    private const val HEADER_BYTES = 43
    private const val TAG_BYTES = 16
    const val MIN_PASSWORD_LENGTH = 12
    const val MAX_PLAINTEXT_BYTES = 4 * FinanceBackupService.MAX_JSON_CHARACTERS
    const val MAX_ENVELOPE_BYTES = MAX_PLAINTEXT_BYTES + HEADER_BYTES + TAG_BYTES
    private const val ITERATIONS = 600_000
    private const val MAX_PASSWORD_LENGTH = 1024
    private const val FAILURE = "Invalid encrypted backup or password"
    private val magic = byteArrayOf('A'.code.toByte(), 'F'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private val random = SecureRandom()

    /** Detection only. A matching prefix does NOT establish authenticity or validity. */
    fun isEncrypted(bytes: ByteArray): Boolean = bytes.size >= magic.size && magic.indices.all { bytes[it] == magic[it] }

    /** Caller owns the password array and must clear it when finished. Run away from the UI thread. */
    fun encrypt(json: String, password: CharArray): ByteArray {
        require(password.size in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) { "Use a passphrase of 12 to 1024 characters" }
        require(json.length <= FinanceBackupService.MAX_JSON_CHARACTERS) { "Backup exceeds supported size" }
        val plaintext = encodeUtf8(json)
        try {
            require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Backup exceeds supported size" }
            val salt = ByteArray(16).also(random::nextBytes)
            val nonce = ByteArray(12).also(random::nextBytes)
            val header = ByteBuffer.allocate(HEADER_BYTES).put(magic)
                .put(1.toByte()).put(1.toByte()).put(1.toByte()).putInt(ITERATIONS)
                .put(salt).put(nonce).putInt(plaintext.size).array()
            val key = deriveKey(password, salt)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD(header)
                return header + cipher.doFinal(plaintext)
            } finally { key.fill(0) }
        } finally { plaintext.fill(0) }
    }

    /** No plaintext is returned before GCM authentication; every rejected import has a safe error. */
    fun decrypt(envelope: ByteArray, password: CharArray): String {
        try {
            require(password.size in 1..MAX_PASSWORD_LENGTH)
            // Check every attacker-controlled size and algorithm parameter before KDF or large allocation.
            require(envelope.size in (HEADER_BYTES + TAG_BYTES)..MAX_ENVELOPE_BYTES)
            require(isEncrypted(envelope))
            val header = envelope.copyOfRange(0, HEADER_BYTES)
            val fields = ByteBuffer.wrap(header).apply { position(magic.size) }
            require(fields.get().toInt() == 1 && fields.get().toInt() == 1 && fields.get().toInt() == 1)
            require(fields.int == ITERATIONS)
            val salt = ByteArray(16).also(fields::get)
            val nonce = ByteArray(12).also(fields::get)
            val length = fields.int
            require(length in 0..MAX_PLAINTEXT_BYTES)
            require(envelope.size == HEADER_BYTES + length + TAG_BYTES)
            val key = deriveKey(password, salt)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD(header)
                // A single authenticated operation: never expose a Cipher.update() plaintext prefix.
                val plaintext = cipher.doFinal(envelope, HEADER_BYTES, envelope.size - HEADER_BYTES)
                try {
                    require(plaintext.size == length)
                    val decoded = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(plaintext))
                    val json = try { decoded.toString() }
                    finally { if (decoded.hasArray()) decoded.array().fill('\u0000') }
                    require(json.length <= FinanceBackupService.MAX_JSON_CHARACTERS)
                    return json
                } finally { plaintext.fill(0) }
            } finally { key.fill(0) }
        } catch (_: Exception) {
            // Deliberately discard provider diagnostics and causes; they may include sensitive input.
            throw IllegalArgumentException(FAILURE)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun encodeUtf8(json: String): ByteArray {
        try {
            val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(json))
            return try { ByteArray(encoded.remaining()).also(encoded::get) }
            finally { if (encoded.hasArray()) encoded.array().fill(0) }
        } catch (_: Exception) { throw IllegalArgumentException("Backup is not valid UTF-8 text") }
    }
}
