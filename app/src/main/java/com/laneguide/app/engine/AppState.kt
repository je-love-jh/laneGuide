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
    /** 높이 경고가 난 뒤, 운전자가 누르면 우회 지점을 내비 경유지로 넣는 제안 (없으면 null) */
    val detourOffer: DetourOffer? = null,
    /** 가상 콘솔 등에서 읽은 차량 속도(km/h). 읽을 수 없으면 null */
    val vehicleSpeedKmh: Int? = null,
    /** 시연 주행 중이면 상태, 아니면 null */
    val demo: DemoStatus? = null,
    /** 앞쪽 높이 제한 시설물이 가까울 때의 상태(경고 또는 통과 가능). 없으면 null */
    val heightStatus: HeightStatus? = null,
)

/**
 * 높이 제한 시설물 상태 (DESIGN_WARN.md).
 * [blocked] true = 내 차 높이 + 여유가 제한 높이보다 커서 통과할 수 없음(경고), false = 통과 가능(조용한 정보).
 */
data class HeightStatus(
    val facilityName: String,
    val limitM: Double,
    val vehicleM: Double,
    val distanceM: Int,
    val blocked: Boolean,
)

/** 시연 주행 상태: GPS 대신 가상 위치로 달리는 중 */
data class DemoStatus(val routeLabel: String, val progressPercent: Int, val hint: String?)

sealed class DemoCommand {
    data class Start(val route: com.laneguide.app.demo.DemoRouteId) : DemoCommand()
    object Stop : DemoCommand()
}

data class DetourOffer(val facilityId: String, val facilityName: String, val detourName: String, val lat: Double, val lon: Double)

/** 서비스(경고 엔진)와 화면을 잇는 아주 단순한 상태 저장소 */
object AppState {
    /** 화면의 [우회 경로로 변경] 버튼이 눌렸을 때 서비스가 처리한다 */
    @Volatile
    var onDetourRequested: ((DetourOffer) -> Unit)? = null

    /** 화면의 [시연 주행]·[종료] 버튼이 서비스에 보내는 명령 */
    @Volatile
    var onDemoCommand: ((DemoCommand) -> Unit)? = null

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
