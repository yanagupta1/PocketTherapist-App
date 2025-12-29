package com.example.pockettherapist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class AudioTextHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: ((String) -> Unit)? = null
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent

    init {
        setupRecognizer()
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError?.invoke("Speech Recognition not available on this device")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("AudioText", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Couldn't catch that. Hold the button and speak clearly."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Hold and speak into your phone."
                    SpeechRecognizer.ERROR_AUDIO -> "Hold the mic button and speak."
                    SpeechRecognizer.ERROR_CLIENT -> "" // Silent - usually happens on cancel
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Check your internet connection."
                    SpeechRecognizer.ERROR_SERVER -> "Try again in a moment."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Hold and try again."
                    else -> "Hold the mic button and speak."
                }
                if (message.isNotEmpty()) {
                    onError?.invoke(message)
                }
            }

            override fun onResults(results: Bundle?) {
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""

                onResult(spoken)
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
    }

    fun startListening() {
        speechRecognizer?.startListening(recognizerIntent)
        Log.d("AudioText", "Listening started")
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        Log.d("AudioText", "Listening stopped")
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
