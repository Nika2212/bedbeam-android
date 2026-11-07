package com.bedbeam.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bedbeam.browser.R

/**
 * Hold-OK menu over a page (layout: overlay_cross_menu, item_cross_pill_inner/outer). Each arrow
 * direction has an inner and an outer action: press an arrow once for the inner one, again for the
 * outer, the opposite arrow to step back; OK runs it, Back closes. No focus system: keys come in
 * through [handleKey]; the selected pill uses the "selected" state for its look.
 */
@SuppressLint("ViewConstructor")
class CrossMenu(
    context: Context,
    centerTitle: String,
    centerSub: String,
    private val arms: Map<Dir, List<Action>>,
    private val onClose: () -> Unit,
) : FrameLayout(context) {

    enum class Dir { UP, DOWN, LEFT, RIGHT }

    class Action(val label: String, val icon: Int, val enabled: Boolean = true, val run: () -> Unit)

    private data class Sel(val dir: Dir, val level: Int)

    private val pills = HashMap<Sel, Pair<View, Action>>()
    private var sel: Sel? = null
    /** The OK press that opened the menu is still held: swallow its repeats and release. */
    private var swallowOk = true
    private var okArmed = false

    init {
        LayoutInflater.from(context).inflate(R.layout.overlay_cross_menu, this, true)
        findViewById<TextView>(R.id.center_title).text = centerTitle
        findViewById<TextView>(R.id.center_sub).apply {
            text = centerSub
            visibility = if (centerSub.isEmpty()) View.GONE else View.VISIBLE
        }
        val slots = mapOf(
            Dir.UP to listOf(R.id.pill_up_inner, R.id.pill_up_outer),
            Dir.DOWN to listOf(R.id.pill_down_inner, R.id.pill_down_outer),
            Dir.LEFT to listOf(R.id.pill_left_inner, R.id.pill_left_outer),
            Dir.RIGHT to listOf(R.id.pill_right_inner, R.id.pill_right_outer),
        )
        for ((dir, ids) in slots) {
            val actions = arms[dir].orEmpty()
            ids.forEachIndexed { level, id ->
                val pill = findViewById<View>(id)
                val a = actions.getOrNull(level)
                if (a == null) {
                    pill.visibility = View.GONE
                } else {
                    pill.findViewById<ImageView>(R.id.icon).setImageResource(a.icon)
                    pill.findViewById<TextView>(R.id.label).text = a.label
                    pill.alpha = if (a.enabled) 1f else 0.4f
                    pills[Sel(dir, level)] = pill to a
                }
            }
        }
        KeyHints.fill(
            findViewById<LinearLayout>(R.id.hints), R.layout.item_key_hint_dim,
            listOf(R.string.key_arrow to R.string.menu_pick, R.string.key_ok to R.string.menu_do, R.string.key_back to R.string.menu_close),
        )
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
        // Run on OK *release*, while the menu still owns the key: running on press let the
        // release fall through to the page and click whatever was under the pointer.
        if (isOk) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) okArmed = true
            if (e.action == KeyEvent.ACTION_UP && okArmed) {
                okArmed = false
                val p = sel?.let { pills[it] }
                if (p == null) onClose()
                else if (p.second.enabled) {
                    onClose()
                    p.second.run()
                }
            }
            return true
        }
        if (e.action != KeyEvent.ACTION_DOWN) return true
        val dir = when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> Dir.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Dir.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> Dir.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Dir.RIGHT
            else -> null
        }
        if (dir != null && e.repeatCount == 0) select(dir)
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
        for ((key, p) in pills) p.first.isSelected = key == sel
    }

    private fun opposite(dir: Dir) = when (dir) {
        Dir.UP -> Dir.DOWN; Dir.DOWN -> Dir.UP; Dir.LEFT -> Dir.RIGHT; Dir.RIGHT -> Dir.LEFT
    }

    companion object {
        val ICON_BACK = R.drawable.ic_arrow_left
        val ICON_FORWARD = R.drawable.ic_arrow_right
        val ICON_RELOAD = R.drawable.ic_reload
        val ICON_BOOKMARK = R.drawable.ic_star
        val ICON_ADDRESS = R.drawable.ic_search
        val ICON_HOME = R.drawable.ic_home
        val ICON_SHIELD = R.drawable.ic_shield
        val ICON_LINK = R.drawable.ic_link
        val ICON_POINTER = R.drawable.ic_pointer
    }
}
