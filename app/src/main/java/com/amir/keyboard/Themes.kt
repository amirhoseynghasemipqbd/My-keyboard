package com.amir.keyboard

import android.content.Context
import android.graphics.Color
import org.json.JSONObject
import java.io.File

/** استایل یک نوع کلید (حروف / کلیدهای خاص) */
class KeyStyle(
    val base: Int,        // رنگ پنل چرم (وسط کلید)
    val rim: Int,         // رنگ لبه‌ی برجسته
    val rimLight: Int,    // نور لبه (بالا-چپ)
    val rimDark: Int,     // سایه‌ی لبه / خط دور کلید
    val pressed: Int,     // رنگ پنل هنگام فشردن
    val stitch: Int?,     // رنگ نخ دوخت (null = بدون دوخت)
    val text: Int
)

class KbTheme(
    val id: String,
    val name: String,
    val builtIn: Boolean,
    val bgTop: Int,
    val bgBottom: Int,
    val normal: KeyStyle,
    val special: KeyStyle,
    val accent: KeyStyle,
    val hint: Int,
    val radius: Int,
    val grain: Float,       // شدت بافت چرم (0 = صاف)
    val grainScale: Float,  // اندازه‌ی بافت
    val rimWidth: Float     // ضخامت لبه (dp)، 0 = بدون لبه
)

fun shade(c: Int, f: Float): Int = Color.argb(
    Color.alpha(c),
    (Color.red(c) * f).toInt().coerceIn(0, 255),
    (Color.green(c) * f).toInt().coerceIn(0, 255),
    (Color.blue(c) * f).toInt().coerceIn(0, 255)
)

fun mix(c: Int, other: Int, t: Float): Int = Color.argb(
    Color.alpha(c),
    (Color.red(c) + (Color.red(other) - Color.red(c)) * t).toInt().coerceIn(0, 255),
    (Color.green(c) + (Color.green(other) - Color.green(c)) * t).toInt().coerceIn(0, 255),
    (Color.blue(c) + (Color.blue(other) - Color.blue(c)) * t).toInt().coerceIn(0, 255)
)

object ThemeLoader {
    private fun col(o: JSONObject, k: String): Int? {
        val s = o.optString(k, "")
        if (s.isEmpty()) return null
        return try { Color.parseColor(s) } catch (e: IllegalArgumentException) { null }
    }

    private fun style(o: JSONObject, p: String, defBase: String, defText: String, stitched: Boolean): KeyStyle {
        val base = col(o, p) ?: Color.parseColor(defBase)
        val rim = col(o, p + "Rim") ?: mix(base, Color.WHITE, 0.16f)
        return KeyStyle(
            base = base,
            rim = rim,
            rimLight = col(o, p + "RimLight") ?: mix(rim, Color.WHITE, 0.25f),
            rimDark = col(o, p + "Edge") ?: shade(rim, 0.5f),
            pressed = col(o, p + "Pressed") ?: shade(base, 0.78f),
            stitch = if (stitched) (col(o, p + "Stitch") ?: mix(rim, Color.WHITE, 0.35f)) else null,
            text = col(o, p + "Text") ?: Color.parseColor(defText)
        )
    }

    /** اگر فایل خراب باشد Exception می‌دهد. فیلدهای جا‌افتاده مقدار پیش‌فرض (چرم کنیاکی) می‌گیرند. */
    fun parse(id: String, text: String, builtIn: Boolean): KbTheme {
        val o = JSONObject(text)
        val stitched = o.optBoolean("stitched", true)
        val bgTop = col(o, "background") ?: Color.parseColor("#2A1C13")
        return KbTheme(
            id = id,
            name = o.optString("name", id),
            builtIn = builtIn,
            bgTop = bgTop,
            bgBottom = col(o, "backgroundBottom") ?: shade(bgTop, 0.7f),
            normal = style(o, "key", "#74401D", "#E6D3B2", stitched),
            special = style(o, "special", "#3E2819", "#E6D3B2", stitched),
            accent = style(o, "accent", "#3E2819", "#E6D3B2", stitched),
            hint = col(o, "hint") ?: Color.parseColor("#9C8466"),
            radius = o.optInt("radius", 10).coerceIn(0, 20),
            grain = o.optDouble("grain", 0.9).toFloat().coerceIn(0f, 1.5f),
            grainScale = o.optDouble("grainScale", 0.38).toFloat().coerceIn(0.1f, 2f),
            rimWidth = o.optDouble("rimWidth", 3.2).toFloat().coerceIn(0f, 6f)
        )
    }

    fun fallback(): KbTheme = parse("fallback", "{}", true)
}

object ThemeStore {
    const val KEY = "theme"
    const val DEFAULT = "leather_cognac"

    fun prefs(ctx: Context) = ctx.getSharedPreferences("kb", Context.MODE_PRIVATE)

    private fun dir(ctx: Context) = File(ctx.filesDir, "themes")

    fun all(ctx: Context): List<KbTheme> {
        val list = mutableListOf<KbTheme>()
        try {
            ctx.assets.list("themes")?.sorted()?.forEach { f ->
                if (f.endsWith(".json")) {
                    val txt = ctx.assets.open("themes/$f").bufferedReader().use { it.readText() }
                    try { list.add(ThemeLoader.parse(f.removeSuffix(".json"), txt, true)) } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) { }
        dir(ctx).listFiles()?.sortedBy { it.name }?.forEach { f ->
            if (f.name.endsWith(".json")) {
                try { list.add(ThemeLoader.parse(f.name.removeSuffix(".json"), f.readText(), false)) } catch (e: Exception) { }
            }
        }
        return list
    }

    fun current(ctx: Context): KbTheme {
        val id = prefs(ctx).getString(KEY, DEFAULT)
        val l = all(ctx)
        return l.firstOrNull { it.id == id } ?: l.firstOrNull() ?: ThemeLoader.fallback()
    }

    fun select(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY, id).apply()
    }

    /** متن JSON را بررسی و ذخیره می‌کند. اگر معتبر نباشد Exception می‌دهد. */
    fun import(ctx: Context, text: String): KbTheme {
        val id = "custom_" + System.currentTimeMillis()
        val t = ThemeLoader.parse(id, text, false)
        dir(ctx).mkdirs()
        File(dir(ctx), "$id.json").writeText(text)
        return t
    }

    fun delete(ctx: Context, id: String) {
        File(dir(ctx), "$id.json").delete()
    }
}
