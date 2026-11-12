package com.laneguide.app.pleos

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener

/**
 * Pleos Vehicle SDK 로 차량 신호를 받는다.
 *  - 크루즈가 ACTIVATED 로 바뀌는 순간 → onCruiseActivated (권한 pleos.car.permission.CAR_DRIVE)
 *  - 방향지시등이 LEFT 로 바뀌는 순간  → onLeftTurnSignal  (권한 pleos.car.permission.CAR_LIGHTS)
 *
 * SDK 콜백은 다른 스레드에서 올 수 있어서, 앱 쪽 콜백은 메인 스레드로 넘겨 실행한다.
 * 같은 상태가 반복해서 와도 "바뀌는 순간"에만 한 번 알린다.
 */
class PleosVehicleSignals(context: Context) : VehicleSignals {

    private val vehicle = Vehicle(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())

    private var lastCruise: CruiseControl? = null
    private var lastTurn: TurnSignalValue? = null
    private var cruiseListener: CruiseControlStateListener? = null
    private var turnListener: TurnSignalStateListener? = null

    override fun start(onCruiseActivated: () -> Unit, onLeftTurnSignal: () -> Unit) {
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle 서비스 연결 실패: 차량 신호 없이 동작합니다", e)
            return
        }

        val cruise = object : CruiseControlStateListener {
            override fun onCruiseControlStateUpdated(state: CruiseControl?) {
                val prev = lastCruise
                lastCruise = state
                Log.d(TAG, "크루즈 상태: $state")
                if (state == CruiseControl.ACTIVATED && prev != CruiseControl.ACTIVATED) {
                    main.post(onCruiseActivated)
                }
            }
            override fun onFailed(e: Exception) {
                Log.w(TAG, "크루즈 상태 수신 실패", e)
            }
        }
        val turn = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                val prev = lastTurn
                lastTurn = state
                Log.d(TAG, "방향지시등: $state")
                if (state == TurnSignalValue.LEFT && prev != TurnSignalValue.LEFT) {
                    main.post(onLeftTurnSignal)
                }
            }
            override fun onFailed(e: Exception) {
                Log.w(TAG, "방향지시등 수신 실패", e)
            }
        }

        try {
            vehicle.getDrivingMode().registerCruiseControlState(cruise)
            cruiseListener = cruise
        } catch (e: Exception) {
            Log.w(TAG, "크루즈 상태 등록 실패 (권한 CAR_DRIVE 확인)", e)
        }
        try {
            vehicle.getTurnSignal().registerTurnSignalState(turn)
            turnListener = turn
        } catch (e: Exception) {
            Log.w(TAG, "방향지시등 등록 실패 (권한 CAR_LIGHTS 확인)", e)
        }
    }

    override fun stop() {
        try {
            cruiseListener?.let { vehicle.getDrivingMode().unregisterCruiseControlState(it) }
            turnListener?.let { vehicle.getTurnSignal().unregisterTurnSignalState(it) }
        } catch (e: Exception) {
            Log.w(TAG, "리스너 해제 실패", e)
        }
        cruiseListener = null
        turnListener = null
        try { vehicle.release() } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "PleosVehicleSignals"

        /** 런타임에 요청해야 할 수 있는 Pleos 차량 권한 */
        val PERMISSIONS = listOf(
            "pleos.car.permission.CAR_DRIVE",
            "pleos.car.permission.CAR_LIGHTS",
        )
    }
}
