package com.bedbeam.browser.web

import android.view.KeyEvent
import com.bedbeam.browser.R
import com.bedbeam.browser.ui.VideoOsd
import kotlin.math.abs

/**
 * Remote keys in full-screen video: OK play/pause, ◀ ▶ seek 10 s (held: faster), ▲ ▼ show time,
 * hold OK opens the cross menu. Back is left to the activity (leaves full screen).
 */
class VideoKeys(
    private val osd: VideoOsd,
    private val command: (String) -> Boolean,
    private val onHoldOk: () -> Unit,
) {
    private var okHandled = false
    private var lastSeekAt = 0L
    private var seekTotal = 0
    private var seekResetAt = 0L
    private var pendingLabel: String? = null

    fun handleKey(e: KeyEvent): Boolean {
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                when {
                    e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 -> okHandled = false
                    e.action == KeyEvent.ACTION_DOWN -> if (!okHandled && (e.isLongPress || e.eventTime - e.downTime >= PointerController.HOLD_MS)) {
                        okHandled = true
                        osd.hideNow()
                        onHoldOk()
                    }
                    e.action == KeyEvent.ACTION_UP -> {
                        if (!okHandled) toggle()
                        okHandled = false
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) toggle()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (e.action != KeyEvent.ACTION_DOWN) return true
                val sign = if (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || e.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) -1 else 1
                val now = e.eventTime
                val held = now - e.downTime
                // First press: 10 s. Held: another step every 200 ms, 30 s steps after 1.5 s.
                if (e.repeatCount > 0 && now - lastSeekAt < 200) return true
                lastSeekAt = now
                val step = if (held > 1500) 30 else 10
                if (now > seekResetAt || (seekTotal != 0 && sign != Integer.signum(seekTotal))) seekTotal = 0
                seekTotal += sign * step
                seekResetAt = now + 1500
                pendingLabel = (if (seekTotal > 0) "+" else "−") + formatSeek(abs(seekTotal))
                osd.flash(pendingLabel)
                command("seek:${sign * step}")
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                    pendingLabel = null
                    osd.flash(null)
                    command("state")
                }
                return true
            }
        }
        return false
    }

    /** Reply from the page after a command: refresh the timeline (and the play/pause label). */
    fun onState(t: Double, d: Double, paused: Boolean, afterToggle: Boolean) {
        osd.setTime(t, d)
        if (afterToggle) osd.flash(osd.context.getString(if (paused) R.string.osd_pause else R.string.osd_play))
    }

    private var toggled = false
    fun consumeToggleFlag(): Boolean = toggled.also { toggled = false }

    private fun toggle() {
        toggled = true
        if (!command("toggle")) osd.flash(osd.context.getString(R.string.osd_no_video))
    }

    private fun formatSeek(sec: Int): String {
        val c = osd.context
        return when {
            sec < 60 -> c.getString(R.string.seek_s, sec)
            sec % 60 == 0 -> c.getString(R.string.seek_min, sec / 60)
            else -> c.getString(R.string.seek_min_s, sec / 60, sec % 60)
        }
    }
}
