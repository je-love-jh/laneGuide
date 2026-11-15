package com.laneguide.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.ArrayDeque
import java.util.Locale

/**
 * 음성 안내. Pleos TextToSpeech SDK(ON_DEVICE: 차 안에서 바로 합성, 네트워크 불필요)를 쓴다.
 * Pleos 에뮬레이터·차량에는 표준 안드로이드 TTS 엔진이 없어서 android.speech.tts 는 초기화가 실패한다.
 * - 재생 전 오디오 포커스(GAIN_TRANSIENT_MAY_DUCK)를 요청해 음악·내비 소리를 잠시 줄인다.
 * - 첫 음절이 잘리지 않도록 포커스를 받은 뒤 180ms 기다렸다 말한다 (Pleos 가이드 권장 150~200ms).
 * - 안내가 겹치면 줄을 세워 하나씩 말한다.
 * 권한: pleos.car.permission.TTS_SERVICE
 */
class Speaker(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            // 글레오 호출 등으로 포커스를 잃으면 즉시 멈춘다
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                main.post { queue.clear(); stopQuietly() }
            }
        }
        .build()

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)

    // 아래 값들은 메인 스레드에서만 만진다
    private val queue = ArrayDeque<String>()
    private var speaking = false
    private var released = false

    @Volatile
    var isReady = false
        private set

    private val listener = object : EventListener {
        override fun onReady() {}
        override fun onStart() { Log.i(TAG, "재생 시작") }
        override fun onDone() { Log.i(TAG, "재생 끝"); main.post { finishOne() } }
        override fun onStop() { main.post { finishOne() } }
        override fun onError(error: ErrorType) { Log.w(TAG, "재생 오류: $error"); main.post { finishOne() } }
        override fun onUpdatedRms(rms: Double) {}
    }

    init {
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        if (released) return@post
                        try {
                            tts.setAudioAttributes(attributes)
                            // 반드시 나라까지(ko-KR). Locale.KOREAN("ko")이면 엔진이 "unsupported locale: ko-" 로 실패한다
                            tts.setLocale(Locale.KOREA)
                            tts.addEventListener(listener)
                            isReady = true
                            Log.i(TAG, "음성 엔진 준비 완료 (Pleos TextToSpeech, ON_DEVICE)")
                            next()
                        } catch (e: Exception) {
                            Log.w(TAG, "음성 엔진 설정 실패", e)
                        }
                    }
                }
                override fun onFailure(error: ErrorType) {
                    Log.w(TAG, "음성 엔진 초기화 실패: $error")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "음성 엔진 초기화 실패 (권한 TTS_SERVICE 확인)", e)
        }
    }

    fun speak(text: String) {
        main.post {
            if (released) return@post
            // 엔진이 아직 준비 중이면 줄에 세워 두었다가 준비되면 말한다 (너무 쌓이면 오래된 것부터 버림)
            if (queue.size >= MAX_QUEUE) queue.pollFirst()
            queue.addLast(text)
            if (!isReady) Log.i(TAG, "대기: 음성 엔진 준비 중")
            next()
        }
    }

    private fun next() {
        if (!isReady || speaking || released) return
        val text = queue.pollFirst() ?: return
        val focus = audioManager.requestAudioFocus(focusRequest)
        if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "건너뜀: 오디오 포커스를 받지 못함 (result=$focus)")
            next()
            return
        }
        speaking = true
        main.postDelayed({
            if (released) return@postDelayed
            try {
                tts.speak(text, true)
            } catch (e: Exception) {
                Log.w(TAG, "말하기 실패", e)
                finishOne()
            }
        }, PRE_ROLL_MS)
    }

    private fun finishOne() {
        if (!speaking) return
        speaking = false
        if (queue.isEmpty()) audioManager.abandonAudioFocusRequest(focusRequest)
        next()
    }

    private fun stopQuietly() {
        try { tts.stop() } catch (_: Exception) {}
    }

    fun shutdown() {
        released = true
        isReady = false
        queue.clear()
        stopQuietly()
        try { tts.removeEventListener(listener) } catch (_: Exception) {}
        try { tts.release() } catch (_: Exception) {}
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private companion object {
        const val TAG = "Speaker"
        const val PRE_ROLL_MS = 180L
        const val MAX_QUEUE = 4
    }
}
