package com.bedbeam.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bedbeam.browser.R
import com.bedbeam.browser.data.Bookmark
import com.bedbeam.browser.data.UrlInput

/** Side sheet shown on hold-OK over a bookmark tile (design: "Bookmark actions"). */
@SuppressLint("ViewConstructor")
class BookmarkActions(
    context: Context,
    bookmark: Bookmark,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
) : FrameLayout(context) {

    private val d = Dims(context)
    private val first: LinearLayout

    init {
        setBackgroundColor(0xBF0B0C0E.toInt()) // dim the start screen behind
        isClickable = true

        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.SURFACE)
            setPadding(d.px(40), d.px(48), d.px(40), d.px(40))
        }
        addView(sheet, LayoutParams(d.px(460), LayoutParams.MATCH_PARENT, Gravity.END))

        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(context).apply {
            text = bookmark.name.trim().firstOrNull()?.uppercase() ?: "?"
            gravity = Gravity.CENTER
            background = oval(Palette.SURFACE3)
            textStyle(d, 28, Palette.TEXT, 700)
        }, LinearLayout.LayoutParams(d.px(64), d.px(64)))
        val names = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        names.addView(TextView(context).apply { text = bookmark.name; textStyle(d, 24, Palette.TEXT, 600) })
        names.addView(TextView(context).apply { text = UrlInput.displayHost(bookmark.url); textStyle(d, 17, Palette.MUTED) },
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = d.px(6) })
        head.addView(names, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = d.px(18) })
        sheet.addView(head)

        first = action(R.drawable.ic_arrow_right, "Open", Palette.TEXT, onOpen)
        sheet.addView(first, actionParams(28))
        sheet.addView(action(R.drawable.ic_edit, "Rename", Palette.TEXT, onRename), actionParams(6))
        sheet.addView(action(R.drawable.ic_move, "Move (then use arrows)", Palette.TEXT, onMove), actionParams(6))
        sheet.addView(action(R.drawable.ic_delete, "Remove", Palette.DANGER, onRemove), actionParams(6))
    }

    fun focusFirst() = first.requestFocus()

    private fun actionParams(top: Int) =
        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, d.px(60)).apply { topMargin = d.px(top) }

    private fun action(iconRes: Int, label: String, color: Int, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        setPadding(d.px(20), 0, d.px(20), 0)
        val icon = ImageView(context).apply { setImageResource(iconRes) }
        addView(icon, LinearLayout.LayoutParams(d.px(22), d.px(22)))
        val text = TextView(context).apply { this.text = label }
        addView(text, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = d.px(16) })
        fun style(has: Boolean) {
            background = if (has) rounded(Palette.TEXT, d.f(14)) else null
            val c = if (has) Palette.BG else color
            text.textStyle(d, 20, c, if (has) 600 else 500)
            icon.imageTintList = ColorStateList.valueOf(if (has) Palette.BG else if (color == Palette.TEXT) Palette.MUTED else color)
        }
        style(false)
        onFocus { style(it) }
        setOnClickListener { onClick() }
    }
}
