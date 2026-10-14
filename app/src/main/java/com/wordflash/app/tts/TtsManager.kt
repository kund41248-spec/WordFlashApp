package com.wordflash.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单词发音（TTS 封装）。
 *
 * ## 设计要点
 *
 * **1. 生命周期绑到 Application**
 * `TextToSpeech` 初始化是异步的（几百毫秒），且持有系统服务连接。
 * 做成 `@Singleton` 全局一个实例，避免每次进学习页都重新初始化。
 * 释放交给进程结束，不主动 shutdown —— 否则再次朗读又要等初始化。
 *
 * **2. 就绪前排队**
 * 用户可能一进页面就点发音，此时引擎还没初始化完。这里把请求
 * 暂存到 [pendingText]，初始化完成后立即补播，不丢请求。
 *
 * **3. 状态可观察**
 * UI 需要知道「是否支持 TTS」「是否正在播放」来决定按钮是否可用。
 *
 * ## 常见坑
 *
 * - Android 11+ 必须在 Manifest 声明 `<queries>` 才能查到 TTS 引擎
 *   （本项目已在 AndroidManifest.xml 中声明）
 * - 设备可能**没有安装任何 TTS 引擎**（部分精简 ROM），
 *   [isAvailable] 会返回 false，UI 应隐藏发音按钮而不是崩溃
 * - 语言包可能缺失，`setLanguage` 返回 `LANG_MISSING_DATA`，
 *   此时仍可尝试朗读，但可能读不出或读成默认语音
 */
@Singleton
class TtsManager @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "TtsManager"

        /** 英文发音统一用美式；如需英式改成 Locale.UK */
        private val DEFAULT_LOCALE: Locale = Locale.US
    }

    /** TTS 可用状态 */
    enum class Status {
        /** 正在初始化 */
        INITIALIZING,
        /** 就绪，可朗读 */
        READY,
        /** 设备不支持或初始化失败 */
        UNAVAILABLE
    }

    private var tts: TextToSpeech? = null

    private val _status = MutableStateFlow(Status.INITIALIZING)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** 是否正在播放 */
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /**
     * 语速倍率。1.0 为正常。
     * 用户可在设置页调节（0.5 ~ 1.5），通过 [setSpeechRate] 写入。
     */
    private var speechRate: Float = 1.0f

    /** 初始化完成前收到的朗读请求，就绪后补播 */
    private var pendingText: String? = null

    /** 当前正在朗读的文本，用于判断是否重复点击 */
    private var speakingText: String? = null

    /**
     * 每次 speak 生成唯一 id。
     * TTS 的 utteranceId 必须是字符串，且要能区分不同请求，
     * 否则 onDone 回调无法判断是哪一条播完了。
     */
    private val utteranceCounter = AtomicLong(0)

    init {
        initTts()
    }

    private fun initTts() {
        // 用 applicationContext 避免持有 Activity 导致内存泄漏
        tts = TextToSpeech(context.applicationContext) { statusCode ->
            if (statusCode == TextToSpeech.SUCCESS) {
                configureEngine()
            } else {
                Log.w(TAG, "TTS 初始化失败，statusCode=$statusCode")
                _status.value = Status.UNAVAILABLE
            }
        }
    }

    private fun configureEngine() {
        val engine = tts ?: run {
            _status.value = Status.UNAVAILABLE
            return
        }

        val result = runCatching { engine.setLanguage(DEFAULT_LOCALE) }
            .getOrElse {
                Log.w(TAG, "setLanguage 异常", it)
                TextToSpeech.LANG_NOT_SUPPORTED
            }

        when (result) {
            TextToSpeech.LANG_MISSING_DATA -> {
                // 语言包缺失：仍标记为可用，但朗读可能不发声。
                // 不直接判定 UNAVAILABLE，因为部分引擎会在首次朗读时自动下载。
                Log.w(TAG, "缺少英语语言包，朗读可能无声")
                _status.value = Status.READY
            }
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                Log.w(TAG, "引擎不支持英语")
                _status.value = Status.UNAVAILABLE
                return
            }
            else -> _status.value = Status.READY
        }

        engine.setSpeechRate(speechRate)
        engine.setPitch(1.0f)

        // 进度回调：维护 isSpeaking 状态
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                speakingText = null
            }

            @Deprecated("TTS API 变更，仍需实现以兼容低版本")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                speakingText = null
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "朗读出错 utteranceId=$utteranceId code=$errorCode")
                _isSpeaking.value = false
                speakingText = null
            }
        })

        // 补播初始化期间到达的请求
        pendingText?.let { text ->
            pendingText = null
            speak(text)
        }
    }

    /**
     * 朗读文本。
     *
     * @param text 要朗读的内容。传单词而非例句 —— 例句太长，
     *        用户等不及，且发音质量不如单词清晰。
     */
    fun speak(text: String) {
        if (text.isBlank()) return

        when (_status.value) {
            Status.UNAVAILABLE -> {
                Log.d(TAG, "TTS 不可用，忽略朗读请求")
                return
            }
            Status.INITIALIZING -> {
                // 引擎还没好，记下来等就绪后补播
                pendingText = text
                return
            }
            Status.READY -> Unit
        }

        val engine = tts ?: return

        // 重复点击同一个词：先停再播，避免排队堆积
        if (speakingText == text) {
            engine.stop()
        }
        speakingText = text

        val utteranceId = "wf-${utteranceCounter.incrementAndGet()}"
        // QUEUE_FLUSH：打断上一条，保证用户点哪个词就读哪个
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "speak 调用失败，result=$result")
            _isSpeaking.value = false
            speakingText = null
        }
    }

    /** 停止当前朗读 */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        speakingText = null
    }

    /**
     * 设置语速。
     *
     * @param rate 0.5 ~ 1.5，超出范围会被截断（过快听不清，过慢失真）
     */
    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 1.5f)
        tts?.setSpeechRate(speechRate)
    }

    fun getSpeechRate(): Float = speechRate

    /**
     * 主动释放。正常不需要调用——进程结束时系统会回收。
     * 仅在「设置 → 清除缓存」等需要彻底重置引擎的场景使用。
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _status.value = Status.UNAVAILABLE
    }
}
