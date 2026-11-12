package com.laneguide.app.pleos

/**
 * 차량 신호(크루즈, 방향지시등) 연결 지점.
 *
 * 1단계(지금): 아무 신호도 보내지 않는 NoOpVehicleSignals 를 쓴다. 앱은 코어 기능만으로 동작한다.
 * 2단계: Pleos Vehicle SDK 로 구현한 클래스를 만들어 바꿔 끼운다.
 *   - 크루즈: DrivingMode.registerCruiseControlState (권한 CAR_DRIVE), 상태가 ACTIVATED 일 때
 *   - 방향지시등: TurnSignal.registerTurnSignalState (권한 CAR_LIGHTS), 좌측 점등일 때
 */
interface VehicleSignals {
    fun start(onCruiseActivated: () -> Unit, onLeftTurnSignal: () -> Unit)
    fun stop()
}

class NoOpVehicleSignals : VehicleSignals {
    override fun start(onCruiseActivated: () -> Unit, onLeftTurnSignal: () -> Unit) = Unit
    override fun stop() = Unit
}
