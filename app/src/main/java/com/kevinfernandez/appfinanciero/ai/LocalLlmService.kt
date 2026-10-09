package com.kevinfernandez.appfinanciero.ai

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.io.FilterInputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Optional text-only adapter. It has no ledger, repository, network, or tool access. */
class LocalLlmService(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val directory = File(appContext.noBackupFilesDir, "local-llm")
    private val model = File(directory, "model.litertlm")
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val lifecycle = Mutex()
    private val activeLock = Any()
    private val closed = AtomicBoolean(false)
    private var engine: Engine? = null
    private var activeConversation: Conversation? = null
    @Volatile private var activeJob: Job? = null

    fun hasModel(): Boolean = model.isFile

    suspend fun importModel(uri: Uri, displayName: String) = withContext(dispatcher) {
        lifecycle.withLock {
            checkOpen()
            val coroutine = currentCoroutineContext()
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                val cancellable = object : FilterInputStream(input) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        coroutine.ensureActive()
                        return super.read(buffer, offset, length)
                    }
                }
                // Atomic import leaves the previous file intact if validation or copying fails.
                ModelFilePolicy.importModel(directory, displayName, cancellable)
                releaseEngine()
            } ?: error("Cannot open the selected model")
        }
    }

    suspend fun removeModel() = withContext(dispatcher) {
        lifecycle.withLock {
            checkOpen()
            releaseEngine()
            check(!model.exists() || model.delete()) { "Cannot remove the local model" }
        }
    }

    suspend fun reply(question: String): AiReply {
        val validated = AiPromptPolicy.validateQuestion(question)
        return withContext(dispatcher) {
            lifecycle.withLock {
                checkOpen()
                if (!hasModel()) return@withLock AiPromptPolicy.noModelReply
                activeJob = currentCoroutineContext()[Job]
                try {
                    val runtime = engine ?: createEngine().also { engine = it }
                    currentCoroutineContext().ensureActive()
                    val conversation = runtime.createConversation(ConversationConfig(
                        systemInstruction = Contents.of(AiPromptPolicy.SYSTEM_INSTRUCTION),
                        samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2),
                        automaticToolCalling = false,
                    ))
                    synchronized(activeLock) { activeConversation = conversation }
                    try {
                        val response = StringBuilder()
                        withTimeout(60_000) {
                            conversation.sendMessageAsync(validated).collect { chunk ->
                                response.append(chunk.toString())
                                check(response.length <= 4000) { "Response exceeded the safety limit" }
                            }
                        }
                        check(response.isNotBlank()) { "The local model returned no text" }
                        AiReply(response.toString(), true)
                    } finally {
                        synchronized(activeLock) {
                            activeConversation = null
                            try { conversation.cancelProcess() } finally { conversation.close() }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Native exceptions may include query text; never forward or log them.
                    throw IllegalStateException("Local inference failed. Check model compatibility or remove the model.")
                } catch (_: LinkageError) {
                    throw IllegalStateException("The local inference runtime is unavailable on this device.")
                } finally {
                    activeJob = null
                }
            }
        }
    }

    fun cancel() {
        activeJob?.cancel()
        synchronized(activeLock) { runCatching { activeConversation?.cancelProcess() } }
    }

    /** Non-blocking: cancel first, then serialize native disposal after ongoing operations finish. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        cancel()
        CoroutineScope(SupervisorJob() + dispatcher).launch {
            try { lifecycle.withLock { runCatching { releaseEngine() } } } finally { dispatcher.close() }
        }
    }

    private fun createEngine(): Engine {
        Engine.setNativeMinLogSeverity(LogSeverity.INFINITY)
        val created = Engine(EngineConfig(
            modelPath = model.absolutePath,
            backend = Backend.CPU(numOfThreads = 4),
            maxNumTokens = 1024,
            cacheDir = File(appContext.cacheDir, "local-llm").apply { mkdirs() }.absolutePath,
        ))
        try {
            created.initialize()
            checkOpen()
            return created
        } catch (error: Throwable) {
            if (created.isInitialized()) created.close()
            throw error
        }
    }

    private fun checkOpen() = check(!closed.get()) { "Local inference adapter is closed" }
    private fun releaseEngine() {
        engine?.close()
        engine = null
    }
}
