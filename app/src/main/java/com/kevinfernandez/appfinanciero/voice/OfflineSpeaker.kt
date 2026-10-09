package com.kevinfernandez.appfinanciero.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Speaks only through an installed, non-network Spanish voice. */
class OfflineSpeaker(context: Context) : AutoCloseable {
    private var ready = false
    private lateinit var engine: TextToSpeech

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS && this::engine.isInitialized) selectOfflineVoice()
        }
    }

    private fun selectOfflineVoice() {
        val voice = engine.voices?.firstOrNull {
            it.locale.language == Locale("es").language &&
                !it.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
        }
        if (voice != null) {
            ready = engine.setVoice(voice) == TextToSpeech.SUCCESS
        }
    }

    val isReady: Boolean get() = ready

    fun speak(text: String): Boolean {
        if (!ready || text.isBlank()) return false
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "finance-explanation") == TextToSpeech.SUCCESS
    }

    override fun close() {
        ready = false
        engine.shutdown()
    }
}
