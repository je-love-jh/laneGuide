package com.laneguide.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.FrameLayout

/** 화면 전체 배경. 테마 배경색을 칠하고, HUD 테마면 격자(32px 간격, accent 6~7% 1px 선)를 더 그린다. */
class GridBackground @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var theme: UiTheme = UiTheme.BLACK
    private val gridPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f }

    /** 가로(전체 화면) 배치면 격자 간격 40, 아니면 32 */
    var wide = false

    init { setWillNotDraw(false) }

    fun setTheme(t: UiTheme) {
        theme = t
        setBackgroundColor(t.bg)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!theme.hud) return
        val d = resources.displayMetrics.density
        val step = (if (wide) 40f else 32f) * d
        // 격자: accent 색 7%
        gridPaint.color = (theme.accent and 0x00FFFFFF) or (0x12 shl 24)
        var x = 0f
        while (x <= width) { canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += step }
        var y = 0f
        while (y <= height) { canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += step }
    }
}

/** HUD 테마 머리 오른쪽 위 눈금자: 8px 간격, 5칸마다 긴 눈금, accent 50% (162×10) */
class HudRulerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : android.view.View(context, attrs) {

    private var theme: UiTheme = UiTheme.BLACK
    private val p = Paint().apply { strokeWidth = 1f }

    fun setTheme(t: UiTheme) {
        theme = t
        visibility = if (t.hud) VISIBLE else GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        p.color = (theme.accent and 0x00FFFFFF) or (0x80 shl 24)
        p.strokeWidth = maxOf(1f, d)
        var i = 0
        var x = 0f
        while (x <= 160f) {
            val h = if (i % 5 == 0) 10f else 5f
            canvas.drawLine(x * d + 0.5f, 0f, x * d + 0.5f, h * d, p)
            i++; x += 8f
        }
    }
}

/**
 * 640×900 기준 화면(무대). HUD 테마일 때 바깥 1px 테두리와 네 모서리 꺾쇠(accent, 22px, 굵기 3)를 그린다.
 * 블랙·화이트에서는 아무것도 더하지 않는다.
 */
class StageFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var theme: UiTheme = UiTheme.BLACK

    /** 가로(전체 화면) 배치면 꺾쇠 28(모서리에서 8), 아니면 22(모서리에서 6) */
    var wide = false
    private val border = Paint().apply { style = Paint.Style.STROKE }
    private val bracket = Paint().apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.SQUARE }

    init { setWillNotDraw(false) }

    fun setTheme(t: UiTheme) {
        theme = t
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!theme.hud) return
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        border.color = theme.frameBorder
        border.strokeWidth = d
        canvas.drawRect(d / 2, d / 2, w - d / 2, h - d / 2, border)
        bracket.color = theme.accent
        bracket.strokeWidth = 3f * d
        val m = (if (wide) 8f else 6f) * d + 1.5f * d
        val L = (if (wide) 28f else 22f) * d
        // 네 모서리 꺾쇠
        canvas.drawLines(floatArrayOf(m, m, m + L, m, m, m, m, m + L), bracket)
        canvas.drawLines(floatArrayOf(w - m, m, w - m - L, m, w - m, m, w - m, m + L), bracket)
        canvas.drawLines(floatArrayOf(m, h - m, m + L, h - m, m, h - m, m, h - m - L), bracket)
        canvas.drawLines(floatArrayOf(w - m, h - m, w - m - L, h - m, w - m, h - m, w - m, h - m - L), bracket)
    }
}
