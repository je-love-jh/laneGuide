package com.laneguide.app.rules

import com.laneguide.app.model.LaneState
import com.laneguide.app.model.VehicleProfile

/**
 * 고속도로 차로 규칙 (도로교통법 시행규칙 [별표 9] 열람본 기준, 원문 대조 전).
 *
 * 1차로(앞지르기 차로)를 뺀 나머지를 반으로 나눠 왼쪽 차로 수 = (n-1)/2 (내림),
 * 그 오른쪽이 화물차 지정차로. 지정차로 바로 왼쪽 차로는 앞지르기할 때만 잠시 이용 가능.
 */
object LaneRules {

    fun laneStates(lanes: Int, profile: VehicleProfile): List<LaneState> {
        if (lanes <= 0) return emptyList()
        if (!profile.isTruck) {
            // 승용: 1차로는 추월할 때만, 나머지는 주행 가능
            return List(lanes) { i -> if (i == 0 && lanes >= 2) LaneState.PASSING_ONLY else LaneState.DRIVE }
        }
        if (lanes == 1) return listOf(LaneState.DRIVE)
        val leftCount = (lanes - 1) / 2
        val firstTruckLane = 2 + leftCount // 1부터 세는 차로 번호
        return List(lanes) { i ->
            val lane = i + 1
            when {
                lane >= firstTruckLane -> LaneState.DRIVE
                lane == firstTruckLane - 1 -> LaneState.PASSING_ONLY
                else -> LaneState.FORBIDDEN
            }
        }
    }

    private fun lanesOf(states: List<LaneState>, state: LaneState): List<Int> =
        states.withIndex().filter { it.value == state }.map { it.index + 1 }

    /** 화면 상단 한 줄 요약 */
    fun summary(lanes: Int, profile: VehicleProfile): String {
        if (!profile.isTruck) return "편도 ${lanes}차로 · 1차로는 추월할 때만"
        val drive = lanesOf(laneStates(lanes, profile), LaneState.DRIVE)
        return "편도 ${lanes}차로 · 화물차 ${drive.joinToString("·")}차로"
    }

    /** 구간이 바뀌거나 크루즈를 켤 때 읽어주는 문장 */
    fun announcement(lanes: Int, profile: VehicleProfile): String {
        if (!profile.isTruck) {
            return "현재 구간은 편도 ${lanes}차로입니다. 1차로는 추월할 때만 이용하세요."
        }
        val states = laneStates(lanes, profile)
        val drive = lanesOf(states, LaneState.DRIVE).joinToString(", ")
        val passing = lanesOf(states, LaneState.PASSING_ONLY).firstOrNull()
        val passingText = if (passing != null) " 앞지르기할 때는 ${passing}차로를 잠시 이용할 수 있습니다." else ""
        return "현재 구간은 편도 ${lanes}차로입니다. 화물차 지정차로는 ${drive}차로입니다.$passingText"
    }
}
