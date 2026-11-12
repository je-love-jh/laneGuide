package com.laneguide.app.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log

/**
 * 주행 중 제한(CarUxRestrictions)을 감시한다.
 * 플랫폼이 "설정 금지(NO_SETUP)"를 내려주면 onLockChanged(true) 를 호출해 설정 화면을 잠근다.
 */
class DrivingRestrictionWatcher(
    private val context: Context,
    private val onLockChanged: (Boolean) -> Unit,
) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        try {
            car = Car.createCar(context)
            manager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            manager?.registerListener { restrictions -> onLockChanged(isSetupLocked(restrictions)) }
            manager?.currentCarUxRestrictions?.let { onLockChanged(isSetupLocked(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "주행 제한 정보를 받을 수 없습니다. 설정은 잠그지 않습니다.", e)
            onLockChanged(false)
        }
    }

    private fun isSetupLocked(r: CarUxRestrictions): Boolean =
        r.isRequiresDistractionOptimization &&
            (r.activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP) != 0

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
    }

    private companion object {
        const val TAG = "DrivingRestriction"
    }
}
