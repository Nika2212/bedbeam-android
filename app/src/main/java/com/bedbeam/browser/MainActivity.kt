package com.bedbeam.browser

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Step 0: remote key tester.
 * Shows every key the remote sends (name, code, scan code, device, short/long press)
 * and logs it under the "BedBeamKeys" tag, so the real buttons can be mapped.
 * Hold Back for 2 seconds to exit; Home always works.
 */
class MainActivity : Activity() {

    private lateinit var log: TextView
    private val lines = ArrayDeque<String>()
    private var backDownAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0C0E"))
            setPadding(dp(48), dp(32), dp(48), dp(24))
        }
        root.addView(text("BedBeam · key test", 26f, "#F2F3F5", bold = true))
        root.addView(text("Press every button on the remote, one at a time. Hold Back 2 s to exit.", 16f, "#A3A8B3").apply {
            setPadding(0, dp(8), 0, dp(20))
        })
        log = text("Waiting for a key…", 18f, "#F2F3F5").apply {
            typeface = Typeface.MONOSPACE
            gravity = Gravity.TOP
        }
        root.addView(log)
        setContentView(root)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            backDownAt = event.eventTime
        }
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_BACK &&
            event.eventTime - backDownAt >= 2000
        ) {
            finish()
            return true
        }
        record(event)
        return true // consume everything, so no key does anything else while testing
    }

    private fun record(e: KeyEvent) {
        val action = when (e.action) {
            KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) "down" else "repeat#${e.repeatCount}"
            KeyEvent.ACTION_UP -> "up   (held ${e.eventTime - e.downTime} ms)"
            else -> "action${e.action}"
        }
        val device = e.device?.name ?: "?"
        val line = "${KeyEvent.keyCodeToString(e.keyCode).removePrefix("KEYCODE_").padEnd(18)} " +
            "code=${e.keyCode.toString().padEnd(4)} scan=${e.scanCode.toString().padEnd(4)} " +
            "$action  [$device]"
        Log.i("BedBeamKeys", line)
        // Only show downs and ups (repeats would flood the screen).
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount > 0) return
        lines.addFirst(line)
        while (lines.size > 14) lines.removeLast()
        log.text = lines.joinToString("\n")
    }

    private fun text(s: String, sp: Float, color: String, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
