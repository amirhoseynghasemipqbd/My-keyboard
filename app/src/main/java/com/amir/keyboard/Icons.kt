package com.amir.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.TextView

enum class Icon { GLOBE, SHIFT, SHIFT_ON, CAPS, BACKSPACE, ENTER, SPACE, CHECK, CROSS, CALC }

/**
 * یک کلید؛ متن عادی یا آیکون تک‌رنگ که با کد کشیده می‌شود (بدون ایموجی).
 * رنگ آیکون همان رنگ متن کلید است، پس با حروف کاملاً یک‌دست می‌شود.
 * cornerText = حرف کوچک گوشه‌ی کلید (نشانه‌ی کاراکتر نگه‌داشتن انگشت).
 */
class KeyView(
    context: Context,
    private val icon: Icon?,
    private val cornerText: String?,
    private val cornerColor: Int,
    private val iconShadow: Int?
) : TextView(context) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val path = Path()
    private val d = resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cornerText != null) {
            cornerPaint.color = cornerColor
            cornerPaint.textSize = 9f * d
            canvas.drawText(cornerText, width - 6.5f * d, 14f * d, cornerPaint)
        }
        val ic = icon ?: return
        val r = minOf(width, height) * 0.2f
        val cx = width / 2f
        val cy = height / 2f
        p.strokeWidth = 1.8f * d
        if (iconShadow != null) {
            p.color = iconShadow
            drawIcon(canvas, ic, cx, cy + 1.2f * d, r)
        }
        p.color = currentTextColor
        drawIcon(canvas, ic, cx, cy, r)
    }

    private fun drawIcon(c: Canvas, ic: Icon, cx: Float, cy: Float, r: Float) {
        path.reset()
        p.style = Paint.Style.STROKE
        when (ic) {
            Icon.GLOBE -> {
                c.drawCircle(cx, cy, r, p)
                c.drawOval(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy + r, p)
                c.drawLine(cx - r, cy, cx + r, cy, p)
                c.drawLine(cx - r * 0.85f, cy - r * 0.5f, cx + r * 0.85f, cy - r * 0.5f, p)
                c.drawLine(cx - r * 0.85f, cy + r * 0.5f, cx + r * 0.85f, cy + r * 0.5f, p)
            }
            Icon.SHIFT, Icon.SHIFT_ON, Icon.CAPS -> {
                val up = if (ic == Icon.CAPS) -0.2f * r else 0f
                path.moveTo(cx, cy - r + up)
                path.lineTo(cx + r, cy + up)
                path.lineTo(cx + 0.45f * r, cy + up)
                path.lineTo(cx + 0.45f * r, cy + 0.85f * r + up)
                path.lineTo(cx - 0.45f * r, cy + 0.85f * r + up)
                path.lineTo(cx - 0.45f * r, cy + up)
                path.lineTo(cx - r, cy + up)
                path.close()
                if (ic != Icon.SHIFT) p.style = Paint.Style.FILL_AND_STROKE
                c.drawPath(path, p)
                if (ic == Icon.CAPS) {
                    p.style = Paint.Style.STROKE
                    c.drawLine(cx - 0.45f * r, cy + 1.2f * r, cx + 0.45f * r, cy + 1.2f * r, p)
                }
            }
            Icon.BACKSPACE -> {
                path.moveTo(cx - 1.15f * r, cy)
                path.lineTo(cx - 0.5f * r, cy - 0.75f * r)
                path.lineTo(cx + 1.1f * r, cy - 0.75f * r)
                path.lineTo(cx + 1.1f * r, cy + 0.75f * r)
                path.lineTo(cx - 0.5f * r, cy + 0.75f * r)
                path.close()
                c.drawPath(path, p)
                c.drawLine(cx + 0.05f * r, cy - 0.3f * r, cx + 0.65f * r, cy + 0.3f * r, p)
                c.drawLine(cx + 0.65f * r, cy - 0.3f * r, cx + 0.05f * r, cy + 0.3f * r, p)
            }
            Icon.ENTER -> {
                path.moveTo(cx + 0.9f * r, cy - 0.7f * r)
                path.lineTo(cx + 0.9f * r, cy + 0.2f * r)
                path.lineTo(cx - 0.8f * r, cy + 0.2f * r)
                c.drawPath(path, p)
                c.drawLine(cx - 0.8f * r, cy + 0.2f * r, cx - 0.2f * r, cy - 0.4f * r, p)
                c.drawLine(cx - 0.8f * r, cy + 0.2f * r, cx - 0.2f * r, cy + 0.8f * r, p)
            }
            Icon.SPACE -> {
                path.moveTo(cx - r, cy - 0.2f * r)
                path.lineTo(cx - r, cy + 0.4f * r)
                path.lineTo(cx + r, cy + 0.4f * r)
                path.lineTo(cx + r, cy - 0.2f * r)
                c.drawPath(path, p)
            }
            Icon.CHECK -> {
                path.moveTo(cx - 0.9f * r, cy + 0.05f * r)
                path.lineTo(cx - 0.3f * r, cy + 0.65f * r)
                path.lineTo(cx + 0.95f * r, cy - 0.7f * r)
                c.drawPath(path, p)
            }
            Icon.CROSS -> {
                c.drawLine(cx - 0.7f * r, cy - 0.7f * r, cx + 0.7f * r, cy + 0.7f * r, p)
                c.drawLine(cx + 0.7f * r, cy - 0.7f * r, cx - 0.7f * r, cy + 0.7f * r, p)
            }
            Icon.CALC -> {
                c.drawRoundRect(cx - 0.8f * r, cy - 1.05f * r, cx + 0.8f * r, cy + 1.05f * r, 0.25f * r, 0.25f * r, p)
                c.drawLine(cx - 0.5f * r, cy - 0.55f * r, cx + 0.5f * r, cy - 0.55f * r, p)
                p.style = Paint.Style.FILL
                val dr = 0.14f * r
                c.drawCircle(cx - 0.33f * r, cy + 0.05f * r, dr, p)
                c.drawCircle(cx + 0.33f * r, cy + 0.05f * r, dr, p)
                c.drawCircle(cx - 0.33f * r, cy + 0.62f * r, dr, p)
                c.drawCircle(cx + 0.33f * r, cy + 0.62f * r, dr, p)
                p.style = Paint.Style.STROKE
            }
        }
    }
}
