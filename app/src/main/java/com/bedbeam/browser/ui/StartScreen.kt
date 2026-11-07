package com.bedbeam.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bedbeam.browser.R
import com.bedbeam.browser.data.Bookmarks

/**
 * Start screen (layout: screen_start, item_bookmark_tile, item_add_tile, item_key_hint).
 * Hold OK on a tile → bookmark actions. "Move" puts a tile in move mode: arrows move it, OK/Back finish.
 */
@SuppressLint("ViewConstructor")
class StartScreen(
    context: Context,
    private val bookmarks: Bookmarks,
    private val onOpenUrl: (String) -> Unit,
    private val onOpenAddress: () -> Unit,
    private val onBookmarkActions: (Int) -> Unit,
    private val onAddBookmark: () -> Unit,
    private val onOpenSettings: () -> Unit,
) : FrameLayout(context) {

    private val inflater = LayoutInflater.from(context)
    private val grid: GridLayout
    private val hints: LinearLayout
    private var moveIndex = -1
    private var lastFocused = 0

    init {
        inflater.inflate(R.layout.screen_start, this, true)
        grid = findViewById(R.id.bookmark_grid)
        hints = findViewById(R.id.hints)
        findViewById<View>(R.id.search_pill).setOnClickListener { onOpenAddress() }
        findViewById<View>(R.id.settings_button).setOnClickListener { onOpenSettings() }
        render(0)
    }

    /** Rebuild the grid (after any bookmark change) and focus [focusIndex] if given. */
    fun render(focusIndex: Int = -1) {
        grid.removeAllViews()
        val list = bookmarks.all()
        list.forEachIndexed { i, b ->
            val tile = inflater.inflate(R.layout.item_bookmark_tile, grid, false)
            tile.findViewById<TextView>(R.id.monogram).text = b.name.trim().firstOrNull()?.uppercase() ?: "?"
            tile.findViewById<TextView>(R.id.name).text = b.name
            tile.isActivated = i == moveIndex // accent outline while being moved
            tile.setOnFocusChangeListener { _, has -> if (has) lastFocused = i }
            tile.setOnClickListener { if (moveIndex < 0) onOpenUrl(b.url) }
            tile.setOnLongClickListener {
                if (moveIndex < 0) onBookmarkActions(i)
                true
            }
            addTile(tile, i)
        }
        val add = inflater.inflate(R.layout.item_add_tile, grid, false)
        add.setOnFocusChangeListener { _, has -> if (has) lastFocused = list.size }
        add.setOnClickListener { if (moveIndex < 0) onAddBookmark() }
        addTile(add, list.size)
        updateHints()
        if (focusIndex >= 0) focusTile(focusIndex)
    }

    /** Last column has no right gap, so four tiles fill the width exactly. */
    private fun addTile(tile: View, index: Int) {
        val lp = tile.layoutParams as GridLayout.LayoutParams
        if (index % 4 == 3) lp.rightMargin = 0
        grid.addView(tile, lp)
    }

    fun focusTile(index: Int) {
        val i = index.coerceIn(0, grid.childCount - 1)
        grid.getChildAt(i)?.requestFocus()
    }

    fun restoreFocus() = focusTile(lastFocused)

    fun startMove(index: Int) {
        moveIndex = index
        render(index)
    }

    private fun finishMove() {
        val i = moveIndex
        moveIndex = -1
        render(i)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (moveIndex < 0) return super.dispatchKeyEvent(e)
        val finishKeys = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)
        if (e.action == KeyEvent.ACTION_DOWN) {
            val delta = when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                KeyEvent.KEYCODE_DPAD_UP -> -4
                KeyEvent.KEYCODE_DPAD_DOWN -> 4
                else -> 0
            }
            if (delta != 0) {
                val to = (moveIndex + delta).coerceIn(0, bookmarks.all().size - 1)
                if (to != moveIndex) {
                    bookmarks.move(moveIndex, to)
                    moveIndex = to
                    render(to)
                }
                return true
            }
            if (e.keyCode in finishKeys) {
                if (e.repeatCount == 0) finishMove()
                return true
            }
        }
        if (e.action == KeyEvent.ACTION_UP && e.keyCode in finishKeys) return true
        return super.dispatchKeyEvent(e)
    }

    private fun updateHints() {
        val items = if (moveIndex >= 0) {
            listOf(R.string.key_arrows to R.string.hint_move_bookmark, R.string.key_ok to R.string.hint_done)
        } else {
            listOf(
                R.string.key_ok to R.string.hint_open, R.string.key_hold_ok to R.string.hint_edit_bookmark,
                R.string.key_up to R.string.hint_address_bar, R.string.key_back to R.string.hint_exit,
            )
        }
        KeyHints.fill(hints, R.layout.item_key_hint, items)
    }
}

/** Fills a hint row with "key  meaning" chips from [layout] (item_key_hint / item_key_hint_dim). */
object KeyHints {
    fun fill(row: LinearLayout, layout: Int, items: List<Pair<Int, Int>>) {
        row.removeAllViews()
        val inflater = LayoutInflater.from(row.context)
        items.forEachIndexed { i, (key, label) ->
            val v = inflater.inflate(layout, row, false)
            v.findViewById<TextView>(R.id.key).setText(key)
            v.findViewById<TextView>(R.id.label).setText(label)
            if (i == 0) (v.layoutParams as LinearLayout.LayoutParams).marginStart = 0
            row.addView(v)
        }
    }
}
