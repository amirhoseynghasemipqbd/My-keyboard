package com.amir.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

class MyKeyboardService : InputMethodService(), KeyboardView.Listener {

    private var kb: KeyboardView? = null
    private val prefs by lazy { ThemeStore.prefs(this) }

    // وقتی در اپ تم عوض شد، همین لحظه روی کیبورد اعمال می‌شود
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ThemeStore.KEY) kb?.setTheme(ThemeStore.current(this))
    }

    private fun savedLang() = if (prefs.getString("lang", "en") == "fa") Lang.FA else Lang.EN

    override fun onCreate() {
        super.onCreate()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this, ThemeStore.current(this))
        v.listener = this
        v.setCalc(prefs.getBoolean("calc", false))
        v.setLang(savedLang())
        kb = v
        return v
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (restarting || info == null) return
        val cls = info.inputType and InputType.TYPE_MASK_CLASS

        // فیلد شماره/تلفن: مستقیم صفحه‌ی اعداد
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) {
            kb?.setLang(Lang.EN)
            kb?.setPage(Page.NUMBERS)
            return
        }
        // رمز/ایمیل/آدرس: همیشه انگلیسی
        kb?.setLang(if (isLatinOnly(info)) Lang.EN else savedLang())

        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        kb?.setShift(if (caps != 0) Shift.ON else Shift.OFF)
    }

    private fun isLatinOnly(info: EditorInfo): Boolean {
        if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return when (info.inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI -> true
            else -> false
        }
    }

    // ---------- KeyboardView.Listener ----------
    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        // ماشین‌حساب روشن: بعد از «=» جواب عبارت پشت مکان‌نما را هم می‌نویسد
        if (text == "=" && prefs.getBoolean("calc", false)) {
            val before = ic.getTextBeforeCursor(80, 0)?.toString() ?: ""
            val result = Calc.evaluateTrailing(before)
            ic.commitText(if (result != null) "=$result" else "=", 1)
            return
        }
        ic.commitText(text, 1)
    }

    override fun onDelete() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun onEnter() {
        val ei = currentInputEditorInfo ?: return
        val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        val noAction = (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (noAction || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else {
            currentInputConnection?.performEditorAction(action)
        }
    }

    override fun onPickIme() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onLangChanged(lang: Lang) {
        prefs.edit().putString("lang", if (lang == Lang.FA) "fa" else "en").apply()
    }

    override fun onCalcChanged(on: Boolean) {
        prefs.edit().putBoolean("calc", on).apply()
    }
}
