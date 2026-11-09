package com.amir.keyboard

import android.content.Context
import android.graphics.Color
import org.json.JSONObject
import java.io.File

/** استایل یک نوع کلید (حروف / عملکردی / اصلی) */
class KeyStyle(
    val base: Int,
    val edge: Int,
    val light: Int,
    val pressed: Int,
    val stitch: Int?,
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
    val grain: Float
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
        return KeyStyle(
            base = base,
            edge = col(o, p + "Edge") ?: shade(base, 0.6f),
            light = col(o, p + "Light") ?: mix(base, Color.WHITE, 0.14f),
            pressed = col(o, p + "Pressed") ?: shade(base, 0.78f),
            stitch = if (stitched) (col(o, p + "Stitch") ?: mix(base, Color.WHITE, 0.4f)) else null,
            text = col(o, p + "Text") ?: Color.parseColor(defText)
        )
    }

    /** اگر فایل خراب باشد Exception می‌دهد. فیلدهای جا‌افتاده مقدار پیش‌فرض (چرم کنیاکی) می‌گیرند. */
    fun parse(id: String, text: String, builtIn: Boolean): KbTheme {
        val o = JSONObject(text)
        val stitched = o.optBoolean("stitched", true)
        val bgTop = col(o, "background") ?: Color.parseColor("#2A211C")
        return KbTheme(
            id = id,
            name = o.optString("name", id),
            builtIn = builtIn,
            bgTop = bgTop,
            bgBottom = col(o, "backgroundBottom") ?: shade(bgTop, 0.7f),
            normal = style(o, "key", "#9A5B2E", "#F0DFC4", stitched),
            special = style(o, "special", "#4A3328", "#F0DFC4", stitched),
            accent = style(o, "accent", "#B5733A", "#FFF3DC", stitched),
            hint = col(o, "hint") ?: Color.parseColor("#A58A68"),
            radius = o.optInt("radius", 9).coerceIn(0, 20),
            grain = o.optDouble("grain", 0.5).toFloat().coerceIn(0f, 1f)
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
