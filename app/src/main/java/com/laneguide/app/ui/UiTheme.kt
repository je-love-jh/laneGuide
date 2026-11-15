package com.laneguide.app.ui

import android.graphics.Color
import com.laneguide.app.model.LaneState

/**
 * 화면 테마. DESIGN.md 의 표를 그대로 옮긴 것이다.
 * 테마는 배경·장식만 바꾼다. 차로 색의 뜻(금지=빨강, 추월 시만=노랑, 주행=파랑)은 모든 테마에서 같다.
 */
class UiTheme(
    val id: String,
    val label: String,
    /** HUD(특수) 테마인가: 격자·꺾쇠·네온·주사선·무늬를 더 그린다 */
    val hud: Boolean,
    val bg: Int,
    val road: Int,
    val text: Int,
    val textSub: Int,
    val summary: Int,
    val accent: Int,
    val buttonBg: Int,
    val buttonBorder: Int,
    /** 선택된 버튼(설정) 바탕 */
    val selectedBg: Int,
    /** 화면 바깥 1px 테두리 (HUD) */
    val frameBorder: Int,
    // 안내 상자: 평상시(info) / 안내(tip, 노랑) / 경고(warn, 빨강) — 바탕·테두리·글자
    val infoBg: Int, val infoBorder: Int, val infoText: Int,
    val tipBg: Int, val tipBorder: Int, val tipText: Int,
    val warnBg: Int, val warnBorder: Int, val warnText: Int,
    // 차로 색
    val forbid: Int, val passing: Int, val drive: Int,
    val forbidIcon: Int, val passingIcon: Int, val driveIcon: Int,
    /** 차로 그라데이션 불투명도: 먼 쪽 → 가까운 쪽 (금지/추월/주행) */
    val alphaFar: Float,
    val alphaNearForbid: Float, val alphaNearPassing: Float, val alphaNearDrive: Float,
    /** 차로판 모양: 먼 쪽 y·반폭, 가까운 쪽 y·반폭 (640×500 기준) */
    val farY: Float, val nearY: Float, val farHalf: Float, val nearHalf: Float,
    val numberY: Float, val wordY: Float, val numberSize: Float,
) {
    fun laneColor(s: LaneState) = when (s) {
        LaneState.FORBIDDEN -> forbid
        LaneState.PASSING_ONLY -> passing
        LaneState.DRIVE -> drive
    }

    fun laneIconColor(s: LaneState) = when (s) {
        LaneState.FORBIDDEN -> forbidIcon
        LaneState.PASSING_ONLY -> passingIcon
        LaneState.DRIVE -> driveIcon
    }

    fun nearAlpha(s: LaneState) = when (s) {
        LaneState.FORBIDDEN -> alphaNearForbid
        LaneState.PASSING_ONLY -> alphaNearPassing
        LaneState.DRIVE -> alphaNearDrive
    }

    companion object {
        private fun c(hex: String) = Color.parseColor(hex)

        val BLACK = UiTheme(
            id = "BLACK", label = "블랙", hud = false,
            bg = c("#0B0E13"), road = c("#1B212B"),
            text = c("#F2F5F9"), textSub = c("#A9B4C2"), summary = c("#8FDBFF"), accent = c("#8FDBFF"),
            buttonBg = c("#18202B"), buttonBorder = c("#3A4656"), selectedBg = c("#12304A"),
            frameBorder = Color.TRANSPARENT,
            infoBg = c("#131A24"), infoBorder = c("#2A3442"), infoText = c("#C9D3E0"),
            tipBg = c("#3A2A05"), tipBorder = c("#F5A524"), tipText = c("#FFE9B8"),
            warnBg = c("#3A0D12"), warnBorder = c("#E5484D"), warnText = c("#FFD9DA"),
            forbid = c("#E5484D"), passing = c("#F5A524"), drive = c("#3DB8F5"),
            forbidIcon = c("#FFD9DA"), passingIcon = c("#FFEFC7"), driveIcon = c("#DDF4FF"),
            alphaFar = 0.10f, alphaNearForbid = 0.50f, alphaNearPassing = 0.62f, alphaNearDrive = 0.62f,
            farY = 30f, nearY = 480f, farHalf = 105f, nearHalf = 292f,
            numberY = 418f, wordY = 458f, numberSize = 44f,
        )

        val WHITE = UiTheme(
            id = "WHITE", label = "화이트", hud = false,
            bg = c("#EDF0F3"), road = c("#59616C"),
            text = c("#12161B"), textSub = c("#55606D"), summary = c("#0B5E8C"), accent = c("#0B5E8C"),
            buttonBg = c("#FFFFFF"), buttonBorder = c("#AEB7C2"), selectedBg = c("#DDEEF8"),
            frameBorder = Color.TRANSPARENT,
            infoBg = c("#FFFFFF"), infoBorder = c("#C3CAD3"), infoText = c("#2B343F"),
            tipBg = c("#FFF3D1"), tipBorder = c("#D98E04"), tipText = c("#5A3A00"),
            warnBg = c("#FFE4E6"), warnBorder = c("#D12F3A"), warnText = c("#7A1018"),
            forbid = c("#E5484D"), passing = c("#F5A524"), drive = c("#3DB8F5"),
            forbidIcon = c("#FFD9DA"), passingIcon = c("#FFEFC7"), driveIcon = c("#DDF4FF"),
            alphaFar = 0.35f, alphaNearForbid = 0.80f, alphaNearPassing = 0.85f, alphaNearDrive = 0.85f,
            farY = 30f, nearY = 480f, farHalf = 105f, nearHalf = 292f,
            numberY = 418f, wordY = 458f, numberSize = 44f,
        )

        val BLUE = UiTheme(
            id = "BLUE", label = "블루", hud = true,
            bg = c("#060C1B"), road = c("#0E1A30"),
            text = c("#F2F7FF"), textSub = c("#9DB7D6"), summary = c("#7FD6FF"), accent = c("#4CC9FF"),
            buttonBg = c("#0D2440"), buttonBorder = c("#4CC9FF"), selectedBg = c("#12365C"),
            frameBorder = c("#1E3A5F"),
            infoBg = c("#224CC9FF"), infoBorder = c("#4CC9FF"), infoText = c("#E6F6FF"),
            tipBg = c("#22FFB627"), tipBorder = c("#FFB627"), tipText = c("#FFEFC7"),
            warnBg = c("#22FF4D5E"), warnBorder = c("#FF4D5E"), warnText = c("#FFD9DD"),
            forbid = c("#FF4D5E"), passing = c("#FFB627"), drive = c("#35B8FF"),
            forbidIcon = c("#FFC2C8"), passingIcon = c("#FFE7A8"), driveIcon = c("#D6F2FF"),
            alphaFar = 0.08f, alphaNearForbid = 0.55f, alphaNearPassing = 0.55f, alphaNearDrive = 0.55f,
            farY = 26f, nearY = 484f, farHalf = 96f, nearHalf = 296f,
            numberY = 416f, wordY = 460f, numberSize = 46f,
        )

        val PURPLE = UiTheme(
            id = "PURPLE", label = "퍼플", hud = true,
            bg = c("#110922"), road = c("#1A1233"),
            text = c("#F2F7FF"), textSub = c("#B9ABD9"), summary = c("#8FDBFF"), accent = c("#B58CFF"),
            buttonBg = c("#22154A"), buttonBorder = c("#B58CFF"), selectedBg = c("#33206B"),
            frameBorder = c("#3A2A66"),
            infoBg = c("#22B58CFF"), infoBorder = c("#B58CFF"), infoText = c("#F1E8FF"),
            tipBg = c("#22FFB627"), tipBorder = c("#FFB627"), tipText = c("#FFEFC7"),
            warnBg = c("#22FF4D5E"), warnBorder = c("#FF4D5E"), warnText = c("#FFD9DD"),
            forbid = c("#FF4D5E"), passing = c("#FFB627"), drive = c("#35B8FF"),
            forbidIcon = c("#FFC2C8"), passingIcon = c("#FFE7A8"), driveIcon = c("#D6F2FF"),
            alphaFar = 0.08f, alphaNearForbid = 0.55f, alphaNearPassing = 0.55f, alphaNearDrive = 0.55f,
            farY = 26f, nearY = 484f, farHalf = 96f, nearHalf = 296f,
            numberY = 416f, wordY = 460f, numberSize = 46f,
        )

        val ALL = listOf(BLACK, WHITE, BLUE, PURPLE)

        fun byId(id: String?): UiTheme = ALL.firstOrNull { it.id == id } ?: BLACK
    }
}
