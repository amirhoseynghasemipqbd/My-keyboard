package com.amir.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val gold = Color.parseColor("#D4AF37")
    private val cream = Color.parseColor("#F0DFC4")
    private var pad = 0
    private lateinit var themesBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pad = (20 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        root.addView(label("کیبورد من ⌨️", 28f, gold))
        root.addView(label("۱) «فعال‌سازی» را بزن و کیبورد من را روشن کن.\n۲) «انتخاب کیبورد» را بزن و آن را انتخاب کن.\n۳) در کادر زیر تایپ کن. کلید کره‌ی زبان = تغییر زبان (نگه‌داشتن = کیبورد سیستم)، «123» = اعداد، «@#» = نمادها.", 15f, cream))

        root.addView(button("۱) فعال‌سازی کیبورد", true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(button("۲) انتخاب کیبورد", true) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        root.addView(EditText(this).apply {
            hint = "اینجا تست کن..."
            setHintTextColor(Color.parseColor("#8A7A4A"))
            setTextColor(Color.WHITE)
            gravity = Gravity.START
        })

        root.addView(label("\nتم‌ها 🎨", 22f, gold))
        root.addView(label("روی هر تم بزن تا همان لحظه روی کیبورد اعمال شود.", 13f, Color.parseColor("#A58A68")))

        themesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(themesBox)

        root.addView(button("➕ وارد کردن تم از فایل (JSON)", true) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(i, 1)
        })
        root.addView(label("تم‌های وارد‌شده را با نگه‌داشتن انگشت روی‌شان می‌توانی حذف کنی.", 12f, Color.parseColor("#A58A68")))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            addView(root)
        })
        renderThemes()
    }

    private fun label(t: String, size: Float, color: Int) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        setPadding(0, 0, 0, pad / 2)
    }

    private fun button(t: String, gold: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setTextColor(if (gold) Color.BLACK else cream)
        setBackgroundColor(if (gold) this@MainActivity.gold else Color.parseColor("#2B2B2B"))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = pad / 2 }
    }

    private fun renderThemes() {
        themesBox.removeAllViews()
        val cur = ThemeStore.current(this).id
        ThemeStore.all(this).forEach { t ->
            val selected = t.id == cur
            val title = (if (selected) "✓  " else "") + t.name + (if (!t.builtIn) "  (من)" else "")
            val b = button(title, selected) {
                ThemeStore.select(this, t.id)
                renderThemes()
            }
            if (!t.builtIn) {
                b.setOnLongClickListener {
                    AlertDialog.Builder(this)
                        .setMessage("تم «${t.name}» حذف شود؟")
                        .setPositiveButton("حذف") { _, _ ->
                            ThemeStore.delete(this, t.id)
                            if (t.id == cur) ThemeStore.select(this, ThemeStore.DEFAULT)
                            renderThemes()
                        }
                        .setNegativeButton("نه", null)
                        .show()
                    true
                }
            }
            themesBox.addView(b)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val text = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                val t = ThemeStore.import(this, text)
                ThemeStore.select(this, t.id)
                Toast.makeText(this, "تم «${t.name}» اضافه شد ✓", Toast.LENGTH_SHORT).show()
                renderThemes()
            } catch (e: Exception) {
                Toast.makeText(this, "فایل تم معتبر نیست", Toast.LENGTH_LONG).show()
            }
        }
    }
}
