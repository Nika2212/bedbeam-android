package com.bedbeam.browser.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.bedbeam.browser.R

/**
 * Feedback over full-screen video (layout: overlay_video_osd): a centre bubble ("+10 s", "Pause")
 * and a slim timeline. Shown only after a key press; fades out after 1.5 s.
 */
class VideoOsd(context: Context) : FrameLayout(context) {
    private val bubble: TextView
    private val now: TextView
    private val total: TextView
    private val track: View
    private val fill: View
    private val knob: View
    private var progress = 0f
    private val hide = Runnable { animate().alpha(0f).setDuration(250).start() }

    init {
        LayoutInflater.from(context).inflate(R.layout.overlay_video_osd, this, true)
        bubble = findViewById(R.id.bubble)
        now = findViewById(R.id.time_now)
        total = findViewById(R.id.time_total)
        track = findViewById(R.id.track)
        fill = findViewById(R.id.track_fill)
        knob = findViewById(R.id.track_knob)
        findViewById<View>(R.id.track_box).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutProgress() }
        isClickable = false
        isFocusable = false
        alpha = 0f
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
        knob.translationX = w * progress - knob.width / 2f
    }

    private fun fmt(sec: Double): String {
        val s = sec.toLong().coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%d:%02d".format(m, ss)
    }
}
