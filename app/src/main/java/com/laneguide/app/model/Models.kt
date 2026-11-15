package com.laneguide.app.model

import java.time.LocalTime

/** 차종 프로필. 프로필마다 안내하는 규칙이 달라진다. */
enum class VehicleProfile(val label: String) {
    PASSENGER("승용"),
    LIGHT_TRUCK("소형 화물"),
    HEAVY_TRUCK("중대형 화물");

    val isTruck: Boolean get() = this != PASSENGER
}

/** 차로 하나의 상태: 진입 금지 / 추월할 때만 / 주행 가능 */
enum class LaneState { FORBIDDEN, PASSING_ONLY, DRIVE }

data class GeoPoint(val lat: Double, val lon: Double)

/** 고속도로 구간: 이 선(path) 위를 달리면 편도 lanes차로 구간으로 본다. */
data class HighwaySegment(
    val id: String,
    val name: String,
    val lanes: Int,
    val path: List<GeoPoint>,
)

/** 높이 제한 시설물 (지하차도, 굴다리 등) */
data class HeightFacility(
    val id: String,
    val name: String,
    val limitM: Double,
    val point: GeoPoint,
    /** 이 시설물을 피해 가기 위해 경유지로 넣을 우회 지점 (없으면 우회 버튼을 띄우지 않는다) */
    val detour: Detour? = null,
)

data class Detour(val name: String, val point: GeoPoint)

/** 화물차 통행제한 구간 (중심점 + 반경으로 단순화) */
data class RestrictionZone(
    val id: String,
    val name: String,
    val center: GeoPoint,
    val radiusM: Double,
    val profiles: Set<VehicleProfile>,
    /** "07:00-10:00" 형식. 비어 있으면 항상 제한 */
    val timeWindows: List<String>,
) {
    fun isActive(now: LocalTime): Boolean {
        if (timeWindows.isEmpty()) return true
        return timeWindows.any { window ->
            val parts = window.split("-")
            if (parts.size != 2) return@any false
            val start = LocalTime.parse(parts[0].trim())
            val end = LocalTime.parse(parts[1].trim())
            if (start <= end) now >= start && now <= end
            else now >= start || now <= end // 자정을 넘기는 시간대
        }
    }
}

data class GoldenDataset(
    val dataDate: String,
    val note: String,
    val segments: List<HighwaySegment>,
    val facilities: List<HeightFacility>,
    val zones: List<RestrictionZone>,
)
