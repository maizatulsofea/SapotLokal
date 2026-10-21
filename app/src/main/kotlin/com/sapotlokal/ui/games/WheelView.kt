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

class WheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var values: List<Int> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val segmentColors = intArrayOf(
        ContextCompat.getColor(context, R.color.game_wheel_segment_1),
        ContextCompat.getColor(context, R.color.game_wheel_segment_2),
        ContextCompat.getColor(context, R.color.game_wheel_segment_3),
        ContextCompat.getColor(context, R.color.game_wheel_segment_4)
    )

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.game_wheel_hub)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.game_wheel_label)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - 8f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val sweep = 360f / values.size

        for (i in values.indices) {
            // Segment i is centred at (i * sweep) degrees clockwise from the top
            segmentPaint.color = segmentColors[i % segmentColors.size]
            canvas.drawArc(rect, -90f + i * sweep - sweep / 2, sweep, true, segmentPaint)

            canvas.save()
            canvas.rotate(i * sweep, cx, cy)
            canvas.drawText(
                values[i].toString(),
                cx,
                cy - radius * 0.68f + textPaint.textSize / 3,
                textPaint
            )
            canvas.restore()
        }

        canvas.drawCircle(cx, cy, radius * 0.12f, hubPaint)
    }
}
