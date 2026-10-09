package com.kevinfernandez.appfinanciero.ai

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.LogSeverity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalLlmServiceTest {
    @Test fun packagedNativeRuntimeLoads() {
        // Checks APK/JNI packaging only, not model inference or phone performance.
        Engine.setNativeMinLogSeverity(LogSeverity.INFINITY)
    }

    @Test fun missingModelHasDeterministicFallbackAndClosingIsIdempotent() = runBlocking<Unit> {
        val root = Files.createTempDirectory("local-ai-test").toFile()
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = root
        }
        val service = LocalLlmService(context)
        try {
            assertFalse(service.hasModel())
            assertEquals(AiPromptPolicy.noModelReply, service.reply("How do budgets work?"))
            service.close()
            service.close()
            assertThrows(IllegalStateException::class.java) {
                runBlocking { service.reply("How do budgets work?") }
            }
        } finally {
            service.close()
            root.deleteRecursively()
        }
    }
}
