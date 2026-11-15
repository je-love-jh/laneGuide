package com.laneguide.app.data

import android.content.Context
import com.laneguide.app.model.Detour
import com.laneguide.app.model.GeoPoint
import com.laneguide.app.model.GoldenDataset
import com.laneguide.app.model.HeightFacility
import com.laneguide.app.model.HighwaySegment
import com.laneguide.app.model.RestrictionZone
import com.laneguide.app.model.VehicleProfile
import org.json.JSONArray
import org.json.JSONObject

/** assets/golden_dataset.json 을 읽어 앱에서 쓰는 형태로 바꾼다. 네트워크 없이 동작. */
object DatasetLoader {

    fun load(context: Context): GoldenDataset {
        val text = context.assets.open("golden_dataset.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        return GoldenDataset(
            dataDate = root.optString("dataDate"),
            note = root.optString("note"),
            segments = root.getJSONArray("segments").objects().map { o ->
                HighwaySegment(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    lanes = o.getInt("lanes"),
                    path = o.getJSONArray("path").points(),
                )
            },
            facilities = root.getJSONArray("facilities").objects().map { o ->
                HeightFacility(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    limitM = o.getDouble("limitM"),
                    point = GeoPoint(o.getDouble("lat"), o.getDouble("lon")),
                    detour = o.optJSONObject("detour")?.let { d ->
                        Detour(d.getString("name"), GeoPoint(d.getDouble("lat"), d.getDouble("lon")))
                    },
                )
            },
            zones = root.getJSONArray("zones").objects().map { o ->
                RestrictionZone(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    center = GeoPoint(o.getDouble("lat"), o.getDouble("lon")),
                    radiusM = o.getDouble("radiusM"),
                    profiles = o.getJSONArray("profiles").strings()
                        .mapNotNull { runCatching { VehicleProfile.valueOf(it) }.getOrNull() }
                        .toSet(),
                    timeWindows = o.optJSONArray("timeWindows")?.strings() ?: emptyList(),
                )
            },
        )
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.points(): List<GeoPoint> =
        (0 until length()).map { val p = getJSONArray(it); GeoPoint(p.getDouble(0), p.getDouble(1)) }
}
