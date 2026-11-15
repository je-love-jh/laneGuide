package com.laneguide.app.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.laneguide.app.data.DatasetLoader
import com.laneguide.app.data.ProfileStore
import com.laneguide.app.geo.Geo
import com.laneguide.app.model.GeoPoint
import com.laneguide.app.model.HeightFacility
import com.laneguide.app.model.HighwaySegment
import com.laneguide.app.model.VehicleProfile
import com.laneguide.app.pleos.PleosVehicleSignals
import com.laneguide.app.pleos.VehicleSignals
import com.laneguide.app.rules.LaneRules
import com.laneguide.app.speech.Speaker
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * 화면이 가려져도 계속 동작하는 경고 서비스.
 * 표준 LocationManager 로 1초마다 위치를 받아 AlertEngine 에 넘기고, 결과를 음성과 화면으로 내보낸다.
 */
class AlertService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var engine: AlertEngine
    private lateinit var speaker: Speaker
    private lateinit var store: ProfileStore
    private val vehicleSignals: VehicleSignals by lazy { PleosVehicleSignals(this) }

    private var lastProfile: VehicleProfile? = null
    private var currentSegment: HighwaySegment? = null
    val navi: com.laneguide.app.navi.NaviController by lazy { com.laneguide.app.navi.NaviController(this) }
    private val speedReader by lazy { com.laneguide.app.car.VehicleSpeedReader(this) }
    private var prevLocation: Location? = null
    private var lastMoveBearing: Float? = null
    private var lastStatusLogMs = 0L

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        store = ProfileStore(this)
        engine = AlertEngine(DatasetLoader.load(this))
        speaker = Speaker(this)

        vehicleSignals.start(
            onCruiseActivated = {
                Log.i(TAG, "크루즈 작동 감지")
                currentSegment?.let {
                    val t = LaneRules.announcement(it.lanes, store.profile)
                    Log.i(TAG, "음성: $t"); speaker.speak(t)
                    // 화면에도 크루즈가 켜졌다는 것이 보이게 (음성 문장은 그대로, 화면은 첫 문장만 붙여서 짧게)
                    publishAlert("크루즈 작동 감지. " + t.substringBefore(". ").trimEnd('.') + ".")
                }
            },
            onLeftTurnSignal = {
                if (currentSegment != null) publishAlert("추월 후 우측 지정차로로 복귀하세요")
            },
        )

        speedReader.start()
        AppState.onDemoCommand = { cmd -> runOnMain { onDemoCommand(cmd) } }
        handler.postDelayed(tickRunnable, TICK_MS)
        navi.onNaviMessage = { msg -> runOnMain { publishAlert(msg) } }
        navi.start()
        // 운전자가 [우회 경로로 변경]을 눌렀을 때만 내비에 경유지를 넣는다 (자동 변경 없음)
        AppState.onDetourRequested = { offer -> runOnMain { requestDetour(offer) } }

        locationManager = getSystemService(LocationManager::class.java)
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "위치 권한이 없어 서비스를 멈춥니다", e)
            stopSelf()
        }
    }

    override fun onLocationChanged(location: Location) {
        // 시연 주행 중에는 GPS 대신 가상 위치를 쓴다
        if (demoRoute != null) return

        // 에뮬레이터 `geo fix`는 속도 0·방향 0(북쪽)을 채워 보낸다. 그래서 방향은 앞뒤 위치(이동 경로)로 계산하고,
        // GPS가 준 방향은 GPS 속도도 함께 있을 때(실제 주행)만 쓴다.
        val gpsMoving = location.hasSpeed() && location.speed > AlertEngine.MIN_MOVING_MPS
        var speedMps = if (gpsMoving) location.speed else 0f
        var bearingDeg: Float? = if (gpsMoving && location.hasBearing()) location.bearing else null
        val prev = prevLocation
        if (prev != null) {
            val dtSec = (location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000_000f
            val distM = prev.distanceTo(location)
            val calcMps = if (dtSec > 0f) distM / dtSec else 0f
            if (calcMps > MAX_PLAUSIBLE_MPS) {
                // 위치가 순간이동함(에뮬레이터 초기 위치 → GPX 시작점 등): 이번 값으로 속도·방향을 만들지 않는다
                Log.i(TAG, "위치 점프 무시: ${distM.roundToInt()}m / ${"%.1f".format(dtSec)}s")
                speedMps = 0f
                bearingDeg = null
                lastMoveBearing = null
            } else {
                if (!gpsMoving) speedMps = calcMps
                if (distM > MIN_MOVE_FOR_BEARING_M) {
                    lastMoveBearing = (prev.bearingTo(location) + 360f) % 360f
                }
                if (bearingDeg == null) bearingDeg = lastMoveBearing
            }
        }
        prevLocation = location

        process(GeoPoint(location.latitude, location.longitude), bearingDeg, speedMps)
    }

    /** 위치·방향·속도 한 번을 판단 엔진에 넣고, 음성과 화면으로 내보낸다. (실제 GPS 와 시연 주행이 같이 쓴다) */
    private fun process(here: GeoPoint, bearingDeg: Float?, speedMps: Float) {
        val profile = store.profile
        if (profile != lastProfile) {
            engine.resetSegmentAnnouncement()
            lastProfile = profile
        }
        val result = engine.onLocation(
            position = here,
            bearingDeg = bearingDeg,
            speedMps = speedMps,
            profile = profile,
            heightM = store.heightM,
            now = LocalTime.now(),
        )
        if (result.segment?.id != currentSegment?.id) {
            Log.i(TAG, "구간: ${result.segment?.let { "${it.name} 편도 ${it.lanes}차로" } ?: "고속도로 아님"} (속도 ${(speedMps * 3.6f).roundToInt()}km/h)")
        }
        currentSegment = result.segment
        logStatus(here, speedMps, bearingDeg)
        updateDetourOffer(here, result.warned)
        result.speech.forEach { Log.i(TAG, "음성: $it"); speaker.speak(it) }

        val segment = result.segment
        AppState.update(
            AppState.current.copy(
                highwayName = segment?.name,
                laneSummary = segment?.let { LaneRules.summary(it.lanes, profile) } ?: "고속도로 구간이 아닙니다",
                lanes = segment?.let { LaneRules.laneStates(it.lanes, profile) } ?: emptyList(),
                lastAlert = result.speech.lastOrNull() ?: AppState.current.lastAlert,
                speedKmh = (speedMps * 3.6f).roundToInt(),
                gpsReady = true,
                detourOffer = offer,
                heightStatus = result.heightStatus,
            ),
        )
    }

    // ---------- 시연 주행 (가상 위치) ----------
    // GPS 대신, 차량 속도(가상 콘솔 ACCEL)만큼 GPX 경로를 따라 전진한 위치를 1초마다 엔진에 넣는다.

    private val handler = android.os.Handler(Looper.getMainLooper())
    private var demoRoute: com.laneguide.app.demo.DemoRoute? = null
    private var demoDistanceM = 0.0
    private var demoLastTickMs = 0L

    private val tickRunnable = object : Runnable {
        override fun run() { tick(); handler.postDelayed(this, TICK_MS) }
    }

    private fun onDemoCommand(cmd: DemoCommand) {
        when (cmd) {
            is DemoCommand.Start -> startDemo(cmd.route)
            DemoCommand.Stop -> if (demoRoute != null) stopDemo("시연 주행을 끝냈습니다. 실제 GPS로 돌아갑니다")
        }
    }

    private fun startDemo(id: com.laneguide.app.demo.DemoRouteId) {
        if (demoRoute != null) return
        if (!speedReader.available) {
            publishAlert("차량 속도를 읽을 수 없어 시연 주행을 시작하지 못했습니다 (차량 속도 권한을 허용해 주세요)")
            return
        }
        if (speedReader.speedMps > STOPPED_MPS) {
            publishAlert("시연 주행은 정차 중에만 시작할 수 있습니다")
            return
        }
        val route = try {
            com.laneguide.app.demo.DemoRoute.load(this, id)
        } catch (e: Exception) {
            Log.w(TAG, "시연 경로를 읽지 못했습니다", e)
            publishAlert("시연 경로를 읽지 못했습니다")
            return
        }
        demoRoute = route
        demoDistanceM = 0.0
        demoLastTickMs = android.os.SystemClock.elapsedRealtime()
        prevLocation = null
        lastMoveBearing = null
        engine.reset()
        clearOffer()
        Log.i(TAG, "시연 주행 시작: ${id.label} (경로 ${route.totalM.roundToInt()}m)")
        AppState.update(AppState.current.copy(lastAlert = "시연 주행을 시작합니다", detourOffer = null))
        tick()
    }

    private fun stopDemo(message: String) {
        Log.i(TAG, "시연 주행 종료 (${demoDistanceM.roundToInt()}m 진행): $message")
        demoRoute = null
        prevLocation = null
        lastMoveBearing = null
        currentSegment = null
        engine.reset()
        clearOffer()
        AppState.update(
            AppState.current.copy(
                highwayName = null,
                laneSummary = "고속도로 구간이 아닙니다",
                lanes = emptyList(),
                speedKmh = 0,
                gpsReady = false,
                detourOffer = null,
                demo = null,
                heightStatus = null,
                lastAlert = message,
            ),
        )
    }

    /** 1초마다: 차량 속도를 화면에 알리고, 시연 주행 중이면 속도만큼 전진시킨다. */
    private fun tick() {
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val kmh = if (speedReader.available) (speedReader.speedMps * 3.6f).roundToInt() else null
        val route = demoRoute
        if (route == null) {
            if (AppState.current.vehicleSpeedKmh != kmh) AppState.update(AppState.current.copy(vehicleSpeedKmh = kmh))
            return
        }
        val dt = ((nowMs - demoLastTickMs) / 1000.0).coerceIn(0.0, 3.0)
        demoLastTickMs = nowMs
        val v = speedReader.speedMps.toDouble()
        demoDistanceM += v * dt
        val fix = route.fixAt(demoDistanceM)
        process(fix.position, fix.bearingDeg, v.toFloat())

        val percent = (demoDistanceM / route.totalM * 100).toInt().coerceIn(0, 100)
        AppState.update(
            AppState.current.copy(
                vehicleSpeedKmh = kmh,
                demo = DemoStatus(route.id.label, percent, demoHint(route.id, v)),
            ),
        )
        if (demoDistanceM >= route.totalM) stopDemo("시연 경로 끝에 닿았습니다. 실제 GPS로 돌아갑니다")
    }

    private fun demoHint(id: com.laneguide.app.demo.DemoRouteId, speedMps: Double): String? {
        if (speedMps < STOPPED_MPS) return "가상 콘솔에서 ACCEL(엑셀)을 밟으면 출발합니다"
        if (id == com.laneguide.app.demo.DemoRouteId.UNDERPASS) {
            val lowest = engine.facilities.minOfOrNull { it.limitM } ?: return null
            if (store.heightM + AlertEngine.SAFETY_MARGIN_M <= lowest) {
                return "차 높이 ${"%.1f".format(store.heightM)}m는 굴다리(${"%.1f".format(lowest)}m)보다 낮아 경고가 나오지 않는 것이 정상입니다"
            }
        }
        return null
    }

    // ---------- 우회 경로 제안 ----------

    private var offer: DetourOffer? = null
    private var offerFacility: HeightFacility? = null

    /** 우회 지점이 있는 시설물에 경고가 났으면 제안을 띄우고, 시설물에서 멀어지면 거둔다. */
    private fun updateDetourOffer(here: GeoPoint, warned: List<HeightFacility>) {
        warned.firstOrNull { it.detour != null }?.let { f ->
            val d = f.detour!!
            offerFacility = f
            offer = DetourOffer(f.id, f.name, d.name, d.point.lat, d.point.lon)
        }
        val f = offerFacility
        if (f != null && Geo.distanceM(here, f.point) > OFFER_MAX_DISTANCE_M) clearOffer()
    }

    private fun clearOffer() { offer = null; offerFacility = null }

    private fun requestDetour(o: DetourOffer) {
        if (!navi.isReady) {
            publishAlert("내비와 연결되지 않아 우회 경로를 요청할 수 없습니다")
            return
        }
        // 시험 결과: 내비에 경로가 없을 때 경유지를 요청하면 내비가 오류(CAUSE_SEARCH_ROUTE)를 낸다
        if (!navi.hasRoute) {
            publishAlert("내비에 안내 중인 경로가 없어 우회 경유지를 넣을 수 없습니다")
            return
        }
        Log.i(TAG, "운전자가 우회 경로 선택: ${o.facilityName} → 경유지 ${o.detourName}")
        navi.addWaypoint(o.lat, o.lon, o.detourName)
        clearOffer()
        publishAlert("우회 지점을 경유지로 내비에 요청했습니다. 내비 화면에서 경로를 확인하세요")
        AppState.update(AppState.current.copy(detourOffer = null))
    }

    private fun runOnMain(block: () -> Unit) = android.os.Handler(Looper.getMainLooper()).post(block)

    /** 5초마다 한 줄: 지금 위치·속도·방향과 가장 가까운 높이 제한 시설물까지 거리 */
    private fun logStatus(here: GeoPoint, speedMps: Float, bearingDeg: Float?) {
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (nowMs - lastStatusLogMs < STATUS_LOG_INTERVAL_MS) return
        lastStatusLogMs = nowMs
        val nearest = engine.facilities.minByOrNull { Geo.distanceM(here, it.point) }
        val facilityText = nearest?.let { f ->
            val d = Geo.distanceM(here, f.point).roundToInt()
            val dir = Geo.bearingDeg(here, f.point).roundToInt()
            "${f.name} ${d}m(방위 ${dir}도)"
        } ?: "없음"
        Log.i(
            TAG,
            "상태: ${"%.5f".format(here.lat)},${"%.5f".format(here.lon)}${if (demoRoute != null) " [시연]" else ""} " +
                "속도 ${(speedMps * 3.6f).roundToInt()}km/h 방향 ${bearingDeg?.roundToInt() ?: "-"}도 " +
                "높이 ${store.heightM}m 시설물 $facilityText",
        )
    }

    private fun publishAlert(text: String) {
        Log.i(TAG, "화면 알림: $text")
        AppState.update(AppState.current.copy(lastAlert = text))
    }

    // API 29 기기 호환을 위해 나머지 콜백도 비워서 구현
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    override fun onDestroy() {
        if (::locationManager.isInitialized) locationManager.removeUpdates(this)
        vehicleSignals.stop()
        AppState.onDetourRequested = null
        AppState.onDemoCommand = null
        handler.removeCallbacks(tickRunnable)
        speedReader.stop()
        navi.stop()
        if (::speaker.isInitialized) speaker.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "지정차로 안내", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("지정차로 안내 동작 중")
            .setContentText("고속도로·높이 제한·통행제한 구간을 확인하고 있습니다")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    private companion object {
        const val TAG = "AlertService"
        const val CHANNEL_ID = "lane_guide"
        const val NOTIFICATION_ID = 1
        /** 250 km/h 보다 빠르게 움직였다면 실제 이동이 아니라 위치 점프로 본다 */
        const val MAX_PLAUSIBLE_MPS = 250f / 3.6f
        const val MIN_MOVE_FOR_BEARING_M = 2f
        const val TICK_MS = 1000L
        /** 이 속도(m/s, 약 1.8km/h) 미만이면 정차로 본다 */
        const val STOPPED_MPS = 0.5
        /** 시설물에서 이 거리보다 멀어지면 우회 제안을 거둔다 */
        const val OFFER_MAX_DISTANCE_M = 600.0
        const val STATUS_LOG_INTERVAL_MS = 5000L
    }
}
