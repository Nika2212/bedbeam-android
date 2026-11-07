package com.bedbeam.browser.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Feedback over full-screen video (design: "Full-screen video"): a centre bubble ("+10 s", "Pause")
 * and a slim timeline. Shown only after a key press; fades out after 1.5 s.
 */
class VideoOsd(context: Context) : FrameLayout(context) {
    private val d = Dims(context)
    private val bubble = TextView(context)
    private val bar = LinearLayout(context)
    private val now = TextView(context)
    private val total = TextView(context)
    private val track = FrameLayout(context)
    private val fill = View(context)
    private val knob = View(context)
    private var progress = 0f
    private val hide = Runnable { animate().alpha(0f).setDuration(250).start() }

    init {
        isClickable = false
        isFocusable = false
        alpha = 0f

        bubble.apply {
            gravity = Gravity.CENTER
            background = oval(0xB80B0C0E.toInt())
            textStyle(d, 22, Palette.TEXT, 700)
        }
        addView(bubble, LayoutParams(d.px(128), d.px(128), Gravity.CENTER))

        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        now.textStyle(d, 18, Palette.TEXT, 600)
        total.textStyle(d, 18, Palette.TEXT, 600)
        track.background = rounded(0x4DF2F3F5, d.f(3))
        fill.background = rounded(Palette.ACCENT, d.f(3))
        knob.background = oval(Palette.TEXT)
        track.addView(fill, LayoutParams(0, LayoutParams.MATCH_PARENT))
        bar.addView(now)
        val trackBox = FrameLayout(context).apply {
            addView(track, LayoutParams(LayoutParams.MATCH_PARENT, d.px(6), Gravity.CENTER_VERTICAL))
            addView(knob, LayoutParams(d.px(20), d.px(20), Gravity.CENTER_VERTICAL))
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutProgress() }
        }
        bar.addView(trackBox, LinearLayout.LayoutParams(0, d.px(24), 1f).apply {
            marginStart = d.px(20); marginEnd = d.px(20)
        })
        bar.addView(total)
        bar.setPadding(d.px(24), d.px(14), d.px(24), d.px(14))
        bar.background = rounded(0x990B0C0E.toInt(), d.f(24))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            leftMargin = d.px(56); rightMargin = d.px(56); bottomMargin = d.px(32)
        })
    }

    /** Show [label] in the centre bubble (null hides the bubble, keeps the timeline). */
    fun flash(label: String?) {
        bubble.visibility = if (label == null) View.INVISIBLE else View.VISIBLE
        if (label != null) bubble.text = label
        show()
    }

    fun setTime(t: Double, dur: Double) {
        now.text = fmt(t)
        total.text = if (dur > 0) fmt(dur) else "--:--"
        progress = if (dur > 0) (t / dur).toFloat().coerceIn(0f, 1f) else 0f
        layoutProgress()
    }

    fun hideNow() {
        removeCallbacks(hide)
        animate().cancel()
        alpha = 0f
    }

    private fun show() {
        removeCallbacks(hide)
        animate().cancel()
        alpha = 1f
        postDelayed(hide, 1500)
    }

    private fun layoutProgress() {
        val w = track.width
        if (w == 0) return
        fill.layoutParams = fill.layoutParams.apply { width = (w * progress).toInt() }
        knob.translationX = w * progress - d.f(10)
    }

    private fun fmt(sec: Double): String {
        val s = sec.toLong().coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%d:%02d".format(m, ss)
    }
}
