package com.amir.keyboard

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** ماشین‌حساب کوچک: عبارت آخرِ پشت مکان‌نما را پیدا می‌کند و جواب را می‌دهد. */
object Calc {
    private const val ALLOWED = "0123456789+-*/().%, "

    /** مثلاً "جمع: 2+2" ← "4". اگر عبارت معتبر (با حداقل یک عملگر) نبود null. */
    fun evaluateTrailing(before: String): String? {
        val text = toLatinDigits(before)
        var start = text.length
        while (start > 0 && ALLOWED.indexOf(text[start - 1]) >= 0) start--
        val run = text.substring(start)
        for (i in run.indices) {
            if (run[i] == ' ' || run[i] == ',') continue
            val expr = run.substring(i).replace(",", "")
            if (!expr.any { it == '+' || it == '-' || it == '*' || it == '/' || it == '%' }) continue
            val v = Parser(expr).parse() ?: continue
            val f = format(v)
            if (f != null) return f
        }
        return null
    }

    private fun toLatinDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun format(v: Double): String? {
        if (v.isNaN() || v.isInfinite() || abs(v) > 1e15) return null
        val bd = BigDecimal(v).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
        val s = bd.toPlainString()
        return if (s == "-0") "0" else s
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && s[i] == ' ') i++
        }

        fun parse(): Double? {
            val v = expr() ?: return null
            ws()
            return if (i == s.length) v else null
        }

        fun expr(): Double? {
            var v = term() ?: return null
            while (true) {
                ws()
                if (i >= s.length) break
                val c = s[i]
                if (c == '+' || c == '-') {
                    i++
                    val r = term() ?: return null
                    v = if (c == '+') v + r else v - r
                } else break
            }
            return v
        }

        fun term(): Double? {
            var v = factor() ?: return null
            while (true) {
                ws()
                if (i >= s.length) break
                val c = s[i]
                if (c == '*' || c == '/') {
                    i++
                    val r = factor() ?: return null
                    if (c == '*') {
                        v *= r
                    } else {
                        if (r == 0.0) return null
                        v /= r
                    }
                } else if (c == '(') {
                    val r = factor() ?: return null
                    v *= r
                } else break
            }
            return v
        }

        fun factor(): Double? {
            ws()
            if (i >= s.length) return null
            val c = s[i]
            if (c == '-') {
                i++
                val f = factor() ?: return null
                return -f
            }
            if (c == '+') {
                i++
                return factor()
            }
            var v = primary() ?: return null
            while (true) {
                ws()
                if (i < s.length && s[i] == '%') {
                    i++
                    v /= 100.0
                } else break
            }
            return v
        }

        fun primary(): Double? {
            ws()
            if (i >= s.length) return null
            if (s[i] == '(') {
                i++
                val v = expr() ?: return null
                ws()
                if (i < s.length && s[i] == ')') {
                    i++
                    return v
                }
                return null
            }
            val st = i
            while (i < s.length && (s[i] in '0'..'9' || s[i] == '.')) i++
            if (st == i) return null
            return s.substring(st, i).toDoubleOrNull()
        }
    }
}
