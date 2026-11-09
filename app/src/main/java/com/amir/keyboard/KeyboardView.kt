package com.amir.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

enum class Lang { EN, FA }
enum class Shift { OFF, ON, CAPS }
enum class Page { LETTERS, NUMBERS, SYMBOLS }

/** کیبورد چندزبانه (فارسی/انگلیسی)؛ ظاهرش کاملاً از روی KbTheme ساخته می‌شود. */
class KeyboardView(context: Context, private var theme: KbTheme) : LinearLayout(context) {

    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onPickIme()
        fun onLangChanged(lang: Lang)
    }

    private enum class Kind { NORMAL, SPECIAL, ACCENT }

    var listener: Listener? = null

    private var lang = Lang.EN
    private var page = Page.LETTERS
    private var shift = Shift.OFF
    private var lastShiftTap = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density

    // ---------- چیدمان‌ها (چپ به راست) ----------
    private val enRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val faRows = listOf("ضصقفغعهخحجچ", "شسیبلاتنمکگ", "ظطژزرذدپوث")
    private val enSym = listOf("@#\$%&-+()/^", "*\"':;!?_=,`", "✓✗~|<>[]{}")
    private val faSym = listOf("@#\$%&-+()/«", "*\"':؛!؟_=،`", "✓✗|<>[]{}»")

    // با نگه‌داشتن انگشت روی این حروف، حرف کوچک گوشه‌ی کلید نوشته می‌شود
    private val faAlt = mapOf("ا" to "آ", "ی" to "ئ", "و" to "ؤ", "ه" to "ء")

    private val repeatDelete = object : Runnable {
        override fun run() {
            listener?.onDelete()
            handler.postDelayed(this, 50)
        }
    }

    init {
        orientation = VERTICAL
        layoutDirection = LAYOUT_DIRECTION_LTR
        setPadding(dp(4), dp(8), dp(4), dp(6))
        setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            val bottom = insets.systemWindowInsetBottom
            setPadding(dp(4), dp(8), dp(4), dp(6) + bottom)
            insets
        }
        applyTheme()
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(repeatDelete)
        super.onDetachedFromWindow()
    }

    // ---------- API برای سرویس ----------
    fun setTheme(t: KbTheme) {
        theme = t
        applyTheme()
        render()
    }

    fun setLang(l: Lang) {
        lang = l; page = Page.LETTERS; shift = Shift.OFF; render()
    }

    fun setPage(p: Page) {
        page = p; render()
    }

    fun setShift(s: Shift) {
        if (shift != s) { shift = s; render() }
    }

    // ---------- ساخت UI ----------
    private fun dp(v: Int) = (v * density).toInt()

    private fun applyTheme() {
        background = LeatherBackground(theme.bgTop, theme.bgBottom, theme.grain, density)
    }

    private fun render() {
        removeAllViews()
        when (page) {
            Page.NUMBERS -> renderNumbers()
            Page.SYMBOLS -> {
                val rows = if (lang == Lang.FA) faSym else enSym
                addView(letterRow(rows[0], false))
                addView(letterRow(rows[1], false))
                addView(thirdRow(rows[2], false))
                addView(bottomRow())
            }
            Page.LETTERS -> {
                val rows = if (lang == Lang.FA) faRows else enRows
                addView(letterRow(rows[0], false))
                addView(letterRow(rows[1], lang == Lang.EN))
                addView(thirdRow(rows[2], lang == Lang.EN))
                addView(bottomRow())
            }
        }
    }

    private fun newRow() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(54))
    }

    private fun spacer(w: Float) = View(context).apply {
        layoutParams = LayoutParams(0, 1, w)
    }

    private fun letterRow(chars: String, pad: Boolean): LinearLayout = newRow().apply {
        if (pad) addView(spacer(0.5f))
        chars.forEach {
            val s = it.toString()
            val alt = if (page == Page.LETTERS && lang == Lang.FA) faAlt[s] else null
            addView(charKey(s, alt))
        }
        if (pad) addView(spacer(0.5f))
    }

    private fun thirdRow(chars: String, withShift: Boolean): LinearLayout = newRow().apply {
        if (withShift) addView(shiftKey())
        chars.forEach { addView(charKey(it.toString())) }
        addView(deleteKey(1.5f))
    }

    private fun bottomRow(): LinearLayout = newRow().apply {
        // نماد / برگشت به حروف
        val first = when {
            page == Page.LETTERS -> "@#"
            lang == Lang.FA -> "ابج"
            else -> "ABC"
        }
        addView(makeKey(first, 1.4f, Kind.SPECIAL).also {
            it.setOnClickListener { v ->
                tap(v)
                page = if (page == Page.LETTERS) Page.SYMBOLS else Page.LETTERS
                render()
            }
        })

        // اعداد
        addView(makeKey("123", 1.4f, Kind.SPECIAL).also {
            it.setOnClickListener { v -> tap(v); page = Page.NUMBERS; render() }
        })

        // زبان: کلیک = تغییر زبان، نگه‌داشتن = انتخاب کیبورد سیستم
        addView(makeKey("", 1.4f, Kind.SPECIAL, Icon.GLOBE).also {
            it.setOnClickListener { v ->
                tap(v)
                lang = if (lang == Lang.EN) Lang.FA else Lang.EN
                page = Page.LETTERS; shift = Shift.OFF
                listener?.onLangChanged(lang)
                render()
            }
            it.setOnLongClickListener { listener?.onPickIme(); true }
        })

        // فاصله (در فارسی نگه‌داشتن = نیم‌فاصله)
        addView(makeKey(if (lang == Lang.FA) "فارسی" else "English", 3.6f, Kind.SPECIAL).also {
            it.setTextColor(theme.hint)
            it.setOnClickListener { v -> tap(v); listener?.onText(" ") }
            if (lang == Lang.FA) {
                it.setOnLongClickListener { v ->
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    listener?.onText("\u200C")
                    true
                }
            }
        })

        // نقطه (نگه‌داشتن = ویرگول)
        addView(charKey(".", if (lang == Lang.FA) "،" else ","))

        addView(enterKey(1.6f))
    }

    /** صفحه‌ی اعداد: کیپد بزرگ */
    private fun renderNumbers() {
        val back = if (lang == Lang.FA) "ابج" else "ABC"

        addView(newRow().apply {
            addView(charKey("+", null, Kind.SPECIAL))
            addView(charKey("1")); addView(charKey("2")); addView(charKey("3"))
            addView(deleteKey(1f))
        })
        addView(newRow().apply {
            addView(charKey("*", null, Kind.SPECIAL))
            addView(charKey("4")); addView(charKey("5")); addView(charKey("6"))
            addView(charKey("-", null, Kind.SPECIAL))
        })
        addView(newRow().apply {
            addView(charKey("#", null, Kind.SPECIAL))
            addView(charKey("7")); addView(charKey("8")); addView(charKey("9"))
            addView(makeKey("", 1f, Kind.SPECIAL, Icon.SPACE).also {
                it.setOnClickListener { v -> tap(v); listener?.onText(" ") }
            })
        })
        addView(newRow().apply {
            addView(makeKey(back, 1f, Kind.SPECIAL).also {
                it.setOnClickListener { v -> tap(v); page = Page.LETTERS; render() }
            })
            addView(charKey(","))
            addView(charKey("0"))
            addView(charKey("."))
            addView(enterKey(1f))
        })
    }

    private fun charKey(c: String, alt: String? = null, kind: Kind = Kind.NORMAL): TextView {
        val upper = lang == Lang.EN && page == Page.LETTERS && shift != Shift.OFF
        val shown = if (upper) c.uppercase() else c
        val icon = when (c) {
            "✓" -> Icon.CHECK
            "✗" -> Icon.CROSS
            else -> null
        }
        return makeKey(if (icon != null) "" else shown, 1f, kind, icon, alt).also {
            it.setOnClickListener { v ->
                tap(v)
                listener?.onText(shown)
                if (shift == Shift.ON && page == Page.LETTERS) { shift = Shift.OFF; render() }
            }
            if (alt != null) {
                it.setOnLongClickListener { v ->
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    listener?.onText(alt)
                    true
                }
            }
        }
    }

    private fun enterKey(weight: Float): TextView =
        makeKey("", weight, Kind.ACCENT, Icon.ENTER).also {
            it.setOnClickListener { v -> tap(v); listener?.onEnter() }
        }

    private fun shiftKey(): TextView {
        val icon = when (shift) { Shift.OFF -> Icon.SHIFT; Shift.ON -> Icon.SHIFT_ON; Shift.CAPS -> Icon.CAPS }
        return makeKey("", 1.5f, if (shift != Shift.OFF) Kind.ACCENT else Kind.SPECIAL, icon).also {
            it.setOnClickListener { v ->
                tap(v)
                val now = System.currentTimeMillis()
                shift = when {
                    shift == Shift.CAPS -> Shift.OFF
                    shift == Shift.ON && now - lastShiftTap < 350 -> Shift.CAPS
                    shift == Shift.OFF -> Shift.ON
                    else -> Shift.OFF
                }
                lastShiftTap = now
                render()
            }
        }
    }

    private fun deleteKey(weight: Float): TextView = makeKey("", weight, Kind.SPECIAL, Icon.BACKSPACE).also {
        it.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    tap(v)
                    listener?.onDelete()
                    handler.postDelayed(repeatDelete, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeatDelete)
                }
            }
            true
        }
    }

    private fun tap(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun keyBg(st: KeyStyle, seed: Int): Drawable {
        val r = dp(theme.radius).toFloat()
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                LeatherDrawable(st, theme.grain, r, density, true, seed)
            )
            addState(
                intArrayOf(),
                LeatherDrawable(st, theme.grain, r, density, false, seed)
            )
        }
    }

    private fun makeKey(
        label: String,
        weight: Float,
        kind: Kind,
        icon: Icon? = null,
        corner: String? = null
    ): TextView {
        val st = when (kind) {
            Kind.NORMAL -> theme.normal
            Kind.SPECIAL -> theme.special
            Kind.ACCENT -> theme.accent
        }
        val shadow = if (theme.grain > 0f) Color.argb(170, 0, 0, 0) else null
        val cornerCol = Color.argb(150, Color.red(st.text), Color.green(st.text), Color.blue(st.text))
        val tv: TextView = KeyView(context, icon, corner, cornerCol, shadow)
        tv.apply {
            text = label
            gravity = Gravity.CENTER
            isClickable = true
            textSize = if (label.length > 1) 14f else 21f
            setTextColor(st.text)
            // سایه‌ی زیر حروف = حس حروف فرورفته/طلاکوب روی چرم
            if (theme.grain > 0f) setShadowLayer(1.2f * density, 0f, 1.2f * density, Color.argb(170, 0, 0, 0))
            background = keyBg(st, label.hashCode() + (weight * 10).toInt() + (icon?.ordinal ?: 0))
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }
        }
        return tv
    }
}
