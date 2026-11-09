package com.amir.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.random.Random

/**
 * بافت واقعی چرم: یک تکه‌ی خاکستری (assets/textures/grain.png) که از عکس مرجع
 * برداشته شده؛ اینجا با هر رنگی که تم بخواهد «رنگ‌آمیزی» و کش می‌شود.
 */
object Grain {
    private var gray: IntArray? = null
    private var gw = 128
    private var gh = 128
    private val cache = HashMap<Long, Bitmap>()

    fun load(ctx: Context) {
        if (gray != null) return
        try {
            val bmp = ctx.assets.open("textures/grain.png").use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalStateException("no grain")
            gw = bmp.width
            gh = bmp.height
            val px = IntArray(gw * gh)
            bmp.getPixels(px, 0, gw, 0, 0, gw, gh)
            gray = IntArray(px.size) { px[it] and 0xFF }
        } catch (e: Exception) {
            fallback()
        }
    }

    private fun fallback() {
        gw = 128; gh = 128
        val r = Random(7)
        gray = IntArray(gw * gh) { 128 + r.nextInt(-40, 40) }
    }

    private fun tinted(color: Int, strength: Float): Bitmap {
        if (gray == null) fallback()
        val g = gray!!
        val key = (color.toLong() and 0xFFFFFFFFL) or ((strength * 100).toLong() shl 32)
        cache[key]?.let { return it }
        val r0 = Color.red(color)
        val g0 = Color.green(color)
        val b0 = Color.blue(color)
        val out = IntArray(g.size)
        for (i in out.indices) {
            val f = 1f + strength * ((g[i] - 128) / 128f)
            out[i] = Color.argb(
                255,
                (r0 * f).toInt().coerceIn(0, 255),
                (g0 * f).toInt().coerceIn(0, 255),
                (b0 * f).toInt().coerceIn(0, 255)
            )
        }
        val bmp = Bitmap.createBitmap(out, gw, gh, Bitmap.Config.ARGB_8888)
        cache[key] = bmp
        return bmp
    }

