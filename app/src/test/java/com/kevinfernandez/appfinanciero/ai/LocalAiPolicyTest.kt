package com.kevinfernandez.appfinanciero.ai

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LocalAiPolicyTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun failedImportPreservesExistingModelAndRemovesPartialFile() {
        val directory = temporaryFolder.newFolder()
        val existing = java.io.File(directory, "model.litertlm").apply { writeText("LITERTLMold") }
        assertThrows(IllegalArgumentException::class.java) {
            ModelFilePolicy.importModel(directory, "bad.litertlm", ByteArrayInputStream("invalid".toByteArray()))
        }
        assertEquals("LITERTLMold", existing.readText())
        assertEquals(listOf("model.litertlm"), directory.list()!!.toList())
    }

    @Test fun validImportUsesFixedPrivateNameNotProviderPath() {
        val directory = temporaryFolder.newFolder()
        val result = ModelFilePolicy.importModel(directory, "../../gemma.litertlm", ByteArrayInputStream("LITERTLMnew".toByteArray()))
        assertEquals(java.io.File(directory, "model.litertlm"), result)
        assertEquals("LITERTLMnew", result.readText())
    }

    @Test fun acceptsBoundedLiteRtContainer() {
        val bytes = "LITERTLMpayload".toByteArray()
        val output = ByteArrayOutputStream()
        ModelFilePolicy.copyValidated("gemma.litertlm", ByteArrayInputStream(bytes), output, 20)
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test fun rejectsWrongExtensionEvenWithHeader() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelFilePolicy.copyValidated("model.gguf", ByteArrayInputStream("LITERTLM".toByteArray()), ByteArrayOutputStream())
        }
    }

    @Test fun rejectsRenamedAndTruncatedFiles() {
        for (bytes in listOf("GGUFpayload", "LITERT")) {
            assertThrows(IllegalArgumentException::class.java) {
                ModelFilePolicy.copyValidated("model.litertlm", ByteArrayInputStream(bytes.toByteArray()), ByteArrayOutputStream())
            }
        }
    }

    @Test fun enforcesActualSizeRatherThanProviderMetadata() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelFilePolicy.copyValidated("model.litertlm", ByteArrayInputStream("LITERTLMtoo large".toByteArray()), ByteArrayOutputStream(), 8)
        }
    }

    @Test fun acceptsExactSizeBoundaryAndPartialReads() {
        val input = object : ByteArrayInputStream("LITERTLM".toByteArray()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(1, len))
        }
        val output = ByteArrayOutputStream()
        ModelFilePolicy.copyValidated("model.litertlm", input, output, 8)
        assertEquals(8, output.size())
    }

    @Test fun promptRejectsEmptyOversizedAndControlCharacters() {
        for (question in listOf("  ", "x".repeat(2001), "hello\u0000world")) {
            assertThrows(IllegalArgumentException::class.java) { AiPromptPolicy.validateQuestion(question) }
        }
        assertEquals("How can I save?", AiPromptPolicy.validateQuestion(" How can I save? "))
    }

    @Test fun noModelFallbackNeverPretendsToBeGenerated() {
        assertFalse(AiPromptPolicy.noModelReply.generated)
        assertTrue(AiPromptPolicy.noModelReply.text.contains("No local model"))
        assertTrue(AiPromptPolicy.SYSTEM_INSTRUCTION.contains("Never calculate"))
        assertTrue(AiPromptPolicy.SYSTEM_INSTRUCTION.contains("cannot record"))
    }
}
