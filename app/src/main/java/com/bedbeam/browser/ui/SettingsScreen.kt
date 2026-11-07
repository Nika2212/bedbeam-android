package com.bedbeam.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bedbeam.browser.R

/**
 * Settings, in the start screen's style. Each row is one setting: OK changes its value
 * (cycles through the choices, or runs the action). Back closes.
 */
@SuppressLint("ViewConstructor")
class SettingsScreen(context: Context, rows: List<Row>) : LinearLayout(context) {

    /** [value] is re-read after every OK; [onOk] changes the setting or runs the action. */
    class Row(val label: String, val hint: String, val value: () -> String, val onOk: (Row) -> Unit)

    private val d = Dims(context)
    private val list = LinearLayout(context)
    private val values = ArrayList<Pair<Row, TextView>>()

    init {
        orientation = VERTICAL
        setBackgroundColor(Palette.BG)
        setPadding(d.px(72), d.px(40), d.px(72), d.px(28))
        isClickable = true
        // Clip, so a long list scrolls inside its area instead of drawing over the footer.
        clipChildren = true

        addView(TextView(context).apply { text = context.getString(R.string.settings); textStyle(d, 30, Palette.TEXT, 700) })

        list.orientation = VERTICAL
        list.setPadding(0, d.px(4), 0, d.px(4))
        rows.forEach { list.addView(row(it), LayoutParams(LayoutParams.MATCH_PARENT, d.px(62)).apply { topMargin = d.px(6) }) }
        addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = d.px(16) })

        addView(TextView(context).apply {
            text = context.getString(R.string.settings_footer)
            gravity = Gravity.CENTER
            textStyle(d, 16, Palette.MUTED)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = d.px(12) })
    }

    fun focusFirst() = list.getChildAt(0)?.requestFocus()

    /** Re-read every value (after a setting changed something else). */
    fun refresh() = values.forEach { (r, tv) -> tv.text = r.value() }

    private fun row(r: Row) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        setPadding(d.px(28), 0, d.px(28), 0)
        val texts = LinearLayout(context).apply { orientation = VERTICAL }
        val label = TextView(context).apply { text = r.label; textStyle(d, 21, Palette.TEXT, 600) }
        val hint = TextView(context).apply { text = r.hint; textStyle(d, 15, Palette.MUTED) }
        texts.addView(label)
        if (r.hint.isNotEmpty()) texts.addView(hint, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = d.px(4) })
        addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val value = TextView(context).apply { text = r.value(); textStyle(d, 20, Palette.ACCENT, 600) }
        addView(value)
        values += r to value
        fun style(has: Boolean) {
            background = if (has) rounded(Palette.TEXT, d.f(16)) else rounded(Palette.SURFACE, d.f(16), d.px(1), Palette.BORDER)
            label.setTextColor(if (has) Palette.BG else Palette.TEXT)
            hint.setTextColor(if (has) Palette.ON_LIGHT_MUTED else Palette.MUTED)
            value.setTextColor(if (has) Palette.ACCENT_ON_LIGHT else Palette.ACCENT)
        }
        style(false)
        onFocus { style(it) }
        setOnClickListener {
            r.onOk(r)
            refresh()
        }
    }
}
