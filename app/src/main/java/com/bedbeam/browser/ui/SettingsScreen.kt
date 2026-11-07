package com.bedbeam.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bedbeam.browser.R

/**
 * Settings (layout: screen_settings, item_setting_row). Each row is one setting: OK changes its
 * value (cycles through the choices, or runs the action). Back closes.
 */
@SuppressLint("ViewConstructor")
class SettingsScreen(context: Context, rows: List<Row>) : FrameLayout(context) {

    /** [value] is re-read after every OK; [onOk] changes the setting or runs the action. */
    class Row(val label: String, val hint: String, val value: () -> String, val onOk: (Row) -> Unit)

    private val list: LinearLayout
    private val values = ArrayList<Pair<Row, TextView>>()

    init {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.screen_settings, this, true)
        list = findViewById(R.id.rows)
        rows.forEach { r ->
            val v = inflater.inflate(R.layout.item_setting_row, list, false)
            v.findViewById<TextView>(R.id.label).text = r.label
            v.findViewById<TextView>(R.id.hint).text = r.hint
            val value = v.findViewById<TextView>(R.id.value).apply { text = r.value() }
            values += r to value
            v.setOnClickListener {
                r.onOk(r)
                refresh()
            }
            list.addView(v)
        }
    }

    fun focusFirst() = list.getChildAt(0)?.requestFocus()

    /** Re-read every value (after a setting changed something else). */
    fun refresh() = values.forEach { (r, tv) -> tv.text = r.value() }
}
