package com.kevinfernandez.appfinanciero.ui

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupDocumentTextTest {
    @Test fun readsExactLimitWithoutTruncating() {
        assertEquals("abc", BackupDocumentText.read(StringReader("abc"), 3))
    }
    @Test fun readsEmptyDocument() {
        assertEquals("", BackupDocumentText.read(StringReader(""), 3))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedDocumentRatherThanTruncating() { BackupDocumentText.read(StringReader("abcd"), 3) }
}
