package com.phoneclaw.app.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class VoiceService : Service() {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    companion object {
        private const val TAG = "VoiceService"
        private const val CHANNEL_ID = "voice"
        private var lastSpokenText = ""
        private var lastRecognizedText = ""
        private var _isListening = false
        var isSpeaking = false; private set
        var onResult: ((String) -> Unit)? = null

        fun isListening(): Boolean = _isListening
        fun setListening(v: Boolean) { _isListening = v }
        fun speak(context: android.content.Context, text: String) {
            lastSpokenText = text
            context.startService(Intent(context, VoiceService::class.java).apply {
                action = "SPEAK"
                putExtra("text", text)
            })
        }
        fun stop(context: android.content.Context) {
            context.startService(Intent(context, VoiceService::class.java).apply { action = "STOP_SPEAK" })
        }
        fun listen(context: android.content.Context) {
            context.startService(Intent(context, VoiceService::class.java).apply { action = "LISTEN" })
        }
        fun stopListening(context: android.content.Context) {
            context.startService(Intent(context, VoiceService::class.java).apply { action = "STOP_LISTEN" })
        }
        fun getLastResult(): String = lastRecognizedText
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1003, Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("语音服务").setContentText("就绪")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build())
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
                Log.i(TAG, "TTS initialized")
            }
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SPEAK" -> speak(intent.getStringExtra("text") ?: lastSpokenText)
            "LISTEN" -> startListening()
            "STOP_LISTEN" -> stopListening()
            "STOP_SPEAK" -> tts?.stop().also { isSpeaking = false }
        }
        return START_STICKY
    }

    private fun speak(text: String) {
        if (text.isEmpty()) return
        try {
            if (isSpeaking) tts?.stop()
            isSpeaking = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt_${System.currentTimeMillis()}")
            } else {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            }
            Log.i(TAG, "speaking: ${text.take(50)}")
        } catch (e: Exception) { Log.e(TAG, "TTS error: ${e.message}"); isSpeaking = false }
    }

    private fun startListening() {
        if (_isListening) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            }
            speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { _isListening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { _isListening = false }
                override fun onError(error: Int) { _isListening = false; Log.e(TAG, "STT error: $error") }
                override fun onResults(results: android.os.Bundle?) {
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!texts.isNullOrEmpty()) { lastRecognizedText = texts[0]; onResult?.invoke(lastRecognizedText) }
                    _isListening = false
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) { Log.e(TAG, "STT error: ${e.message}"); _isListening = false }
    }

    private fun stopListening() { speechRecognizer?.stopListening(); speechRecognizer?.cancel(); _isListening = false }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "语音", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onDestroy() { tts?.shutdown(); speechRecognizer?.destroy(); super.onDestroy() }
}
