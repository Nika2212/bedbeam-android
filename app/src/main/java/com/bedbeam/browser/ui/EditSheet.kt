package com.bedbeam.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bedbeam.browser.R

/** Centered form for adding or renaming a bookmark. Back cancels. */
@SuppressLint("ViewConstructor")
class EditSheet(
    context: Context,
    title: String,
    fields: List<Field>,
    private val onSave: (List<String>) -> Unit,
) : FrameLayout(context) {

    data class Field(val label: String, val initial: String, val isUrl: Boolean = false)

    private val d = Dims(context)
    private val inputs = mutableListOf<EditText>()

    init {
        setBackgroundColor(Palette.SCRIM)
        isClickable = true

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.SURFACE, d.f(24), d.px(1), Palette.BORDER)
            setPadding(d.px(40), d.px(36), d.px(40), d.px(36))
        }
        addView(panel, LayoutParams(d.px(640), LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply {
            topMargin = d.px(48) // stay above the on-screen keyboard
        })

        panel.addView(TextView(context).apply { text = title; textStyle(d, 26, Palette.TEXT, 700) })

        fields.forEachIndexed { i, f ->
            panel.addView(TextView(context).apply { text = f.label; textStyle(d, 16, Palette.MUTED, 500) },
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = d.px(if (i == 0) 24 else 16) })
            val et = EditText(context).apply {
                setText(f.initial)
                isSingleLine = true
                textStyle(d, 20, Palette.TEXT)
                setHintTextColor(Palette.MUTED)
                setPadding(d.px(20), 0, d.px(20), 0)
                inputType = InputType.TYPE_CLASS_TEXT or
                    (if (f.isUrl) InputType.TYPE_TEXT_VARIATION_URI else InputType.TYPE_TEXT_FLAG_CAP_WORDS)
                imeOptions = (if (i == fields.lastIndex) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT) or
                    EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                fun style(has: Boolean) {
                    background = rounded(Palette.SURFACE2, d.f(12), d.px(if (has) 3 else 1), if (has) Palette.ACCENT else Palette.BORDER)
                }
                style(false)
                onFocus { style(it) }
                if (i == fields.lastIndex) {
                    setOnEditorActionListener { _, id, _ ->
                        if (id == EditorInfo.IME_ACTION_DONE) { save(); true } else false
                    }
                }
            }
            inputs += et
            panel.addView(et, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, d.px(56)).apply { topMargin = d.px(8) })
        }

        val saveBtn = TextView(context).apply {
            text = context.getString(R.string.save)
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            fun style(has: Boolean) {
                background = rounded(if (has) Palette.TEXT else Palette.ACCENT, d.f(28))
                textStyle(d, 20, Palette.BG, 600)
            }
            style(false)
            onFocus { style(it); focusScale(it, 1.05f) }
            setOnClickListener { save() }
        }
        panel.addView(saveBtn, LinearLayout.LayoutParams(d.px(180), d.px(56)).apply {
            topMargin = d.px(28)
            gravity = Gravity.END
        })
    }

    fun focusFirst() {
        val et = inputs.firstOrNull() ?: return
        et.requestFocus()
        et.setSelection(et.text.length)
        et.postDelayed({
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    fun hideKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(windowToken, 0)
    }

    private fun save() {
        hideKeyboard()
        onSave(inputs.map { it.text.toString().trim() })
    }
}
