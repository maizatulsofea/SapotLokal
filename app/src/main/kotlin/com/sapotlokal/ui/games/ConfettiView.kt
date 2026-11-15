package com.sapotlokal.ui.games

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * ConfettiView — a short celebratory burst of coloured paper (win, badge unlock).
 * Transparent and not clickable, so it can sit on top of any screen. Call [burst] to play it.
 */
class ConfettiView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Piece(
        val vx: Float, val vy: Float, val size: Float, val color: Int,
        val spin: Float, val round: Boolean
    )

    private val colors = intArrayOf(
        // Kopi Orange, ink and the shop palette (BRAND_DESIGN.md §3)
        0xFFF5A524.toInt(), 0xFF1A1C21.toInt(), 0xFFE0436A.toInt(), 0xFF4B4FC4.toInt(),
        0xFF1A9E63.toInt(), 0xFF2F6FDE.toInt(), 0xFF7B4FD6.toInt(), 0xFFE86A4A.toInt()
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pieces: List<Piece> = emptyList()
    private var progress = 0f
    private var originX = 0f
    private var originY = 0f
    private var animator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
    }

    /** Fires the confetti from [yFraction] of the view's height (0 = top, 1 = bottom). */
    fun burst(yFraction: Float = 0.35f) {
        val d = resources.displayMetrics.density
        originX = width / 2f
        originY = height * yFraction
        pieces = List(90) {
            val angle = Random.nextDouble(-Math.PI * 0.95, -Math.PI * 0.05)   // upward fan
            val speed = Random.nextFloat() * 520f * d + 240f * d
            Piece(
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed).toFloat(),
                size = (Random.nextFloat() * 6f + 5f) * d,
                color = colors.random(),
                spin = Random.nextFloat() * 720f - 360f,
                round = Random.nextInt(4) == 0
            )
        }
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (pieces.isEmpty() || progress >= 1f) return
        val d = resources.displayMetrics.density
        val t = progress * 2.6f                 // seconds
        val gravity = 900f * d
        val alpha = if (progress > 0.7f) ((1f - progress) / 0.3f) else 1f

        for (p in pieces) {
            val x = originX + p.vx * t
            val y = originY + p.vy * t + 0.5f * gravity * t * t
            paint.color = p.color
            paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            if (p.round) {
                canvas.drawCircle(x, y, p.size / 2f, paint)
            } else {
                canvas.save()
                canvas.rotate(p.spin * t, x, y)
                canvas.drawRect(RectF(x - p.size / 2f, y - p.size / 4f, x + p.size / 2f, y + p.size / 4f), paint)
                canvas.restore()
            }
        }
    }
}
