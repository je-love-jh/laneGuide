package com.laneguide.app.engine

import android.os.Handler
import android.os.Looper
import com.laneguide.app.model.LaneState

/** 화면에 보여줄 상태 */
data class UiState(
    val highwayName: String? = null,
    val laneSummary: String = "고속도로 구간이 아닙니다",
    val lanes: List<LaneState> = emptyList(),
    val lastAlert: String? = null,
    val speedKmh: Int = 0,
    val gpsReady: Boolean = false,
)

/** 서비스(경고 엔진)와 화면을 잇는 아주 단순한 상태 저장소 */
object AppState {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(UiState) -> Unit>()

    @Volatile
    var current = UiState()
        private set

    fun update(state: UiState) {
        current = state
        mainHandler.post { listeners.toList().forEach { it(state) } }
    }

    /** 메인 스레드에서 호출 */
    fun observe(listener: (UiState) -> Unit) {
        listeners += listener
        listener(current)
    }

    fun remove(listener: (UiState) -> Unit) {
        listeners -= listener
    }
}
