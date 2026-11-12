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
                currentSegment?.let { val t = LaneRules.announcement(it.lanes, store.profile); Log.i(TAG, "음성: $t"); speaker.speak(t) }
            },
            onLeftTurnSignal = {
                if (currentSegment != null) publishAlert("추월 후 우측 지정차로로 복귀하세요")
            },
        )

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
        val profile = store.profile
        if (profile != lastProfile) {
            engine.resetSegmentAnnouncement()
            lastProfile = profile
        }

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

        val result = engine.onLocation(
            position = GeoPoint(location.latitude, location.longitude),
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
        logStatus(location, speedMps, bearingDeg)
        result.speech.forEach { Log.i(TAG, "음성: $it"); speaker.speak(it) }

        val segment = result.segment
        AppState.update(
            UiState(
                highwayName = segment?.name,
                laneSummary = segment?.let { LaneRules.summary(it.lanes, profile) } ?: "고속도로 구간이 아닙니다",
                lanes = segment?.let { LaneRules.laneStates(it.lanes, profile) } ?: emptyList(),
                lastAlert = result.speech.lastOrNull() ?: AppState.current.lastAlert,
                speedKmh = (speedMps * 3.6f).roundToInt(),
                gpsReady = true,
            ),
        )
    }

    /** 5초마다 한 줄: 지금 위치·속도·방향과 가장 가까운 높이 제한 시설물까지 거리 */
    private fun logStatus(location: Location, speedMps: Float, bearingDeg: Float?) {
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (nowMs - lastStatusLogMs < STATUS_LOG_INTERVAL_MS) return
        lastStatusLogMs = nowMs
        val here = GeoPoint(location.latitude, location.longitude)
        val nearest = engine.facilities.minByOrNull { Geo.distanceM(here, it.point) }
        val facilityText = nearest?.let { f ->
            val d = Geo.distanceM(here, f.point).roundToInt()
            val dir = Geo.bearingDeg(here, f.point).roundToInt()
            "${f.name} ${d}m(방위 ${dir}도)"
        } ?: "없음"
        Log.i(
            TAG,
            "상태: ${"%.5f".format(location.latitude)},${"%.5f".format(location.longitude)} " +
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
        const val STATUS_LOG_INTERVAL_MS = 5000L
    }
}
