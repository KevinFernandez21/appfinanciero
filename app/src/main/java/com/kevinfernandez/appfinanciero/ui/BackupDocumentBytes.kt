package com.kevinfernandez.appfinanciero.ui

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal object BackupDocumentBytes {
    fun read(input: InputStream, maximumBytes: Int): ByteArray {
        require(maximumBytes >= 0)
        val output = ByteArrayOutputStream(minOf(maximumBytes, 8192))
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) return output.toByteArray()
            require(count > 0) { "Cannot read backup document" }
            require(count <= maximumBytes - output.size()) { "Backup exceeds supported size" }
            output.write(buffer, 0, count)
        }
    }

    fun decodeUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
}
