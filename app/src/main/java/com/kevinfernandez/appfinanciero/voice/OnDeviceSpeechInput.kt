package com.kevinfernandez.appfinanciero.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.MainThread

/** Uses only Android's on-device recognizer; it never falls back to a network service. */
class OnDeviceSpeechInput(private val context: Context) : AutoCloseable {
    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    @MainThread
    fun start(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!isAvailable) {
            onError("On-device speech recognition is not available")
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Microphone permission is required")
            return
        }
        close()
        val engine = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = engine
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                close()
                onError("Speech recognition failed ($error)")
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                close()
                if (text.isNullOrBlank()) onError("No speech was recognized") else onResult(text)
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        engine.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        })
    }

    @MainThread
    fun stop() { recognizer?.stopListening() }

    @MainThread
    override fun close() {
        recognizer?.destroy()
        recognizer = null
    }
}
