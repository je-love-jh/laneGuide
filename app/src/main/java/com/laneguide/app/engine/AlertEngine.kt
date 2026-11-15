package com.laneguide.app.engine

import com.laneguide.app.geo.Geo
import com.laneguide.app.model.GeoPoint
import com.laneguide.app.model.GoldenDataset
import com.laneguide.app.model.HighwaySegment
import com.laneguide.app.model.VehicleProfile
import com.laneguide.app.rules.LaneRules
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 경고 판단 엔진. 위치가 들어올 때마다
 * 1) 지금 어느 고속도로 구간인지, 2) 앞에 낮은 시설물이 있는지, 3) 통행제한 구간에 다가가는지 판단한다.
 * 같은 대상은 한 번만 알린다.
 */
class AlertEngine(private val data: GoldenDataset) {

    /** [warned]: 이번 위치에서 새로 경고한 높이 제한 시설물 (우회 경로 제안에 쓴다) */
    data class Result(
        val segment: HighwaySegment?,
        val speech: List<String>,
        val warned: List<com.laneguide.app.model.HeightFacility> = emptyList(),
        /** 화면에 띄울 높이 제한 상태(경고/통과 가능). 앞에 시설물이 없으면 null */
        val heightStatus: HeightStatus? = null,
    )

    /** 상태 로그용 */
    val facilities get() = data.facilities

    private var currentSegmentId: String? = null
    private val warnedFacilities = mutableSetOf<String>()
    private val warnedZones = mutableSetOf<String>()

    fun onLocation(
        position: GeoPoint,
        bearingDeg: Float?,
        speedMps: Float,
        profile: VehicleProfile,
        heightM: Double,
        now: LocalTime,
    ): Result {
        val speech = mutableListOf<String>()
        val warned = mutableListOf<com.laneguide.app.model.HeightFacility>()

        // 1) 고속도로 구간 매칭 (구간 선에서 50m 이내)
        val segment = data.segments
            .map { it to Geo.distanceToPathM(position, it.path) }
            .filter { it.second < SEGMENT_MATCH_M }
            .minByOrNull { it.second }
            ?.first
        if (segment?.id != currentSegmentId) {
            currentSegmentId = segment?.id
            if (segment != null) speech += LaneRules.announcement(segment.lanes, profile)
        }

        // 2) 높이 제한 시설물: 진행 방향 앞쪽 ±30도, 속도에 비례한 거리 안
        if (bearingDeg != null && speedMps > MIN_MOVING_MPS) {
            val range = max(300.0, speedMps * 15.0)
            for (f in data.facilities) {
                val d = Geo.distanceM(position, f.point)
                if (d > RESET_DISTANCE_M) { warnedFacilities -= f.id; continue }
                if (f.id in warnedFacilities) continue
                if (f.limitM >= heightM + SAFETY_MARGIN_M) continue
                if (d > range) continue
                if (Geo.angleDiff(bearingDeg.toDouble(), Geo.bearingDeg(position, f.point)) > AHEAD_ANGLE_DEG) continue
                warnedFacilities += f.id
                warned += f
                speech += "전방 약 ${roundTo50(d)}미터에 높이 제한 ${formatM(f.limitM)}미터 시설물이 있습니다. 현장 표지를 확인하세요."
            }
        }

        // 3) 통행제한 구간: 이 프로필에 해당하고, 지금이 제한 시간대일 때만
        for (z in data.zones) {
            if (profile !in z.profiles) continue
            val d = Geo.distanceM(position, z.center)
            if (d > z.radiusM * 3 + RESET_DISTANCE_M) { warnedZones -= z.id; continue }
            if (z.id in warnedZones || !z.isActive(now)) continue
            if (d <= z.radiusM + max(200.0, speedMps * 10.0)) {
                warnedZones += z.id
                speech += "전방은 화물차 통행제한 구간으로 등록된 곳입니다. 현장 표지를 확인하세요."
            }
        }

        return Result(segment, speech, warned, heightStatus(position, bearingDeg, speedMps, heightM))
    }

    /** 화면에 띄우고 있는 시설물. 한 번 잡으면 지나가거나 600m 넘게 멀어질 때까지 유지한다. */
    private var statusFacilityId: String? = null

    private fun heightStatus(position: GeoPoint, bearingDeg: Float?, speedMps: Float, heightM: Double): HeightStatus? {
        // 이미 보여 주던 시설물: 지나쳤거나(진행 방향과 90도 넘게 어긋남) 600m 넘게 멀어지면 거둔다
        statusFacilityId?.let { id ->
            val f = data.facilities.firstOrNull { it.id == id }
            if (f != null) {
                val d = Geo.distanceM(position, f.point)
                val passed = bearingDeg != null &&
                    Geo.angleDiff(bearingDeg.toDouble(), Geo.bearingDeg(position, f.point)) > 90.0
                if (d <= STATUS_KEEP_M && !passed) return statusOf(f, d, heightM)
            }
            statusFacilityId = null
        }
        // 새로 잡기: 경고 조건과 같게 진행 방향 앞쪽 ±30도, 속도에 비례한 거리 안
        if (bearingDeg == null || speedMps <= MIN_MOVING_MPS) return null
        val range = max(300.0, speedMps * 15.0)
        val found = data.facilities
            .map { it to Geo.distanceM(position, it.point) }
            .filter { it.second <= range }
            .filter { Geo.angleDiff(bearingDeg.toDouble(), Geo.bearingDeg(position, it.first.point)) <= AHEAD_ANGLE_DEG }
            .minByOrNull { it.second } ?: return null
        statusFacilityId = found.first.id
        return statusOf(found.first, found.second, heightM)
    }

    private fun statusOf(f: com.laneguide.app.model.HeightFacility, d: Double, heightM: Double) = HeightStatus(
        facilityName = f.name,
        limitM = f.limitM,
        vehicleM = heightM,
        distanceM = d.roundToInt(),
        blocked = f.limitM < heightM + SAFETY_MARGIN_M,
    )

    /** 위치가 크게 바뀔 때(시연 주행 시작·종료) 구간·경고 기록을 모두 지워, 같은 경로를 다시 달려도 안내가 나오게 한다 */
    fun reset() {
        statusFacilityId = null
        currentSegmentId = null
        warnedFacilities.clear()
        warnedZones.clear()
    }

    /** 프로필을 바꾸면 현재 구간 안내를 다시 하도록 초기화 */
    fun resetSegmentAnnouncement() {
        currentSegmentId = null
    }

    private fun roundTo50(m: Double): Int = max(50, (m / 50.0).roundToInt() * 50)
    private fun formatM(m: Double): String = String.format("%.1f", m)

    companion object {
        const val SEGMENT_MATCH_M = 50.0
        const val MIN_MOVING_MPS = 1.5f
        const val AHEAD_ANGLE_DEG = 30.0
        const val RESET_DISTANCE_M = 1000.0
        /** 안전 여유값: 시설물 높이가 (차 높이 + 0.1m)보다 낮으면 경고 */
        const val SAFETY_MARGIN_M = 0.1
        /** 높이 제한 카드를 계속 보여 주는 최대 거리 (DESIGN_WARN.md: 600m 넘게 멀어지면 사라짐) */
        const val STATUS_KEEP_M = 600.0
    }
}
