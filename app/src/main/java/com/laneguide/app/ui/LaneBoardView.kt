package com.laneguide.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.laneguide.app.model.LaneState

/**
 * 실시간 지정차로 안내 화면의 차로 그림.
 * 차로마다 빨강(진입 금지) / 노랑(추월할 때만) / 초록(주행 차로)으로 칠한다. 움직이는 효과는 없다.
 */
class LaneBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var lanes: List<LaneState> = emptyList()
    private val density = resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#9AA0A6")
        textSize = 18 * density
    }
    private val rect = RectF()

    fun setLanes(newLanes: List<LaneState>) {
        if (newLanes == lanes) return
        lanes = newLanes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lanes.isEmpty()) {
            canvas.drawText("고속도로에 들어서면 차로 안내가 표시됩니다", width / 2f, height / 2f, hint)
            return
        }
        val pad = 8 * density
        val gap = 8 * density
        val n = lanes.size
        val laneWidth = (width - pad * 2 - gap * (n - 1)) / n
        lanes.forEachIndexed { i, state ->
            val left = pad + i * (laneWidth + gap)
            rect.set(left, pad, left + laneWidth, height - pad)
            fill.color = when (state) {
                LaneState.FORBIDDEN -> Color.parseColor("#D93025")
                LaneState.PASSING_ONLY -> Color.parseColor("#F9AB00")
                LaneState.DRIVE -> Color.parseColor("#1E8E3E")
            }
            canvas.drawRoundRect(rect, 12 * density, 12 * density, fill)

            label.color = if (state == LaneState.PASSING_ONLY) Color.BLACK else Color.WHITE
            label.textSize = 20 * density
            canvas.drawText("${i + 1}차로", rect.centerX(), rect.top + 40 * density, label)
            label.textSize = 18 * density
            val word = when (state) {
                LaneState.FORBIDDEN -> "진입 금지"
                LaneState.PASSING_ONLY -> "추월 시만"
                LaneState.DRIVE -> "주행"
            }
            canvas.drawText(word, rect.centerX(), rect.centerY(), label)
        }
    }
}
