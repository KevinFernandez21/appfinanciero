package com.kevinfernandez.appfinanciero.ui

import java.io.ByteArrayInputStream
import java.nio.charset.CharacterCodingException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupDocumentBytesTest {
    @Test fun readsExactBinaryLimit() {
        val bytes = byteArrayOf(0, 1, -1)
        assertArrayEquals(bytes, BackupDocumentBytes.read(ByteArrayInputStream(bytes), 3))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedFile() { BackupDocumentBytes.read(ByteArrayInputStream(ByteArray(4)), 3) }
    @Test fun decodesUnicodeLegacyJsonWithoutLoss() {
        val text = "{\"note\":\"Ahorro 😀\"}"
        assertEquals(text, BackupDocumentBytes.decodeUtf8(text.toByteArray(Charsets.UTF_8)))
    }
    @Test(expected = CharacterCodingException::class)
    fun rejectsMalformedUtf8RatherThanReplacingFinancialNotes() { BackupDocumentBytes.decodeUtf8(byteArrayOf(-61, 40)) }
}
