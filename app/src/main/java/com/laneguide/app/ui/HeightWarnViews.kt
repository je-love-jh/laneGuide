package com.laneguide.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.laneguide.app.engine.HeightStatus
import kotlin.math.floor
import kotlin.math.min

/** 높이 경고의 붉은색 (DESIGN_WARN.md): 일반 #E5484D, HUD 테마 #FF4D5E, 화이트 테마 글자용은 조금 진하게 */
internal fun warnRed(theme: UiTheme): Int = if (theme.hud) Color.parseColor("#FF4D5E") else Color.parseColor("#E5484D")

/**
 * 화면 가장자리 붉은 빛(비네트). 네 변에서 안쪽으로 번지는 정적 그라데이션이다 — 깜빡이거나 움직이지 않는다.
 * 가장자리 불투명도 0.55(화이트 테마 0.7) → 짧은 변의 18% 안쪽에서 0. 터치는 막지 않는다(클릭 불가 뷰).
 */
class RedVignetteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var theme: UiTheme = UiTheme.BLACK
    private val paint = Paint()

    init { isClickable = false; isFocusable = false }

    fun setTheme(t: UiTheme) { theme = t; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val edge = min(w, h) * 0.18f
        val red = warnRed(theme)
        val alpha = if (theme.id == "WHITE") 0.7f else 0.55f
        val strong = (red and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
        val clear = red and 0x00FFFFFF
        fun edgeFill(x0: Float, y0: Float, x1: Float, y1: Float, rect: RectF) {
            paint.shader = LinearGradient(x0, y0, x1, y1, strong, clear, Shader.TileMode.CLAMP)
            canvas.drawRect(rect, paint)
        }
        edgeFill(0f, 0f, 0f, edge, RectF(0f, 0f, w, edge))               // 위
        edgeFill(0f, h, 0f, h - edge, RectF(0f, h - edge, w, h))         // 아래
        edgeFill(0f, 0f, edge, 0f, RectF(0f, 0f, edge, h))               // 왼쪽
        edgeFill(w, 0f, w - edge, 0f, RectF(w - edge, 0f, w, h))         // 오른쪽
        paint.shader = null
    }
}

/**
 * 차로판 자리에 그리는 높이 제한 카드 (DESIGN_WARN.md).
 * - 통과 불가(blocked): 큰 경고 카드 — 제한 표지, "전방 250 m", "높이 제한 2.8 m", "내 차 3.0 m · 통과할 수 없습니다", "현장 표지를 확인하세요"
 * - 통과 가능: 붉은색 없는 작은 정보 카드 — 표지, "전방 250 m · 높이 제한 2.8 m", "내 차 2.0 m · 통과 가능"
 * 글자 크기는 sp(= 기준 화면 1단위)로 쓰고, 칸이 모자라면 한꺼번에 줄인다. 거리는 50m 단위로 내림한다.
 */
class HeightCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var theme: UiTheme = UiTheme.BLACK
    private var status: HeightStatus? = null

    /** 가로(전체 화면) 배치면 글자를 더 크게 쓴다 */
    var wide = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val black800 = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val bold700 = Typeface.create("sans-serif", Typeface.BOLD)

    init { isClickable = false; isFocusable = false }

    fun setTheme(t: UiTheme) { theme = t; invalidate() }
    fun setStatus(s: HeightStatus?) { status = s; invalidate() }

    private val d get() = resources.displayMetrics.density
    private fun sp(v: Float) = v * d

    override fun onDraw(canvas: Canvas) {
        val s = status ?: return
        if (s.blocked) drawWarn(canvas, s) else drawPass(canvas, s)
    }

    /** 50m 단위 내림: 250 → 200 → 150 … (50m 안쪽은 "50 m 이내") */
    private fun distanceText(m: Int): String {
        val f = floor(m / 50.0).toInt() * 50
        return if (f < 50) "전방 50 m 이내" else "전방 $f m"
    }

    private fun m1(v: Double) = String.format("%.1f", v)

    private fun drawWarn(canvas: Canvas, s: HeightStatus) {
        val margin = sp(if (wide) 40f else 24f)
        val card = RectF(margin, margin * 0.5f, width - margin, height - margin * 0.5f)
        val red = warnRed(theme)
        val radius = if (theme.hud) 0f else sp(16f)
        // 배경: 테마 도로 바탕색 위에 붉은색 12%
        fill.style = Paint.Style.FILL
        fill.color = theme.road
        canvas.drawRoundRect(card, radius, radius, fill)
        fill.color = (red and 0x00FFFFFF) or (0x1F shl 24)
        canvas.drawRoundRect(card, radius, radius, fill)
        stroke.color = red
        stroke.strokeWidth = sp(2f)
        canvas.drawRoundRect(card, radius, radius, stroke)
        if (theme.hud) drawBrackets(canvas, card, red)

        val big = if (wide) 96f else 64f
        val mid = if (wide) 52f else 36f
        val small = if (wide) 34f else 26f
        val tiny = if (wide) 26f else 20f
        val signD = card.height() * 0.35f
        val gap = sp(if (wide) 14f else 10f)
        // 칸에 안 들어가면 비율로 줄인다
        val total = signD + gap + sp(big) * 1.15f + gap * 0.6f + sp(mid) * 1.2f + gap * 0.6f + sp(small) * 1.2f + gap * 0.6f + sp(tiny) * 1.2f
        val k = min(1f, (card.height() - sp(24f)) / total)
        var y = card.centerY() - total * k / 2f
        val cx = card.centerX()

        drawSign(canvas, cx, y + signD * k / 2f, signD * k / 2f, s.limitM, red)
        y += (signD + gap) * k

        val textColor = if (theme.id == "WHITE") Color.parseColor("#12161B") else Color.WHITE
        y = line(canvas, distanceText(s.distanceM), cx, y, sp(big) * k, 1.15f, black800, textColor)
        y += gap * 0.6f * k
        val redText = if (theme.id == "WHITE") Color.parseColor("#D12F3A") else red
        y = line(canvas, "높이 제한 ${m1(s.limitM)} m", cx, y, sp(mid) * k, 1.2f, bold700, redText)
        y += gap * 0.6f * k
        y = line(canvas, "내 차 ${m1(s.vehicleM)} m · 통과할 수 없습니다", cx, y, sp(small) * k, 1.2f, bold700, theme.text)
        y += gap * 0.6f * k
        line(canvas, "현장 표지를 확인하세요", cx, y, sp(tiny) * k, 1.2f, Typeface.DEFAULT, theme.textSub)
    }

    private fun drawPass(canvas: Canvas, s: HeightStatus) {
        val margin = sp(if (wide) 40f else 24f)
        val cardH = height * 0.55f
        val card = RectF(margin, (height - cardH) / 2f, width - margin, (height + cardH) / 2f)
        val radius = if (theme.hud) 0f else sp(16f)
        fill.style = Paint.Style.FILL
        fill.color = theme.infoBg
        canvas.drawRoundRect(card, radius, radius, fill)
        stroke.color = theme.accent
        stroke.strokeWidth = sp(2f)
        canvas.drawRoundRect(card, radius, radius, stroke)
        if (theme.hud) drawBrackets(canvas, card, theme.accent)

        val l1 = if (wide) 44f else 30f
        val l2 = if (wide) 34f else 26f
        // 표지 지름 = 경고 카드 표지(카드 높이 35%)의 70%
        val signD = height * 0.35f * 0.7f
        val gap = sp(if (wide) 14f else 10f)
        val total = signD + gap + sp(l1) * 1.25f + gap * 0.5f + sp(l2) * 1.25f
        val k = min(1f, (card.height() - sp(20f)) / total)
        var y = card.centerY() - total * k / 2f
        val cx = card.centerX()
        drawSign(canvas, cx, y + signD * k / 2f, signD * k / 2f, s.limitM, warnRed(theme))
        y += (signD + gap) * k
        y = line(canvas, "${distanceText(s.distanceM)} · 높이 제한 ${m1(s.limitM)} m", cx, y, sp(l1) * k, 1.25f, bold700, theme.text)
        y += gap * 0.5f * k
        line(canvas, "내 차 ${m1(s.vehicleM)} m · 통과 가능", cx, y, sp(l2) * k, 1.25f, bold700, theme.summary)
    }

    /** 높이 제한 표지: 붉은 원 테두리(지름의 12%) 안에 흰 바탕, 검은 글자로 "2.8m" */
    private fun drawSign(canvas: Canvas, cx: Float, cy: Float, r: Float, limitM: Double, red: Int) {
        fill.style = Paint.Style.FILL
        fill.color = Color.WHITE
        canvas.drawCircle(cx, cy, r, fill)
        val ring = r * 2f * 0.12f
        stroke.color = red
        stroke.strokeWidth = ring
        canvas.drawCircle(cx, cy, r - ring / 2f, stroke)
        val label = "${m1(limitM)}m"
        text.typeface = black800
        text.color = Color.parseColor("#12161B")
        text.textSize = r * 0.62f
        // 글자가 테두리 안쪽 폭(지름의 약 62%)에 들어가게
        val maxW = (r - ring) * 2f * 0.84f
        val w = text.measureText(label)
        if (w > maxW) text.textSize = text.textSize * maxW / w
        val fm = text.fontMetrics
        canvas.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, text)
    }

    /** 한 줄을 그리고, 다음 줄의 y 를 돌려준다 */
    private fun line(canvas: Canvas, s: String, cx: Float, top: Float, size: Float, lineMul: Float, face: Typeface, color: Int): Float {
        text.typeface = face
        text.color = color
        text.textSize = size
        // 카드 폭보다 길면 폭에 맞춘다
        val maxW = width - sp(if (wide) 120f else 72f)
        val w = text.measureText(s)
        if (w > maxW) text.textSize = size * maxW / w
        val fm = text.fontMetrics
        val h = size * lineMul
        val baseline = top + h / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(s, cx, baseline, text)
        return top + h
    }

    private fun drawBrackets(canvas: Canvas, r: RectF, color: Int) {
        stroke.color = color
        stroke.strokeWidth = sp(3f)
        stroke.strokeCap = Paint.Cap.SQUARE
        val l = sp(if (wide) 28f else 22f)
        val m = sp(8f)
        canvas.drawLines(floatArrayOf(r.left + m, r.top + m, r.left + m + l, r.top + m, r.left + m, r.top + m, r.left + m, r.top + m + l), stroke)
        canvas.drawLines(floatArrayOf(r.right - m, r.top + m, r.right - m - l, r.top + m, r.right - m, r.top + m, r.right - m, r.top + m + l), stroke)
        canvas.drawLines(floatArrayOf(r.left + m, r.bottom - m, r.left + m + l, r.bottom - m, r.left + m, r.bottom - m, r.left + m, r.bottom - m - l), stroke)
        canvas.drawLines(floatArrayOf(r.right - m, r.bottom - m, r.right - m - l, r.bottom - m, r.right - m, r.bottom - m, r.right - m, r.bottom - m - l), stroke)
        stroke.strokeCap = Paint.Cap.BUTT
    }
}
