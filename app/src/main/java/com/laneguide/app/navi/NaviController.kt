package com.laneguide.app.navi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

/**
 * Pleos NaviHelper SDK 로 내비 앱에 경로 변경을 요청한다. (경유지 추가/제거, 재탐색, 목적지 설정)
 * 권한: NAVI_ROUTE, NAVI_ROUTE_SEARCH, NAVI_CUSTOM_ROUTE (매니페스트 선언)
 *
 * 경로는 절대 자동으로 바꾸지 않는다. 이 클래스의 요청 함수는 운전자가 버튼을 눌렀을 때만 부른다.
 * 요청 결과는 내비 앱이 이벤트로 돌려준다 → 모든 이벤트를 로그(태그 NaviController)로 남긴다.
 */
class NaviController(private val context: Context) {

    @Volatile var isReady = false
        private set
    /** 내비가 지금 경로를 안내 중인가 (경로가 없으면 경유지 추가가 오류가 난다) */
    @Volatile var hasRoute = false
        private set
    @Volatile var navigationStatus: NavigationStatus? = null
        private set
    @Volatile var destination: DestinationInfo? = null
        private set

    /** 내비가 요청을 거절했을 때 등, 운전자에게 알릴 말 (SDK 콜백 스레드에서 불림) */
    @Volatile var onNaviMessage: ((String) -> Unit)? = null

    private val helper = NaviHelper(context.applicationContext)
    private var debugReceiver: BroadcastReceiver? = null

    private val listener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            navigationStatus = status
            hasRoute = status == NavigationStatus.ROUTE_DRIVE
            Log.i(TAG, "이벤트 내비 상태: $status")
        }
        override fun onRouteStarted(info: RouteStartInfo) {
            Log.i(TAG, "이벤트 경로 시작: 거리=${info.distance} 시간=${info.duration} 이름=${info.name}")
        }
        override fun onRoutingCompleted() { Log.i(TAG, "이벤트 경로 탐색 완료") }
        override fun onReRouteEnded() { Log.i(TAG, "이벤트 재탐색 끝") }
        override fun onRouteEnded() { Log.i(TAG, "이벤트 경로 끝") }
        override fun onRouteCancelled() { Log.i(TAG, "이벤트 경로 취소") }
        override fun onRouteOptionChanged(option: RouteOption) { Log.i(TAG, "이벤트 경로 옵션: $option") }
        override fun onWaypointChanged(info: WaypointChangedInfo) {
            Log.i(TAG, "이벤트 경유지 변경: index=${info.index} cmd=${info.cmdType} 새경로 거리=${info.routeStartInfo.distance} 시간=${info.routeStartInfo.duration}")
        }
        override fun onDestinationChanged() { Log.i(TAG, "이벤트 목적지 변경") }
        override fun onDestinationInfo(info: DestinationInfo) {
            destination = info
            Log.i(TAG, "이벤트 목적지 정보: ${info.name} (${info.latitude},${info.longitude}) 남은거리=${info.remainDistance} 남은시간=${info.remainTime}")
        }
        override fun onWayPointInfo(list: List<WaypointInfo>) {
            Log.i(TAG, "이벤트 경유지 목록: ${list.size}개 ${list.joinToString { "${it.name}(${it.latitude},${it.longitude})" }}")
        }
        override fun onRouteStateInfo(info: RouteStateInfo) {
            hasRoute = info.naviMode == NaviMode.ROUTE_GUIDING || info.naviMode == NaviMode.SIMULATION_GUIDING
            Log.i(TAG, "이벤트 경로 상태: 모드=${info.naviMode} 옵션=${info.routeOption}")
        }
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            Log.i(TAG, "이벤트 현재 위치: ${info.latitude},${info.longitude} 방향=${info.heading} 주소=${info.address}")
        }
        override fun onCurrentRoadSpeedLimit(limit: Int) { Log.i(TAG, "이벤트 제한속도: $limit") }
        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "이벤트 오류: $code")
            onNaviMessage?.invoke("내비가 경로 요청을 처리하지 못했습니다 ($code)")
        }
    }

    fun start() {
        try {
            helper.initialize(
                {
                    isReady = true; helper.addListener(listener); Log.i(TAG, "NaviHelper 연결 성공")
                    // 연결 전에 이미 안내 중이었을 수 있으니 현재 모드를 한 번 물어본다
                    runCatching { helper.getRouteStateInfo() }
                },
                { e -> Log.w(TAG, "NaviHelper 연결 실패: $e") },
            )
        } catch (e: Exception) {
            Log.w(TAG, "NaviHelper 초기화 예외 (권한 NAVI_* 확인)", e)
        }
        registerDebugReceiverIfDebuggable()
    }

    fun stop() {
        debugReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        debugReceiver = null
        runCatching { helper.removeListener(listener) }
        runCatching { helper.release() }
        isReady = false
    }

    // ---------- 요청 (운전자가 눌렀을 때만 부른다) ----------

    fun setDestination(lat: Double, lon: Double, name: String) {
        Log.i(TAG, "요청 목적지 설정: $name ($lat,$lon)")
        helper.requestRoute(RouteInfo(lat, lon, null, null, name, "", "", "", RouteOption.RECOMMENDED, "", false))
    }

    /** 경유지 1개를 추가한다. 내비가 새 경로를 만들면 onWaypointChanged 이벤트가 온다. */
    fun addWaypoint(lat: Double, lon: Double, name: String, index: WaypointIndex = WaypointIndex.FIRST) {
        Log.i(TAG, "요청 경유지 추가: $name ($lat,$lon) index=$index")
        helper.addWaypoint(RequestWaypointInfo(lat, lon, name, index, "", "", "", ""))
    }

    fun removeWaypoints() {
        Log.i(TAG, "요청 경유지 전부 제거")
        helper.removeWaypoint(WaypointIndex.ALL)
    }

    fun reroute() { Log.i(TAG, "요청 재탐색"); helper.requestReRoute() }
    fun cancelRoute() { Log.i(TAG, "요청 경로 취소"); helper.cancelRoute() }

    fun queryState() {
        Log.i(TAG, "요청 상태 조회")
        helper.getRouteStateInfo(); helper.getDestinationInfo(); helper.getWaypointInfo(); helper.getCurrentLocationInfo()
    }

    // ---------- 개발용: adb 로 호출해 보기 ----------
    // adb shell am broadcast -a com.laneguide.app.DEBUG_NAVI --es cmd dest --es lat 37.3 --es lon 127.1 --es name 목적지
    // cmd: dest | wp | rmwp | reroute | cancel | state

    private fun registerDebugReceiverIfDebuggable() {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val lat = i.getStringExtra("lat")?.toDoubleOrNull() ?: 0.0
                val lon = i.getStringExtra("lon")?.toDoubleOrNull() ?: 0.0
                val name = i.getStringExtra("name") ?: "테스트"
                try {
                    when (i.getStringExtra("cmd")) {
                        "dest" -> setDestination(lat, lon, name)
                        "wp" -> addWaypoint(lat, lon, name)
                        "rmwp" -> removeWaypoints()
                        "reroute" -> reroute()
                        "cancel" -> cancelRoute()
                        "state" -> queryState()
                        else -> Log.w(TAG, "알 수 없는 명령")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "명령 실패", e)
                }
            }
        }
        val filter = IntentFilter("com.laneguide.app.DEBUG_NAVI")
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(r, filter, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(r, filter)
        debugReceiver = r
    }

    private companion object { const val TAG = "NaviController" }
}
