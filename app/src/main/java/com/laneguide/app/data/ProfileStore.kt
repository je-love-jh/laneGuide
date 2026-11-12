package com.laneguide.app.data

import android.content.Context
import com.laneguide.app.model.VehicleProfile

/** 차종 프로필과 차 높이를 기기에 저장한다. (2단계에서 차대번호별로 저장하도록 확장) */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    var profile: VehicleProfile
        get() = runCatching {
            VehicleProfile.valueOf(prefs.getString(KEY_PROFILE, null) ?: VehicleProfile.LIGHT_TRUCK.name)
        }.getOrDefault(VehicleProfile.LIGHT_TRUCK)
        set(value) = prefs.edit().putString(KEY_PROFILE, value.name).putBoolean(KEY_SAVED, true).apply()

    /** 운전자가 저장 버튼을 한 번이라도 눌렀는지 */
    val hasSaved: Boolean get() = prefs.getBoolean(KEY_SAVED, false)

    /** 적재물을 포함한 차 높이(m) */
    var heightM: Double
        get() = prefs.getFloat(KEY_HEIGHT, DEFAULT_HEIGHT_M).toDouble()
        set(value) = prefs.edit().putFloat(KEY_HEIGHT, value.toFloat()).apply()

    /** 운전자가 프로필을 확인(저장)한 차대번호. 다른 차에 앱을 쓰면 다시 추천한다. */
    var confirmedVin: String?
        get() = prefs.getString(KEY_VIN, null)
        set(value) = prefs.edit().putString(KEY_VIN, value).apply()

    companion object {
        private const val KEY_VIN = "confirmedVin"
        private const val KEY_SAVED = "saved"
        private const val KEY_PROFILE = "profile"
        private const val KEY_HEIGHT = "heightM"
        const val DEFAULT_HEIGHT_M = 2.0f
    }
}
