package com.sapotlokal.ui.games

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.sapotlokal.R
import kotlin.math.min

/**
 * WheelView — the Spin the Wheel face.
 * Cocoa and soft-blue slices with white dividers, a gold slice for the biggest prize, a studded
 * outer rim and a centre hub. The pointer is a separate view in the layout, because this whole
 * view rotates.
 */
class WheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var values: List<Int> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val cocoa = ContextCompat.getColor(context, R.color.hu_ink)
    private val soft = ContextCompat.getColor(context, R.color.hu_accent_tint)
    private val gold = ContextCompat.getColor(context, R.color.game_gold)
    private val light = ContextCompat.getColor(context, R.color.hu_bg)

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = light
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cocoa }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = light }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cocoa
        alpha = 40
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cocoa }
    private val hubCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 19f, resources.displayMetrics)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)   // needed for the blurred shadow
        shadowPaint.setShadowLayer(dp(10f), 0f, dp(4f), 0x55000000)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val cx = width / 2f
        val cy = height / 2f
        val outer = min(cx, cy) - dp(10f)           // rim radius
        val wheel = outer - dp(14f)                 // slices radius

        // Drop shadow + rim
        canvas.drawCircle(cx, cy, outer, shadowPaint)
        canvas.drawCircle(cx, cy, outer, rimPaint)

        // Studs around the rim
        val studs = values.size * 2
        for (i in 0 until studs) {
            val a = Math.toRadians(360.0 / studs * i)
            val x = cx + (outer - dp(7f)) * Math.sin(a).toFloat()
            val y = cy - (outer - dp(7f)) * Math.cos(a).toFloat()
            canvas.drawCircle(x, y, dp(2.4f), dotPaint)
        }

        // Slices
        val rect = RectF(cx - wheel, cy - wheel, cx + wheel, cy + wheel)
        val sweep = 360f / values.size
        val top = values.maxOrNull()
        for (i in values.indices) {
            val isTop = values[i] == top
            val bg = when {
                isTop -> gold
                i % 2 == 0 -> cocoa
                else -> soft
            }
            segmentPaint.color = bg
            // Slice i is centred at (i * sweep) degrees clockwise from the top.
            canvas.drawArc(rect, -90f + i * sweep - sweep / 2, sweep, true, segmentPaint)
        }
        // Dividers
        for (i in values.indices) {
            val a = Math.toRadians((i * sweep - sweep / 2).toDouble())
            canvas.drawLine(
                cx, cy,
                cx + wheel * Math.sin(a).toFloat(), cy - wheel * Math.cos(a).toFloat(),
                dividerPaint
            )
        }
        // Labels (white on cocoa, cocoa on light/gold)
        for (i in values.indices) {
            val onDark = !(values[i] == top) && i % 2 == 0
            textPaint.color = if (onDark) light else cocoa
            canvas.save()
            canvas.rotate(i * sweep, cx, cy)
            val label = values[i].toString()
            val baseline = cy - wheel * 0.68f + textPaint.textSize / 3
            canvas.drawText(label, cx, baseline, textPaint)
            // Labels turn with the wheel, so an upside-down 6 reads as 9. A bar under 6 and 9
            // (as on dice and pool balls) shows which way up they are.
            if (label == "6" || label == "9") {
                val half = textPaint.measureText(label) / 2
                val y = baseline + dp(3f)
                canvas.drawRect(cx - half, y, cx + half, y + dp(2f), textPaint)
            }
            canvas.restore()
        }

        // Hub
        canvas.drawCircle(cx, cy, wheel * 0.16f, hubPaint)
        canvas.drawCircle(cx, cy, wheel * 0.07f, hubCorePaint)
    }
}
