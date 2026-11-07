package com.bedbeam.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bedbeam.browser.R
import com.bedbeam.browser.data.Bookmarks

/**
 * Start screen: logo, search pill and the bookmark grid (design: "Start screen").
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
) : LinearLayout(context) {

    private val d = Dims(context)
    private val grid = GridLayout(context)
    private val hints = LinearLayout(context)
    private val search: TextView
    private var moveIndex = -1
    private var lastFocused = 0

    private val tileW = (1280 - 144 - 3 * 24) / 4
    private val tileH = 148

    init {
        orientation = VERTICAL
        setBackgroundColor(Palette.BG)
        setPadding(d.px(72), d.px(40), d.px(72), d.px(28))
        clipChildren = false
        clipToPadding = false

        addView(header())

        search = searchPill()
        addView(search, LayoutParams(d.px(780), d.px(64)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = d.px(32)
        })

        addView(TextView(context).apply {
            text = "BOOKMARKS"
            letterSpacing = 0.12f
            textStyle(d, 15, Palette.MUTED, 600)
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = d.px(32) })

        grid.columnCount = 4
        grid.clipChildren = false
        grid.clipToPadding = false
        grid.setPadding(0, d.px(16), 0, d.px(16))
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(grid)
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        hints.orientation = HORIZONTAL
        hints.gravity = Gravity.CENTER
        addView(hints, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = d.px(12) })

        render(0)
    }

    /** Rebuild the grid (after any bookmark change) and focus [focusIndex] if given. */
    fun render(focusIndex: Int = -1) {
        grid.removeAllViews()
        val list = bookmarks.all()
        list.forEachIndexed { i, b -> grid.addView(tile(i, b.name, b.url), tileParams(i)) }
        grid.addView(addTile(), tileParams(list.size))
        updateHints()
        if (focusIndex >= 0) focusTile(focusIndex)
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

    // ---- pieces -------------------------------------------------------------------------

    private fun header() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(context).apply { setImageResource(R.drawable.ic_launcher) }, LayoutParams(d.px(32), d.px(32)))
        addView(TextView(context).apply {
            text = "BedBeam"
            textStyle(d, 22, Palette.TEXT, 600)
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = d.px(12) })
    }

    private fun searchPill() = TextView(context).apply {
        text = "Search or type a web address"
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        compoundDrawablePadding = d.px(16)
        setPadding(d.px(28), 0, d.px(28), 0)
        val icon = context.getDrawable(R.drawable.ic_search)!!.mutate().apply { setBounds(0, 0, d.px(24), d.px(24)) }
        setCompoundDrawables(icon, null, null, null)
        fun style(has: Boolean) {
            background = if (has) rounded(Palette.TEXT, d.f(32)) else rounded(Palette.SURFACE, d.f(32), d.px(1), Palette.BORDER)
            textStyle(d, 21, if (has) Palette.BG else Palette.MUTED, if (has) 500 else 400)
            compoundDrawableTintList = ColorStateList.valueOf(if (has) Palette.ON_LIGHT_MUTED else Palette.MUTED)
        }
        style(false)
        onFocus { has -> style(has); focusScale(has, 1.03f) }
        setOnClickListener { onOpenAddress() }
    }

    private fun tileParams(i: Int) = GridLayout.LayoutParams().apply {
        width = d.px(tileW)
        height = d.px(tileH)
        setMargins(0, 0, if (i % 4 == 3) 0 else d.px(24), d.px(24))
    }

    private fun tile(index: Int, name: String, url: String): View {
        val tile = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            isLongClickable = true
        }
        val mono = TextView(context).apply {
            text = name.trim().firstOrNull()?.uppercase() ?: "?"
            gravity = Gravity.CENTER
            textStyle(d, 24, Palette.TEXT, 700)
        }
        tile.addView(mono, LayoutParams(d.px(56), d.px(56)))
        val label = TextView(context).apply {
            text = name
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(d.px(12), 0, d.px(12), 0)
        }
        tile.addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = d.px(14) })

        val moving = index == moveIndex
        fun style(has: Boolean) {
            tile.background = when {
                has -> rounded(Palette.TEXT, d.f(18), if (moving) d.px(4) else 0, Palette.ACCENT)
                else -> rounded(Palette.SURFACE, d.f(18), d.px(1), 0xFF23272E.toInt())
            }
            mono.background = oval(if (has) Palette.BG else Palette.SURFACE3)
            label.textStyle(d, 20, if (has) Palette.BG else Palette.TEXT, if (has) 600 else 500)
            tile.elevation = if (has) d.f(14) else 0f
            tile.focusScale(has)
        }
        style(false)
        tile.onFocus { has ->
            style(has)
            if (has) lastFocused = index
        }
        tile.setOnClickListener { if (moveIndex < 0) onOpenUrl(url) }
        tile.setOnLongClickListener {
            if (moveIndex < 0) onBookmarkActions(index)
            true
        }
        return tile
    }

    private fun addTile(): View {
        val index = bookmarks.all().size
        val tile = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
        }
        val icon = ImageView(context).apply { setImageResource(R.drawable.ic_plus) }
        tile.addView(icon, LayoutParams(d.px(32), d.px(32)))
        val label = TextView(context).apply { text = "Add bookmark" }
        tile.addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = d.px(14) })
        fun style(has: Boolean) {
            tile.background = if (has) rounded(Palette.TEXT, d.f(18)) else rounded(0, d.f(18), d.px(2), Palette.BORDER2, d.f(8))
            icon.imageTintList = ColorStateList.valueOf(if (has) Palette.BG else Palette.MUTED)
            label.textStyle(d, 20, if (has) Palette.BG else Palette.MUTED, if (has) 600 else 500)
            tile.elevation = if (has) d.f(14) else 0f
            tile.focusScale(has)
        }
        style(false)
        tile.onFocus { has ->
            style(has)
            if (has) lastFocused = index
        }
        tile.setOnClickListener { if (moveIndex < 0) onAddBookmark() }
        return tile
    }

    private fun updateHints() {
        hints.removeAllViews()
        val items = if (moveIndex >= 0) {
            listOf("← ↑ ↓ →" to "Move bookmark", "OK" to "Done")
        } else {
            listOf("OK" to "Open", "Hold OK" to "Edit bookmark", "↑" to "Address bar", "Back" to "Exit")
        }
        items.forEachIndexed { i, (key, what) ->
            hints.addView(hint(key, what), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) marginStart = d.px(36)
            })
        }
    }

    private fun hint(key: String, what: String) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = key
            textStyle(d, 16, Palette.TEXT, 600)
            setPadding(d.px(10), d.px(3), d.px(10), d.px(3))
            background = rounded(0, d.f(6), d.px(1), Palette.BORDER2)
        })
        addView(TextView(context).apply {
            text = what
            textStyle(d, 16, Palette.MUTED)
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = d.px(10) })
    }
}
