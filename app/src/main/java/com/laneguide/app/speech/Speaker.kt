package com.laneguide.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 음성 안내. 1단계는 안드로이드 표준 TTS를 쓰고, 2단계에서 Pleos TextToSpeech(ON_DEVICE)로 바꾼다.
 * - 재생 전 오디오 포커스(GAIN_TRANSIENT_MAY_DUCK)를 요청해 음악·내비 소리를 잠시 줄인다.
 * - 첫 음절이 잘리지 않도록 180ms 무음을 먼저 넣는다 (Pleos 가이드 권장 150~200ms).
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            // 글레오 호출 등으로 포커스를 잃으면 즉시 멈춘다
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                tts.stop()
            }
        }
        .build()

    private val tts = TextToSpeech(context.applicationContext, this)

    @Volatile
    var isReady = false
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.KOREAN)
        isReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId?.startsWith(MSG) == true) audioManager.abandonAudioFocusRequest(focusRequest)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
        })
    }

    fun speak(text: String) {
        if (!isReady) return
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        val id = System.nanoTime()
        tts.playSilentUtterance(PRE_ROLL_MS, TextToSpeech.QUEUE_ADD, "pre$id")
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "$MSG$id")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private companion object {
        const val MSG = "msg"
        const val PRE_ROLL_MS = 180L
    }
}