    fun shader(color: Int, strength: Float, scale: Float, seed: Int): BitmapShader {
        val sh = BitmapShader(tinted(color, strength), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val rnd = Random(seed)
        val m = Matrix()
        m.setScale(scale, scale)
        m.postTranslate(rnd.nextInt(gw) * scale, rnd.nextInt(gh) * scale)
        sh.setLocalMatrix(m)
        return sh
    }
}

/**
 * یک کلید چرمی شبیه عکس مرجع: لبه‌ی ضخیم برجسته، پنل مات با بافت واقعی،
 * سایه‌ی داخلی زیر لبه، و دوخت تابیده‌ی دور کلید.
 */
class LeatherDrawable(
    private val st: KeyStyle,
    private val grain: Float,
    private val grainScale: Float,
    rimWidthDp: Float,
    private val radius: Float,
    private val density: Float,
    private val pressed: Boolean,
    private val seed: Int
) : Drawable() {

    private val d = density
    private val rimW = rimWidthDp * density

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(115, 0, 0, 0) }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.rimDark }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.9f * density
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val topShade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leftShade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val botLight = Paint(Paint.ANTI_ALIAS_FLAG)
    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.9f * density
        color = Color.argb(120, 0, 0, 0)
    }
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(50, 0, 0, 0) }

    private val threadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.5f * density
        color = st.stitch ?: Color.TRANSPARENT
    }
    private val threadShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.5f * density
        color = Color.argb(150, 0, 0, 0)
    }
    private val twistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 0.6f * density
        color = Color.argb(95, 0, 0, 0)
    }

    private val body = RectF()
    private val shadowRect = RectF()
    private val rimInner = RectF()
    private val hiRect = RectF()
    private val panel = RectF()
    private val stitchRect = RectF()
    private val path = Path()
    private var panelR = 0f
    private var dashPts = FloatArray(0)
    private var twistPts = FloatArray(0)

    override fun onBoundsChange(b: Rect) {
        val lift = if (pressed) 1f * d else 0f
        val bodyTop = b.top + lift
        val bodyBottom = b.bottom - (if (pressed) 0f else 1.8f * d)
        body.set(b.left.toFloat(), bodyTop, b.right.toFloat(), bodyBottom)
        shadowRect.set(b.left.toFloat(), bodyTop + 1.4f * d, b.right.toFloat(), b.bottom.toFloat())

        val hasRim = rimW > 0f
        if (hasRim) {
            rimInner.set(body.left + 0.8f * d, body.top + 0.8f * d, body.right - 0.8f * d, body.bottom - 0.8f * d)
            hiRect.set(body.left + 1.2f * d, body.top + 1.2f * d, body.right - 1.2f * d, body.bottom - 1.2f * d)
            rimPaint.shader = LinearGradient(
                body.left, body.top, body.right, body.bottom,
                intArrayOf(st.rimLight, st.rim, st.rimDark), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
            hiPaint.shader = LinearGradient(
                body.left, body.top, body.right, body.bottom,
                Color.argb(110, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
            )
            panel.set(body.left + rimW, body.top + rimW, body.right - rimW, body.bottom - rimW)
            panelR = max(0f, radius - rimW * 0.6f)
        } else {
            panel.set(body)
            panelR = radius
        }

        // پنل: بافت واقعی چرم (یا رنگ ساده اگر grain = 0)
        val pc = if (pressed) st.pressed else st.base
        if (grain > 0f) {
            panelPaint.shader = Grain.shader(pc, grain, grainScale * density / 2.6f, seed)
        } else {
            panelPaint.shader = null
            panelPaint.color = pc
        }

        if (hasRim) {
            topShade.shader = LinearGradient(
                0f, panel.top, 0f, panel.top + 8f * d,
                Color.argb(95, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            leftShade.shader = LinearGradient(
                panel.left, 0f, panel.left + 6f * d, 0f,
                Color.argb(60, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            botLight.shader = LinearGradient(
                0f, panel.bottom - 6f * d, 0f, panel.bottom,
                Color.TRANSPARENT, Color.argb(30, 255, 255, 255), Shader.TileMode.CLAMP
            )
        }

        // دوخت تابیده: خط‌چین‌های ریز روی مسیر گرد، با یک «تاب» مورب روی هر کدام
        if (st.stitch != null && hasRim) {
            val si = rimW + 2.4f * d
            stitchRect.set(body.left + si, body.top + si, body.right - si, body.bottom - si)
            val sr = max(1f * d, radius - si * 0.8f)
            path.reset()
            path.addRoundRect(stitchRect, sr, sr, Path.Direction.CW)
            val pm = PathMeasure(path, false)
            val len = pm.length
            val count = max(1, (len / (3.6f * d + 2.2f * d)).toInt())
            val step = len / count
            val dash = 3.6f * d
            val pos = FloatArray(2)
            val tan = FloatArray(2)
            dashPts = FloatArray(count * 4)
            twistPts = FloatArray(count * 4)
            for (i in 0 until count) {
                pm.getPosTan(i * step + step / 2f, pos, tan)
                val hx = tan[0] * dash / 2f
                val hy = tan[1] * dash / 2f
                dashPts[i * 4] = pos[0] - hx
                dashPts[i * 4 + 1] = pos[1] - hy
                dashPts[i * 4 + 2] = pos[0] + hx
                dashPts[i * 4 + 3] = pos[1] + hy
                val nx = -tan[1]
                val ny = tan[0]
                val t = 0.75f * d
                val a = 0.6f * t
                twistPts[i * 4] = pos[0] - nx * t - tan[0] * a
                twistPts[i * 4 + 1] = pos[1] - ny * t - tan[1] * a
                twistPts[i * 4 + 2] = pos[0] + nx * t + tan[0] * a
                twistPts[i * 4 + 3] = pos[1] + ny * t + tan[1] * a
            }
        } else {
            dashPts = FloatArray(0)
            twistPts = FloatArray(0)
        }
    }

    override fun draw(c: Canvas) {
        val hasRim = rimW > 0f
        if (!pressed) c.drawRoundRect(shadowRect, radius, radius, shadowPaint)
        if (hasRim) {
            c.drawRoundRect(body, radius, radius, outlinePaint)
            c.drawRoundRect(rimInner, radius - 0.8f * d, radius - 0.8f * d, rimPaint)
            c.drawRoundRect(hiRect, radius - 1.2f * d, radius - 1.2f * d, hiPaint)
        }
        c.drawRoundRect(panel, panelR, panelR, panelPaint)
        if (hasRim) {
            c.drawRoundRect(panel, panelR, panelR, topShade)
            c.drawRoundRect(panel, panelR, panelR, leftShade)
            c.drawRoundRect(panel, panelR, panelR, botLight)
            c.drawRoundRect(panel, panelR, panelR, groovePaint)
        }
        if (pressed) c.drawRoundRect(panel, panelR, panelR, pressPaint)
        if (dashPts.isNotEmpty()) {
            c.save()
            c.translate(0f, 0.8f * d)
            c.drawLines(dashPts, threadShadow)
            c.restore()
            c.drawLines(dashPts, threadPaint)
            c.drawLines(twistPts, twistPaint)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** پس‌زمینه‌ی کل کیبورد: چرم تیره‌ی بافت‌دار، کمی تیره‌تر به سمت پایین، با خط نازک بالا. */
class LeatherBackground(
    private val top: Int,
    private val bottom: Int,
    private val grain: Float,
    private val grainScale: Float,
    private val density: Float
) : Drawable() {

    private val texPaint = Paint().apply {
        if (grain > 0f) shader = Grain.shader(top, grain * 0.8f, grainScale * density / 2.6f, 3) else color = top
    }
    private val darkPaint = Paint()
    private val linePaint = Paint().apply { color = mix(top, Color.WHITE, 0.10f) }

    override fun onBoundsChange(b: Rect) {
        darkPaint.shader = LinearGradient(
            0f, b.top.toFloat(), 0f, b.bottom.toFloat(),
            Color.argb(0, Color.red(bottom), Color.green(bottom), Color.blue(bottom)),
            Color.argb(215, Color.red(bottom), Color.green(bottom), Color.blue(bottom)),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(c: Canvas) {
        val b = bounds
        c.drawRect(b, texPaint)
        c.drawRect(b, darkPaint)
        c.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.top + density, linePaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
