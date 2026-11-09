package com.amir.keyboard

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.random.Random

/** بافت دانه‌ی چرم؛ یک بار با کد ساخته می‌شود (بدون هیچ عکسی). */
object Grain {
    const val SIZE = 128
    private var bmp: Bitmap? = null

    fun tile(): Bitmap = bmp ?: build().also { bmp = it }

    private fun build(): Bitmap {
        val b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val r = Random(7)
        val px = IntArray(SIZE * SIZE)
        for (i in px.indices) {
            val a = r.nextInt(0, 22)
            px[i] = if (r.nextInt(4) == 0) Color.argb(a, 255, 255, 255) else Color.argb(a, 0, 0, 0)
        }
        b.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)

        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // حفره‌های ریز چرم
        p.style = Paint.Style.FILL
        repeat(520) {
            p.color = Color.argb(r.nextInt(25, 70), 0, 0, 0)
            c.drawCircle(r.nextFloat() * SIZE, r.nextFloat() * SIZE, r.nextFloat() * 0.9f + 0.5f, p)
        }
        // چین‌های ظریف
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.7f
        repeat(110) {
            p.color = Color.argb(r.nextInt(18, 45), 0, 0, 0)
            val x = r.nextFloat() * SIZE
            val y = r.nextFloat() * SIZE
            val path = Path()
            path.moveTo(x, y)
            path.quadTo(
                x + r.nextFloat() * 10 - 5, y + r.nextFloat() * 10 - 5,
                x + r.nextFloat() * 14 - 7, y + r.nextFloat() * 14 - 7
            )
            c.drawPath(path, p)
        }
        // برق‌های نرم
        p.style = Paint.Style.FILL
        repeat(160) {
            p.color = Color.argb(r.nextInt(10, 30), 255, 255, 255)
            c.drawCircle(r.nextFloat() * SIZE, r.nextFloat() * SIZE, 0.8f, p)
        }
        return b
    }

    fun paint(alpha: Float, seed: Int): Paint {
        val rnd = Random(seed)
        val m = Matrix().apply { setTranslate(rnd.nextInt(SIZE).toFloat(), rnd.nextInt(SIZE).toFloat()) }
        val shader = BitmapShader(tile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        shader.setLocalMatrix(m)
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            this.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        }
    }
}

/** یک کلید چرمی: سایه، لبه، شیب نور، بافت و دوخت دور کلید. */
class LeatherDrawable(
    private val st: KeyStyle,
    private val grain: Float,
    private val radius: Float,
    private val density: Float,
    private val pressed: Boolean,
    seed: Int
) : Drawable() {

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.edge }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressShade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Grain.paint(grain, seed)
    private val stitchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = st.stitch ?: Color.TRANSPARENT
    }
    private val stitchShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(130, 0, 0, 0)
    }

    private val body = RectF()
    private val shadowRect = RectF()
    private val inner = RectF()
    private val stitchRect = RectF()
    private val stitchPath = Path()
    private val innerRadius = maxOf(0f, radius - 1.4f * density)

    override fun onBoundsChange(b: Rect) {
        val d = density
        val bodyTop = b.top + if (pressed) 1.5f * d else 0f
        val bodyBottom = b.bottom - if (pressed) 0.5f * d else 2f * d
        body.set(b.left.toFloat(), bodyTop, b.right.toFloat(), bodyBottom)
        shadowRect.set(b.left.toFloat(), bodyTop + 1.5f * d, b.right.toFloat(), b.bottom.toFloat())

        val inset = 1.4f * d
        inner.set(body.left + inset, body.top + inset, body.right - inset, body.bottom - inset)

        val top = if (pressed) shade(st.pressed, 0.85f) else st.light
        val mid = if (pressed) st.pressed else st.base
        val bot = if (pressed) st.pressed else shade(st.base, 0.9f)
        innerPaint.shader = LinearGradient(
            0f, inner.top, 0f, inner.bottom,
            intArrayOf(top, mid, bot), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
        )
        if (pressed) {
            pressShade.shader = LinearGradient(
                0f, inner.top, 0f, inner.top + 9f * d,
                Color.argb(120, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }

        if (st.stitch != null) {
            val si = 3.2f * d
            stitchRect.set(body.left + si, body.top + si, body.right - si, body.bottom - si)
            stitchPath.reset()
            val sr = maxOf(0f, radius - si)
            stitchPath.addRoundRect(stitchRect, sr, sr, Path.Direction.CW)
            val dash = DashPathEffect(floatArrayOf(3.2f * d, 2.3f * d), 0f)
            stitchPaint.pathEffect = dash
            stitchShadow.pathEffect = dash
            stitchPaint.strokeWidth = 1.1f * d
            stitchShadow.strokeWidth = 1.1f * d
        }
    }

    override fun draw(c: Canvas) {
        if (!pressed) c.drawRoundRect(shadowRect, radius, radius, shadowPaint)
        c.drawRoundRect(body, radius, radius, edgePaint)
        c.drawRoundRect(inner, innerRadius, innerRadius, innerPaint)
        if (grain > 0f) c.drawRoundRect(inner, innerRadius, innerRadius, grainPaint)
        if (pressed) c.drawRoundRect(inner, innerRadius, innerRadius, pressShade)
        if (st.stitch != null) {
            c.save()
            c.translate(0f, 0.9f * density)
            c.drawPath(stitchPath, stitchShadow)
            c.restore()
            c.drawPath(stitchPath, stitchPaint)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** پس‌زمینه‌ی کل کیبورد: شیب رنگ + بافت چرم + خط نازک بالا. */
class LeatherBackground(
    private val top: Int,
    private val bottom: Int,
    grain: Float,
    private val density: Float
) : Drawable() {

    private val paint = Paint()
    private val grainPaint = Grain.paint(grain * 0.9f, 3)
    private val linePaint = Paint().apply { color = mix(top, Color.WHITE, 0.10f) }
    private val hasGrain = grain > 0f

    override fun onBoundsChange(b: Rect) {
        paint.shader = LinearGradient(
            0f, b.top.toFloat(), 0f, b.bottom.toFloat(), top, bottom, Shader.TileMode.CLAMP
        )
    }

    override fun draw(c: Canvas) {
        val b = bounds
        c.drawRect(b, paint)
        if (hasGrain) c.drawRect(b, grainPaint)
        c.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.top + density, linePaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
