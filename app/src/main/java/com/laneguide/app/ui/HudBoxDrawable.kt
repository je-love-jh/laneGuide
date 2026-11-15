package com.laneguide.app.ui

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * HUD(블루·퍼플) 테마의 상자·버튼 배경: 바탕 + 1~2px 테두리 + 바깥 번짐 + 안쪽 번짐 + (선택) 네 모서리 꺾쇠.
 * 바깥 번짐이 잘리지 않도록 이 배경을 쓰는 뷰의 부모는 clipChildren=false 여야 한다.
 */
class HudBoxDrawable(
    private val fill: Int,
    private val border: Int,
    private val borderPx: Float,
    private val glow: Int,
    private val glowPx: Float,
    private val innerGlow: Int,
    private val innerPx: Float,
    private val radiusPx: Float,
    private val bracketPx: Float,
    private val bracketColor: Int,
    private val density: Float,
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val half = borderPx / 2f
        rect.set(b.left + half, b.top + half, b.right - half, b.bottom - half)

        // 바깥 번짐
        if (glowPx > 0f && (glow ushr 24) != 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = borderPx * 2f
            paint.color = glow
            paint.maskFilter = BlurMaskFilter(glowPx, BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
            paint.maskFilter = null
        }
        // 바탕
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        // 안쪽 번짐 (상자 안으로만)
        if (innerPx > 0f && (innerGlow ushr 24) != 0) {
            canvas.save()
            canvas.clipRect(b)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = innerPx
            paint.color = innerGlow
            paint.maskFilter = BlurMaskFilter(innerPx / 2f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
            paint.maskFilter = null
            canvas.restore()
        }
        // 테두리
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = borderPx
        paint.color = border
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        // 네 모서리 꺾쇠
        if (bracketPx > 0f) {
            paint.strokeWidth = 3f * density
            paint.strokeCap = Paint.Cap.SQUARE
            paint.color = bracketColor
            val t = 1.5f * density
            val l = b.left + t; val r = b.right - t; val tp = b.top + t; val bt = b.bottom - t
            canvas.drawLines(floatArrayOf(l, tp, l + bracketPx, tp, l, tp, l, tp + bracketPx), paint)
            canvas.drawLines(floatArrayOf(r, tp, r - bracketPx, tp, r, tp, r, tp + bracketPx), paint)
            canvas.drawLines(floatArrayOf(l, bt, l + bracketPx, bt, l, bt, l, bt - bracketPx), paint)
            canvas.drawLines(floatArrayOf(r, bt, r - bracketPx, bt, r, bt, r, bt - bracketPx), paint)
            paint.strokeCap = Paint.Cap.BUTT
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
