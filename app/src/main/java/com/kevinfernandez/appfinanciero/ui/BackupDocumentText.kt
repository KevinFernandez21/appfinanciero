package com.kevinfernandez.appfinanciero.ui

import java.io.Reader

/** Reject oversized content before materializing an unbounded document in memory. */
internal object BackupDocumentText {
    fun read(reader: Reader, maximumCharacters: Int): String {
        require(maximumCharacters >= 0)
        val result = StringBuilder()
        val buffer = CharArray(4096)
        while (true) {
            val count = reader.read(buffer)
            if (count == -1) return result.toString()
            require(count <= maximumCharacters - result.length) { "Backup exceeds supported size" }
            result.append(buffer, 0, count)
        }
    }
}
