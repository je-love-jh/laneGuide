package com.laneguide.app.demo

import android.content.Context
import com.laneguide.app.geo.Geo
import com.laneguide.app.model.GeoPoint

/** [시연 주행]에서 고를 수 있는 경로. 좌표는 assets 의 GPX 파일. */
enum class DemoRouteId(val label: String, val asset: String) {
    HIGHWAY("고속도로", "demo_drive.gpx"),
    UNDERPASS("굴다리", "demo_underpass.gpx"),
}

/**
 * GPX 경로를 따라 "몇 m 갔다"로 위치를 구한다.
 * 시연 주행은 GPS 대신 (차량 속도 × 시간)만큼 이 경로를 따라 전진한 위치를 쓴다.
 */
class DemoRoute private constructor(val id: DemoRouteId, private val points: List<GeoPoint>) {

    /** 시작점부터 각 지점까지의 누적 거리(m) */
    private val cumulative: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + Geo.distanceM(points[i - 1], points[i])
    }

    val totalM: Double get() = cumulative.last()

    data class Fix(val position: GeoPoint, val bearingDeg: Float)

    /** 시작점에서 [distanceM] 만큼 간 위치와 그곳의 진행 방향 (경로 끝을 넘으면 끝점) */
    fun fixAt(distanceM: Double): Fix {
        val d = distanceM.coerceIn(0.0, totalM)
        var i = 1
        while (i < points.size - 1 && cumulative[i] < d) i++
        val a = points[i - 1]
        val b = points[i]
        val seg = cumulative[i] - cumulative[i - 1]
        val t = if (seg <= 0.0) 0.0 else ((d - cumulative[i - 1]) / seg).coerceIn(0.0, 1.0)
        val pos = GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
        return Fix(pos, Geo.bearingDeg(a, b).toFloat())
    }

    companion object {
        fun load(context: Context, id: DemoRouteId): DemoRoute {
            val text = context.assets.open(id.asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
            // <trkpt lat="37.312610" lon="127.103450"> 만 뽑는다 (속성 순서가 바뀌어도 읽도록 각각 찾음)
            val points = Regex("<trkpt\\b[^>]*>").findAll(text).mapNotNull { m ->
                val tag = m.value
                val lat = Regex("lat=\"([-0-9.]+)\"").find(tag)?.groupValues?.get(1)?.toDoubleOrNull()
                val lon = Regex("lon=\"([-0-9.]+)\"").find(tag)?.groupValues?.get(1)?.toDoubleOrNull()
                if (lat != null && lon != null) GeoPoint(lat, lon) else null
            }.toList()
            require(points.size >= 2) { "${id.asset}: 경로 지점이 2개 미만입니다" }
            return DemoRoute(id, points)
        }
    }
}
