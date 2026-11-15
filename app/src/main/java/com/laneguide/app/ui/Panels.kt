package com.laneguide.app.ui

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

private fun blend(a: Int, b: Int, t: Float): Int {
    fun mix(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
    return Color.argb(mix(Color.alpha(a), Color.alpha(b)), mix(Color.red(a), Color.red(b)), mix(Color.green(a), Color.green(b)), mix(Color.blue(a), Color.blue(b)))
}

private fun withAlpha(c: Int, alpha: Int) = (c and 0x00FFFFFF) or (alpha shl 24)

/** 안내 상자의 종류: 평상시(info) / 안내(tip, 노랑) / 경고(warn, 빨강) */
enum class AlertKind { INFO, TIP, WARN }

/** 테마에 맞는 상자·버튼 배경을 만든다. */
object Panels {

    fun alertColors(theme: UiTheme, kind: AlertKind): Triple<Int, Int, Int> = when (kind) {
        AlertKind.INFO -> Triple(theme.infoBg, theme.infoBorder, theme.infoText)
        AlertKind.TIP -> Triple(theme.tipBg, theme.tipBorder, theme.tipText)
        AlertKind.WARN -> Triple(theme.warnBg, theme.warnBorder, theme.warnText)
    }

    /** 안내 상자 배경. 블랙·화이트: 둥근 모서리 14, 테두리 2. HUD: 각진 모서리, 1px 테두리 + 번짐 + 꺾쇠 */
    fun alertBox(theme: UiTheme, kind: AlertKind, density: Float): Drawable {
        val (bg, border, _) = alertColors(theme, kind)
        if (theme.hud) {
            return HudBoxDrawable(
                fill = bg, border = border, borderPx = 1f * density,
                glow = withAlpha(border, 0x55), glowPx = 18f * density,
                innerGlow = withAlpha(border, 0x33), innerPx = 24f * density,
                radiusPx = 0f, bracketPx = 16f * density, bracketColor = border, density = density,
            )
        }
        return GradientDrawable().apply {
            setColor(bg)
            setStroke((2 * density).toInt().coerceAtLeast(1), border)
            cornerRadius = 14f * density
        }
    }

    /** 일반 버튼 배경 (눌렀을 때 살짝 밝게) */
    /**
     * 일반 버튼 배경. choice=true 는 여럿 중 하나를 고르는 버튼(설정): 고른 것만 굵은 accent 테두리·밝은 바탕,
     * HUD 에서는 고르지 않은 것을 흐리게 해서 구분한다.
     */
    fun button(theme: UiTheme, density: Float, selected: Boolean = false, choice: Boolean = false): Drawable {
        val radius = (if (theme.hud) 8f else 12f) * density
        val bg = if (selected) theme.selectedBg else theme.buttonBg
        val border = if (selected) theme.accent else theme.buttonBorder
        val strokeW = ((if (selected) 3 else 2) * density).toInt().coerceAtLeast(1)
        val dimHud = theme.hud && choice && !selected
        fun shape(fill: Int) = GradientDrawable().apply {
            setColor(fill)
            setStroke(strokeW, border)
            cornerRadius = radius
        }
        val pressed = blend(bg, theme.accent, 0.25f)
        if (theme.hud) {
            // HUD 버튼: accent 2px 테두리 + 번짐
            val base = if (dimHud) 0x70 else 0xFF
            val glowBase = if (dimHud) 0x1A else if (selected) 0x99 else 0x66
            fun hudBox(fill: Int, alpha: Int) = HudBoxDrawable(
                fill = fill, border = withAlpha(border, (base * alpha) / 0xFF), borderPx = strokeW.toFloat(),
                glow = withAlpha(theme.accent, (glowBase * alpha) / 0xFF), glowPx = 14f * density,
                innerGlow = 0, innerPx = 0f, radiusPx = radius, bracketPx = 0f, bracketColor = 0, density = density,
            )
            return StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), hudBox(pressed, 0xFF))
                addState(intArrayOf(-android.R.attr.state_enabled), hudBox(withAlpha(bg, 0x80), 0x80))
                addState(intArrayOf(), hudBox(bg, 0xFF))
            }
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(pressed))
            addState(intArrayOf(-android.R.attr.state_enabled), shape(withAlpha(bg, 0x80)))
            addState(intArrayOf(), shape(bg))
        }
    }

    /** 저장처럼 강조 버튼: accent 로 가득 채운다 */
    fun primaryButton(theme: UiTheme, density: Float): Drawable {
        val fill = if (theme.hud) theme.accent else Color.parseColor("#35B8FF")
        return GradientDrawable().apply { setColor(fill); cornerRadius = 12f * density }
    }
}
