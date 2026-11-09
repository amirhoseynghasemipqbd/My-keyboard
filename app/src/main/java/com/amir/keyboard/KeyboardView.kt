package com.amir.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
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
        fun onCalcChanged(on: Boolean)
    }

    // دو رنگ بیشتر نداریم: کلیدهای نوشتن (چرم کنیاکی) و کلیدهای خاص (چرم تیره)
    private enum class Kind { NORMAL, SPECIAL }

    var listener: Listener? = null

    private var lang = Lang.EN
    private var page = Page.LETTERS
    private var shift = Shift.OFF
    private var calcOn = false
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

    // نمادهای ستون کشویی صفحه‌ی اعداد
    private val sideSymbols = listOf("+", "-", "*", "/", "(", ")")

    private val repeatDelete = object : Runnable {
        override fun run() {
            listener?.onDelete()
            handler.postDelayed(this, 50)
        }
    }

    init {
        Grain.load(context)
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

    fun setCalc(on: Boolean) {
        calcOn = on
        if (page == Page.NUMBERS) render()
    }

    // ---------- ساخت UI ----------
    private fun dp(v: Int) = (v * density).toInt()

    private fun applyTheme() {
        background = LeatherBackground(theme.bgTop, theme.bgBottom, theme.grain, theme.grainScale, density)
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

    private fun newRow(h: Int = dp(52)) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, h)
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

    /** ردیف پایین حروف/نمادها: نماد · اعداد · زبان · فاصله · نقطه · Enter (همه هم‌اندازه‌ی حروف) */
    private fun bottomRow(): LinearLayout = newRow().apply {
        val first = when {
            page == Page.LETTERS -> "@#"
            lang == Lang.FA -> "ابج"
            else -> "ABC"
        }
        addView(makeKey(first, 1f, Kind.SPECIAL).also {
            it.setOnClickListener { v ->
                tap(v)
                page = if (page == Page.LETTERS) Page.SYMBOLS else Page.LETTERS
                render()
            }
        })

        addView(makeKey("123", 1f, Kind.SPECIAL).also {
            it.setOnClickListener { v -> tap(v); page = Page.NUMBERS; render() }
        })

        // زبان: کلیک = تغییر زبان، نگه‌داشتن = انتخاب کیبورد سیستم
        addView(makeKey("", 1f, Kind.SPECIAL, Icon.GLOBE).also {
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
        addView(makeKey(if (lang == Lang.FA) "فارسی" else "English", 4.4f, Kind.SPECIAL).also {
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

    /** صفحه‌ی اعداد: کیپد شبیه گوگل، با ستون کشویی نمادها در سمت چپ و ماشین‌حساب */
    private fun renderNumbers() {
        val rowH = dp(48)
        addView(calcBar())

        val block = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, rowH * 3)
        }
        block.addView(symbolScroller(rowH))

        val grid = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 3f)
        }
        listOf("123", "456", "789").forEach { digits ->
            val r = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            }
            digits.forEach { r.addView(charKey(it.toString())) }
            grid.addView(r)
        }
        block.addView(grid)

        val spaceKey = makeKey("", 1f, Kind.SPECIAL, Icon.SPACE).also {
            it.setOnClickListener { v -> tap(v); listener?.onText(" ") }
        }
        block.addView(verticalColumn(1f, charKey("%"), spaceKey, deleteKey(1f)))
        addView(block)

        // ردیف آخر: ABC , !?# 0 = . Enter
        val back = if (lang == Lang.FA) "ابج" else "ABC"
        addView(newRow(rowH).apply {
            addView(makeKey(back, 1.3f, Kind.SPECIAL).also {
                it.setOnClickListener { v -> tap(v); page = Page.LETTERS; render() }
            })
            addView(charKey(",").also { it.layoutParams = weightParams(0.8f) })
            addView(makeKey("!?#", 1f, Kind.SPECIAL).also {
                it.setOnClickListener { v -> tap(v); page = Page.SYMBOLS; render() }
            })
            addView(charKey("0").also { it.layoutParams = weightParams(1.5f) })
            addView(charKey("=").also { it.layoutParams = weightParams(0.9f) })
            addView(charKey(".").also { it.layoutParams = weightParams(0.8f) })
            addView(enterKey(1.3f))
        })
    }

    private fun weightParams(w: Float) = LayoutParams(0, LayoutParams.MATCH_PARENT, w).apply {
        setMargins(dp(2), dp(3), dp(2), dp(3))
    }

    private fun verticalColumn(weight: Float, vararg keys: TextView): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
        keys.forEach { k ->
            k.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }
            addView(k)
        }
    }

    /** نوار بالای صفحه‌ی اعداد: کلید ماشین‌حساب (روشن/خاموش) */
    private fun calcBar(): LinearLayout = newRow(dp(36)).apply {
        addView(makeKey("", 1.2f, if (calcOn) Kind.NORMAL else Kind.SPECIAL, Icon.CALC).also {
            it.setOnClickListener { v ->
                tap(v)
                calcOn = !calcOn
                listener?.onCalcChanged(calcOn)
                render()
            }
        })
        addView(TextView(context).apply {
            text = if (calcOn) "ماشین‌حساب روشن: بعد از = جواب می‌آید" else "ماشین‌حساب خاموش"
            textSize = 12f
            setTextColor(theme.hint)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 3.8f)
        })
    }

    /** ستون بلند و کشویی نمادها: + - * / ( ) ؛ با کشیدن انگشت بالا/پایین می‌رود */
    private fun symbolScroller(rowH: Int): View {
        val st = theme.normal
        val pad = dp(4)
        val inner = rowH * 3 - dp(6) - pad * 2
        val itemH = (inner / 4.5f).toInt()
        val sv = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            background = keyBg(st, 77)
            setPadding(pad, pad, pad, pad)
            clipToPadding = true
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }
        }
        val col = LinearLayout(context).apply { orientation = VERTICAL }
        sideSymbols.forEach { s ->
            col.addView(TextView(context).apply {
                text = s
                gravity = Gravity.CENTER
                textSize = 22f
                isClickable = true
                setTextColor(st.text)
                if (theme.grain > 0f) setShadowLayer(1.2f * density, 0f, 1.2f * density, Color.argb(170, 0, 0, 0))
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(Color.argb(70, 0, 0, 0)))
                    addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
                }
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, itemH)
                setOnClickListener { v -> tap(v); listener?.onText(s) }
            })
        }
        sv.addView(col)
        return sv
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
        makeKey("", weight, Kind.SPECIAL, Icon.ENTER).also {
            it.setOnClickListener { v -> tap(v); listener?.onEnter() }
        }

    private fun shiftKey(): TextView {
        val icon = when (shift) { Shift.OFF -> Icon.SHIFT; Shift.ON -> Icon.SHIFT_ON; Shift.CAPS -> Icon.CAPS }
        // شیفت فعال = رنگ چرم کنیاکی + آیکون پر
        return makeKey("", 1.5f, if (shift != Shift.OFF) Kind.NORMAL else Kind.SPECIAL, icon).also {
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
                LeatherDrawable(st, theme.grain, theme.grainScale, theme.rimWidth, r, density, true, seed)
            )
            addState(
                intArrayOf(),
                LeatherDrawable(st, theme.grain, theme.grainScale, theme.rimWidth, r, density, false, seed)
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
        val st = if (kind == Kind.NORMAL) theme.normal else theme.special
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
