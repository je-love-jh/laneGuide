package com.laneguide.app.car

import android.car.Car
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlin.math.roundToInt

/**
 * 차량 속도를 읽는다. Pleos Vehicle SDK(2.3.2)에는 현재 속도를 읽는 기능이 없어서,
 * 안드로이드 자동차 표준 CarPropertyManager 의 PERF_VEHICLE_SPEED 를 쓴다.
 * 권한: android.car.permission.CAR_SPEED (실행 중 허용 필요)
 * 값 단위: m/s. 에뮬레이터 가상 콘솔의 ACCEL(엑셀)로 바뀌는 차량 속도가 여기로 들어온다.
 */
class VehicleSpeedReader(private val context: Context) {

    /** 마지막으로 받은 차량 속도 (m/s) */
    @Volatile var speedMps: Float = 0f
        private set

    /** 속도 값을 한 번이라도 받았는지(읽을 수 있는 상태인지) */
    @Volatile var available: Boolean = false
        private set

    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var lastLoggedKmh = -1

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val v = value.value as? Float ?: return
            speedMps = v
            available = true
            val kmh = (v * 3.6f).roundToInt()
            if (kmh != lastLoggedKmh) {
                lastLoggedKmh = kmh
                Log.i(TAG, "차량 속도: ${kmh}km/h (${"%.2f".format(v)}m/s)")
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "차량 속도 오류 propId=$propId zone=$zone")
        }
    }

    fun start() {
        try {
            val c = Car.createCar(context)
            car = c
            val m = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            manager = m
            if (m == null) { Log.w(TAG, "CarPropertyManager 를 얻지 못했습니다"); return }
            val ok = m.registerCallback(callback, PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_UI)
            Log.i(TAG, "차량 속도 등록: $ok")
            // 등록 직후에도 현재 값을 한 번 읽어 둔다 (값이 안 바뀌면 이벤트가 오지 않으므로)
            runCatching { m.getFloatProperty(PERF_VEHICLE_SPEED, 0) }.getOrNull()?.let {
                speedMps = it; available = true
                Log.i(TAG, "차량 속도 현재값: ${(it * 3.6f).roundToInt()}km/h")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "차량 속도 권한(CAR_SPEED)이 없습니다. 허용해 주세요", e)
        } catch (e: Exception) {
            Log.w(TAG, "차량 속도를 읽을 수 없습니다", e)
        }
    }

    fun stop() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
        manager = null; car = null; available = false
    }

    companion object {
        private const val TAG = "VehicleSpeed"
        const val PERMISSION = "android.car.permission.CAR_SPEED"
        /** VehiclePropertyIds.PERF_VEHICLE_SPEED */
        private const val PERF_VEHICLE_SPEED = 0x11600207
    }
}
