package com.laneguide.app.ui

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.laneguide.app.model.LaneState
import kotlin.math.min

/**
 * 지정차로 차로판: 소실점 쪽으로 좁아지는 원근 도로. (DESIGN.md "차로판 그리기")
 * 640×500 좌표계로 그린 뒤 뷰 크기에 맞춰 비례해서 늘리거나 줄인다. 내 차는 그리지 않고, 움직이는 효과도 없다.
 * 차로 색의 뜻은 모든 테마에서 같고, 색만으로 구분하지 않도록 아이콘·글자를 같이 그린다.
 */
class LaneBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var lanes: List<LaneState> = emptyList()
    private var theme: UiTheme = UiTheme.BLACK

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-black", Typeface.NORMAL); color = 0xFFFFFFFF.toInt()
    }
    private val word = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = 0xFFFFFFFF.toInt()
    }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()

    fun setLanes(newLanes: List<LaneState>) {
        if (newLanes == lanes) return
        lanes = newLanes
        invalidate()
    }

    fun setTheme(newTheme: UiTheme) {
        if (newTheme === theme) return
        theme = newTheme
        invalidate()
    }

    // ---------- 원근 도로 계산 ----------

    private fun half(y: Float): Float {
        val t = (y - theme.farY) / (theme.nearY - theme.farY)
        return theme.farHalf + (theme.nearHalf - theme.farHalf) * t
    }

    /** 높이 y 에서 i 번째 경계선의 x (i = 0 은 왼쪽 끝, n 은 오른쪽 끝, 소수도 가능) */
    private fun edgeX(i: Float, n: Int, y: Float): Float {
        val h = half(y)
        return CENTER_X - h + 2f * h * i / n
    }

    private fun trapezoid(from: Float, to: Float, n: Int, out: Path) {
        out.reset()
        out.moveTo(edgeX(from, n, theme.farY), theme.farY)
        out.lineTo(edgeX(to, n, theme.farY), theme.farY)
        out.lineTo(edgeX(to, n, theme.nearY), theme.nearY)
        out.lineTo(edgeX(from, n, theme.nearY), theme.nearY)
        out.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lanes.isEmpty()) {
            hint.color = theme.textSub
            // 안내 글자도 뷰 크기에 비례 (640×500 기준 22)
            hint.textSize = 22f * min(width / BOARD_W, height / BOARD_H)
            canvas.drawText("고속도로에 들어서면 차로 안내가 표시됩니다", width / 2f, height / 2f, hint)
            return
        }
        // 640×500 좌표계를 뷰 안에 비례해서 가운데로
        val s = min(width / BOARD_W, height / BOARD_H)
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - BOARD_W * s) / 2f, (height - BOARD_H * s) / 2f)
        canvas.scale(s, s)
        drawBoard(canvas, s)
        canvas.restore()
    }

    private fun drawBoard(canvas: Canvas, s: Float) {
        if (theme.hud) drawHud(canvas, s) else drawPlain(canvas, s)
    }

    // ---------- 블랙·화이트 ----------

    private fun drawPlain(canvas: Canvas, s: Float) {
        val n = lanes.size
        val th = theme

        // 1) 도로 바탕
        fill.shader = null
        fill.color = th.road
        trapezoid(0f, n.toFloat(), n, path)
        canvas.drawPath(path, fill)

        // 2) 차로마다 상태 색 세로 그라데이션 (먼 쪽 투명 → 가까운 쪽 진함)
        lanes.forEachIndexed { i, state ->
            val base = th.laneColor(state) and 0x00FFFFFF
            fun argb(a: Float) = (base or ((a * 255).toInt().coerceIn(0, 255) shl 24))
            fill.shader = LinearGradient(0f, th.farY, 0f, th.nearY, argb(th.alphaFar), argb(th.nearAlpha(state)), Shader.TileMode.CLAMP)
            trapezoid(i.toFloat(), (i + 1).toFloat(), n, path)
            canvas.drawPath(path, fill)
        }
        fill.shader = null

        // 3) 주행 차로 묶음의 양쪽 바깥 경계: 밝은 파란 선 + 은은한 번짐
        val firstDrive = lanes.indexOfFirst { it == LaneState.DRIVE }
        val lastDrive = lanes.indexOfLast { it == LaneState.DRIVE }
        val edgeBoundaries = mutableSetOf<Int>()
        if (firstDrive >= 0) {
            edgeBoundaries += firstDrive
            edgeBoundaries += lastDrive + 1
            glow.maskFilter = BlurMaskFilter(7f * s, BlurMaskFilter.Blur.NORMAL)
            glow.strokeWidth = 6f
            glow.color = EDGE_BLUE
            line.pathEffect = null
            line.strokeWidth = 2.5f
            line.color = EDGE_BLUE
            edgeBoundaries.forEach { b ->
                path.reset()
                path.moveTo(edgeX(b.toFloat(), n, th.farY), th.farY)
                path.lineTo(edgeX(b.toFloat(), n, th.nearY), th.nearY)
                canvas.drawPath(path, glow)
                canvas.drawPath(path, line)
            }
            glow.maskFilter = null
        }

        // 4) 안쪽 경계: 흰 점선 (파란 경계선이 있는 곳은 제외)
        line.strokeWidth = 3f
        line.color = 0xFFE8ECF2.toInt()
        line.alpha = (0.55f * 255).toInt()
        line.pathEffect = DashPathEffect(floatArrayOf(26f, 22f), 0f)
        for (b in 1 until n) {
            if (b in edgeBoundaries) continue
            path.reset()
            path.moveTo(edgeX(b.toFloat(), n, th.farY), th.farY)
            path.lineTo(edgeX(b.toFloat(), n, th.nearY), th.nearY)
            canvas.drawPath(path, line)
        }
        // 도로 양끝: 흰 실선
        line.pathEffect = null
        line.alpha = (0.8f * 255).toInt()
        for (b in intArrayOf(0, n)) {
            path.reset()
            path.moveTo(edgeX(b.toFloat(), n, th.farY), th.farY)
            path.lineTo(edgeX(b.toFloat(), n, th.nearY), th.nearY)
            canvas.drawPath(path, line)
        }
        line.alpha = 255

        // 5) 차로마다 아이콘·번호·상태 글자
        number.textSize = th.numberSize
        word.textSize = if (n >= 5) 17f else 19f
        lanes.forEachIndexed { i, state ->
            val mid = i + 0.5f
            drawIcon(canvas, state, edgeX(mid, n, ICON_Y), ICON_Y)
            canvas.drawText("${i + 1}", edgeX(mid, n, th.numberY), th.numberY, number)
            canvas.drawText(wordOf(state), edgeX(mid, n, th.wordY), th.wordY, word)
        }
    }

    // ---------- 블루·퍼플 (HUD) ----------

    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val scan = Paint().apply { style = Paint.Style.FILL; color = 0x12FFFFFF }
    private val ref = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val roadClip = Path()
    private val laneClip = Path()

    private fun neonCore(state: LaneState) = when (state) {
        LaneState.FORBIDDEN -> 0xFFFF8A95.toInt()
        LaneState.PASSING_ONLY -> 0xFFFFD77A.toInt()
        LaneState.DRIVE -> 0xFF9BE1FF.toInt()
    }

    private fun drawHud(canvas: Canvas, s: Float) {
        val n = lanes.size
        val th = theme
        trapezoid(0f, n.toFloat(), n, roadClip)

        // 1) 도로 바탕
        fill.shader = null
        fill.color = th.road
        canvas.drawPath(roadClip, fill)

        // 2) 차로 상태 색 그라데이션
        lanes.forEachIndexed { i, state ->
            val base = th.laneColor(state) and 0x00FFFFFF
            fun argb(a: Float) = (base or ((a * 255).toInt().coerceIn(0, 255) shl 24))
            fill.shader = LinearGradient(0f, th.farY, 0f, th.nearY, argb(th.alphaFar), argb(th.nearAlpha(state)), Shader.TileMode.CLAMP)
            trapezoid(i.toFloat(), (i + 1).toFloat(), n, path)
            canvas.drawPath(path, fill)
        }
        fill.shader = null

        // 3) 금지 차로: 45° 빨간 빗금(28%), 추월 차로: 금색 점(45%)
        lanes.forEachIndexed { i, state ->
            if (state == LaneState.DRIVE) return@forEachIndexed
            trapezoid(i.toFloat(), (i + 1).toFloat(), n, laneClip)
            canvas.save()
            canvas.clipPath(laneClip)
            if (state == LaneState.FORBIDDEN) {
                hatchPaint.color = (th.forbid and 0x00FFFFFF) or (0x47 shl 24)
                canvas.rotate(45f, CENTER_X, (th.farY + th.nearY) / 2f)
                var x = -400f
                while (x < 1000f) { canvas.drawRect(x, -400f, x + 3f, 900f, hatchPaint); x += 14f }
            } else {
                hatchPaint.color = 0x73FFD77A
                var y = th.farY
                while (y < th.nearY) {
                    var x = 0f
                    while (x < BOARD_W) { canvas.drawCircle(x + 2f, y + 2f, 1.2f, hatchPaint); x += 10f }
                    y += 10f
                }
            }
            canvas.restore()
        }

        // 4) 도로 전체 가로 주사선 (6px 간격, 흰색 7%)
        canvas.save()
        canvas.clipPath(roadClip)
        var sy = th.farY
        while (sy < th.nearY) { canvas.drawRect(0f, sy, BOARD_W, sy + 1f, scan); sy += 6f }
        canvas.restore()

        // 5) 상태가 바뀌는 경계와 도로 양끝: 상태 색 네온 선 (밝은 심 2.5 + 번짐 7)
        glow.maskFilter = BlurMaskFilter(6f * s, BlurMaskFilter.Blur.NORMAL)
        glow.strokeWidth = 7f
        line.pathEffect = null
        line.strokeWidth = 2.5f
        line.alpha = 255
        val neon = mutableSetOf<Int>()
        for (b in 0..n) {
            val changed = b == 0 || b == n || lanes[b - 1] != lanes[b]
            if (!changed) continue
            neon += b
            val st = lanes[if (b == n) n - 1 else b]
            path.reset()
            path.moveTo(edgeX(b.toFloat(), n, th.farY), th.farY)
            path.lineTo(edgeX(b.toFloat(), n, th.nearY), th.nearY)
            glow.color = th.laneColor(st)
            line.color = neonCore(st)
            canvas.drawPath(path, glow)
            canvas.drawPath(path, line)
        }
        glow.maskFilter = null

        // 6) 같은 상태 차로 사이: 흰 점선
        line.color = 0xFFFFFFFF.toInt()
        line.alpha = (0.6f * 255).toInt()
        line.pathEffect = DashPathEffect(floatArrayOf(24f, 22f), 0f)
        for (b in 1 until n) {
            if (b in neon) continue
            path.reset()
            path.moveTo(edgeX(b.toFloat(), n, th.farY), th.farY)
            path.lineTo(edgeX(b.toFloat(), n, th.nearY), th.nearY)
            canvas.drawPath(path, line)
        }
        line.pathEffect = null
        line.alpha = 255

        // 7) 원근 기준 가로선 3개 (accent 18%)
        ref.color = (th.accent and 0x00FFFFFF) or (0x2E shl 24)
        for (ry in floatArrayOf(120f, 200f, 300f)) {
            canvas.drawLine(edgeX(0f, n, ry), ry, edgeX(n.toFloat(), n, ry), ry, ref)
        }

        // 8) 아이콘·번호·상태 글자
        number.textSize = th.numberSize
        word.textSize = if (n >= 5) 17f else 19f
        lanes.forEachIndexed { i, state ->
            val mid = i + 0.5f
            drawIcon(canvas, state, edgeX(mid, n, ICON_Y), ICON_Y)
            canvas.drawText("${i + 1}", edgeX(mid, n, th.numberY), th.numberY, number)
            canvas.drawText(wordOf(state), edgeX(mid, n, th.wordY), th.wordY, word)
        }
    }

    private fun wordOf(s: LaneState) = when (s) {
        LaneState.FORBIDDEN -> "진입 금지"
        LaneState.PASSING_ONLY -> "추월 시만"
        LaneState.DRIVE -> "주행"
    }

    /** 색만으로 구분하지 않도록: 금지=✕, 추월 시만=꺾인 화살표, 주행=위쪽 화살표 */
    private fun drawIcon(canvas: Canvas, state: LaneState, cx: Float, cy: Float) {
        icon.color = theme.laneIconColor(state)
        path.reset()
        val hud = theme.hud
        // HUD 시안은 아이콘이 약간 크고(굵기 5) 아래로 2px 더 길다
        val bottom = if (hud) 20f else 18f
        val apex = if (hud) 19f else 18f
        when (state) {
            LaneState.FORBIDDEN -> {
                val a = if (hud) 17f else 16f
                icon.strokeWidth = if (hud) 5f else 6f
                path.moveTo(cx - a, cy - a); path.lineTo(cx + a, cy + a)
                path.moveTo(cx + a, cy - a); path.lineTo(cx - a, cy + a)
            }
            LaneState.PASSING_ONLY -> {
                icon.strokeWidth = 5f
                path.moveTo(cx - 10f, cy + bottom); path.lineTo(cx - 10f, cy - 4f)
                path.quadTo(cx - 10f, cy - 16f, cx + 4f, cy - 16f)
                path.moveTo(cx - 4f, cy - 26f); path.lineTo(cx + 8f, cy - 16f); path.lineTo(cx - 4f, cy - 6f)
            }
            LaneState.DRIVE -> {
                icon.strokeWidth = if (hud) 5f else 6f
                path.moveTo(cx, cy + bottom); path.lineTo(cx, cy - 16f)
                path.moveTo(cx - 13f, cy - 3f); path.lineTo(cx, cy - apex); path.lineTo(cx + 13f, cy - 3f)
            }
        }
        canvas.drawPath(path, icon)
    }

    private companion object {
        const val BOARD_W = 640f
        const val BOARD_H = 500f
        const val CENTER_X = 320f
        const val ICON_Y = 300f
        /** 블랙·화이트에서 주행 차로 묶음 바깥 경계선 색 (DESIGN.md) */
        const val EDGE_BLUE = 0xFF8FDBFF.toInt()
    }
}
