package com.bedbeam.browser.web

import android.view.KeyEvent

/**
 * Link mode: arrows jump the focus ring between links and buttons (the page script picks the
 * nearest one in that direction, or scrolls when there is none), OK opens it with a real tap,
 * hold OK opens the cross menu.
 */
class LinkKeys(
    private val send: (String) -> Boolean,
    private val onHoldOk: () -> Unit,
) {
    private var okHandled = false
    private var lastMove = 0L

    fun handleKey(e: KeyEvent): Boolean {
        val dir = when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            else -> null
        }
        if (dir != null) {
            if (e.action == KeyEvent.ACTION_DOWN) {
                // Held arrow: keep jumping, but no faster than ~7 per second.
                if (e.repeatCount == 0 || e.eventTime - lastMove >= 140) {
                    lastMove = e.eventTime
                    send("link:$dir")
                }
            }
            return true
        }
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                when {
                    e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 -> okHandled = false
                    e.action == KeyEvent.ACTION_DOWN -> if (!okHandled && (e.isLongPress || e.eventTime - e.downTime >= PointerController.HOLD_MS)) {
                        okHandled = true
                        onHoldOk()
                    }
                    e.action == KeyEvent.ACTION_UP -> {
                        if (!okHandled) send("link:target")
                        okHandled = false
                    }
                }
                return true
            }
        }
        return false
    }

    fun stop() {
        okHandled = false
    }
}
