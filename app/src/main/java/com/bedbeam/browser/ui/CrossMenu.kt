package com.bedbeam.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bedbeam.browser.R

/**
 * Hold-OK menu over a page (design: "Cross menu"). Each arrow direction has an inner and an
 * outer action: press an arrow once for the inner one, again for the outer, the opposite arrow
 * to step back; OK runs it, Back closes. No focus system: keys come in through [handleKey].
 */
@SuppressLint("ViewConstructor")
class CrossMenu(
    context: Context,
    centerTitle: String,
    centerSub: String,
    arms: Map<Dir, List<Action>>,
    private val onClose: () -> Unit,
) : FrameLayout(context) {

    enum class Dir { UP, DOWN, LEFT, RIGHT }

    class Action(val label: String, val icon: Int, val enabled: Boolean = true, val run: () -> Unit)

    private data class Sel(val dir: Dir, val level: Int)

    private val d = Dims(context)
    private val pills = HashMap<Sel, Pill>()
    private val arms = arms
    private var sel: Sel? = null
    /** The OK press that opened the menu is still held: swallow its repeats and release. */
    private var swallowOk = true

    private inner class Pill(val root: LinearLayout, val icon: ImageView, val text: TextView, val action: Action, val inner: Boolean)

    init {
        setBackgroundColor(0xC70B0C0E.toInt()) // dim the page (78%)
        isClickable = true
        clipChildren = false

        // Center circle: 168 px at (556, 276) on the 1280×720 design.
        val center = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = oval(Palette.SURFACE).apply { setStroke(d.px(2), Palette.BORDER) }
            setPadding(d.px(14), 0, d.px(14), 0)
        }
        center.addView(TextView(context).apply {
            text = centerTitle
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            textStyle(d, 17, Palette.TEXT, 600)
        })
        if (centerSub.isNotEmpty()) center.addView(TextView(context).apply {
            text = centerSub
            gravity = Gravity.CENTER
            textStyle(d, 15, Palette.MUTED)
        }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = d.px(4) })
        place(center, 556, 276, 168, 168)

        // Arm positions from the design: (left, top, width) for inner then outer.
        val layout = mapOf(
            Dir.UP to listOf(Triple(540, 192, 200), Triple(540, 116, 200)),
            Dir.DOWN to listOf(Triple(530, 468, 220), Triple(530, 544, 220)),
            Dir.LEFT to listOf(Triple(352, 330, 180), Triple(156, 330, 180)),
            Dir.RIGHT to listOf(Triple(748, 330, 180), Triple(944, 330, 180)),
        )
        for ((dir, actions) in arms) {
            actions.take(2).forEachIndexed { level, a ->
                val (l, t, w) = layout.getValue(dir)[level]
                val pill = pill(a, inner = level == 0)
                place(pill.root, l, t, w, 60)
                pills[Sel(dir, level)] = pill
            }
        }

        val hints = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("Arrow" to "Pick (press twice for outer)", "OK" to "Do it", "Back" to "Close").forEachIndexed { i, (k, what) ->
            hints.addView(hint(k, what), LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) marginStart = d.px(32)
            })
        }
        addView(hints, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = d.px(640) })

        render()
    }

    /** Returns true if the key was used. Back is left to the activity (it closes the menu). */
    fun handleKey(e: KeyEvent): Boolean {
        val isOk = e.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || e.keyCode == KeyEvent.KEYCODE_ENTER ||
            e.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (isOk && swallowOk) {
            if (e.action == KeyEvent.ACTION_UP) swallowOk = false
            return true
        }
        if (e.keyCode == KeyEvent.KEYCODE_BACK) return false
        if (e.action != KeyEvent.ACTION_DOWN) return true
        val dir = when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> Dir.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Dir.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> Dir.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Dir.RIGHT
            else -> null
        }
        if (dir != null) {
            if (e.repeatCount == 0) select(dir)
            return true
        }
        if (isOk && e.repeatCount == 0) {
            val p = sel?.let { pills[it] }
            if (p == null) onClose()
            else if (p.action.enabled) {
                onClose()
                p.action.run()
            }
        }
        return true
    }

    private fun select(dir: Dir) {
        val s = sel
        val count = arms[dir]?.size?.coerceAtMost(2) ?: 0
        sel = when {
            s == null -> if (count > 0) Sel(dir, 0) else null
            s.dir == dir -> if (s.level + 1 < count) Sel(dir, s.level + 1) else s
            s.dir == opposite(dir) -> if (s.level > 0) Sel(s.dir, s.level - 1) else null
            else -> if (count > 0) Sel(dir, 0) else s
        }
        render()
    }

    private fun opposite(dir: Dir) = when (dir) {
        Dir.UP -> Dir.DOWN; Dir.DOWN -> Dir.UP; Dir.LEFT -> Dir.RIGHT; Dir.RIGHT -> Dir.LEFT
    }

    private fun render() {
        for ((key, p) in pills) {
            val on = key == sel
            p.root.background = when {
                on -> rounded(Palette.TEXT, d.f(30), d.px(4), Palette.ACCENT)
                p.inner -> rounded(0xFF2E333B.toInt(), d.f(30))
                else -> rounded(Palette.SURFACE2, d.f(30))
            }
            val fg = if (on) Palette.BG else Palette.TEXT
            p.text.textStyle(d, 18, fg, if (on) 700 else if (p.inner) 600 else 500)
            p.icon.imageTintList = ColorStateList.valueOf(fg)
            p.root.alpha = if (p.action.enabled) 1f else 0.4f
            p.root.animate().scaleX(if (on) 1.08f else 1f).scaleY(if (on) 1.08f else 1f).setDuration(100).start()
        }
    }

    private fun pill(a: Action, inner: Boolean): Pill {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val icon = ImageView(context).apply { setImageResource(a.icon) }
        root.addView(icon, LinearLayout.LayoutParams(d.px(20), d.px(20)))
        val text = TextView(context).apply {
            text = a.label
            maxLines = 1
        }
        root.addView(text, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = d.px(10) })
        return Pill(root, icon, text, a, inner)
    }

    private fun place(v: android.view.View, left: Int, top: Int, w: Int, h: Int) {
        addView(v, LayoutParams(d.px(w), d.px(h)).apply {
            leftMargin = d.px(left)
            topMargin = d.px(top)
        })
    }

    private fun hint(key: String, what: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = key
            textStyle(d, 16, Palette.TEXT, 600)
            setPadding(d.px(10), d.px(3), d.px(10), d.px(3))
            background = rounded(0, d.f(6), d.px(1), 0xFF5A606B.toInt())
        })
        addView(TextView(context).apply {
            text = what
            textStyle(d, 16, 0xFFD5D8DE.toInt())
        }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = d.px(10) })
    }

    companion object {
        val ICON_BACK = R.drawable.ic_arrow_left
        val ICON_FORWARD = R.drawable.ic_arrow_right
        val ICON_RELOAD = R.drawable.ic_reload
        val ICON_BOOKMARK = R.drawable.ic_star
        val ICON_ADDRESS = R.drawable.ic_search
        val ICON_HOME = R.drawable.ic_home
        val ICON_SHIELD = R.drawable.ic_shield
    }
}
