package com.laneguide.app.geo

import com.laneguide.app.model.GeoPoint
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 거리·방위각 계산. 구글 서비스(GMS) 없이 직접 계산한다. */
object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val METERS_PER_DEGREE = 111_320.0

    private fun rad(d: Double) = Math.toRadians(d)

    /** 두 지점 사이 거리(m), Haversine 공식 */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) +
            cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** a에서 b를 바라보는 방위각(도, 북쪽 0도 기준 시계 방향) */
    fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = rad(a.lat)
        val lat2 = rad(b.lat)
        val dLon = rad(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** 두 방위각의 차이(0~180도) */
    fun angleDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /** 점에서 선(여러 점을 이은 길)까지의 가장 가까운 거리(m). 짧은 거리용 근사 계산 */
    fun distanceToPathM(p: GeoPoint, path: List<GeoPoint>): Double {
        if (path.isEmpty()) return Double.MAX_VALUE
        if (path.size == 1) return distanceM(p, path[0])
        val k = cos(rad(p.lat))
        var best = Double.MAX_VALUE
        for (i in 0 until path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            val ax = (a.lon - p.lon) * k
            val ay = a.lat - p.lat
            val bx = (b.lon - p.lon) * k
            val by = b.lat - p.lat
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
            val cx = ax + t * dx
            val cy = ay + t * dy
            best = min(best, sqrt(cx * cx + cy * cy) * METERS_PER_DEGREE)
        }
        return best
    }
}
