package com.bedbeam.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bedbeam.browser.R

/** Form for adding or renaming a bookmark (layout: sheet_edit, item_edit_field). Back cancels. */
@SuppressLint("ViewConstructor")
class EditSheet(
    context: Context,
    title: String,
    fields: List<Field>,
    private val onSave: (List<String>) -> Unit,
) : FrameLayout(context) {

    data class Field(val label: String, val initial: String, val isUrl: Boolean = false)

    private val inputs = mutableListOf<EditText>()

    init {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.sheet_edit, this, true)
        findViewById<TextView>(R.id.title).text = title
        val container = findViewById<LinearLayout>(R.id.fields)
        fields.forEachIndexed { i, f ->
            val row = inflater.inflate(R.layout.item_edit_field, container, false)
            row.findViewById<TextView>(R.id.label).text = f.label
            val et = row.findViewById<EditText>(R.id.input).apply {
                setText(f.initial)
                inputType = InputType.TYPE_CLASS_TEXT or
                    (if (f.isUrl) InputType.TYPE_TEXT_VARIATION_URI else InputType.TYPE_TEXT_FLAG_CAP_WORDS)
                val last = i == fields.lastIndex
                imeOptions = (if (last) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT) or
                    EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                if (last) setOnEditorActionListener { _, id, _ ->
                    if (id == EditorInfo.IME_ACTION_DONE) { save(); true } else false
                }
            }
            inputs += et
            container.addView(row)
        }
        findViewById<View>(R.id.save).setOnClickListener { save() }
    }

    fun focusFirst() {
        val et = inputs.firstOrNull() ?: return
        et.requestFocus()
        et.setSelection(et.text.length)
        et.postDelayed({ imm().showSoftInput(et, InputMethodManager.SHOW_IMPLICIT) }, 150)
    }

    fun hideKeyboard() {
        imm().hideSoftInputFromWindow(windowToken, 0)
    }

    private fun imm() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private fun save() {
        hideKeyboard()
        onSave(inputs.map { it.text.toString().trim() })
    }
}
