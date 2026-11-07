package com.bedbeam.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bedbeam.browser.R
import com.bedbeam.browser.data.Bookmark
import com.bedbeam.browser.data.UrlInput

/** Side sheet on hold-OK over a bookmark tile (layout: sheet_bookmark_actions, item_sheet_action*). */
@SuppressLint("ViewConstructor")
class BookmarkActions(
    context: Context,
    bookmark: Bookmark,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
) : FrameLayout(context) {

    private val first: View

    init {
        LayoutInflater.from(context).inflate(R.layout.sheet_bookmark_actions, this, true)
        findViewById<TextView>(R.id.monogram).text = bookmark.name.trim().firstOrNull()?.uppercase() ?: "?"
        findViewById<TextView>(R.id.name).text = bookmark.name
        findViewById<TextView>(R.id.host).text = UrlInput.displayHost(bookmark.url)
        first = action(R.id.action_open, R.drawable.ic_arrow_right, R.string.bm_open, onOpen)
        action(R.id.action_rename, R.drawable.ic_edit, R.string.bm_rename, onRename)
        action(R.id.action_move, R.drawable.ic_move, R.string.bm_move, onMove)
        action(R.id.action_remove, R.drawable.ic_delete, R.string.bm_remove, onRemove)
    }

    fun focusFirst() = first.requestFocus()

    private fun action(id: Int, icon: Int, label: Int, onClick: () -> Unit): View =
        findViewById<View>(id).apply {
            findViewById<ImageView>(R.id.icon).setImageResource(icon)
            findViewById<TextView>(R.id.label).setText(label)
            setOnClickListener { onClick() }
        }
}
