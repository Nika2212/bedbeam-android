package com.bedbeam.browser.web

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import com.bedbeam.browser.ui.Dims
import com.bedbeam.browser.ui.Palette
import kotlin.math.min

/** Draws the on-screen pointer (design: "Web page: pointer mode"). Never takes focus or touches. */
class PointerView(context: Context) : View(context) {
    private val d = Dims(context)
    private val s = d.f(1.5f) // arrow scale: the 22×26 design icon at ~33×39 design px
    private val arrow = Path().apply {
        moveTo(0f, 0f); lineTo(17f * s, 11f * s); lineTo(9.5f * s, 12.6f * s); lineTo(6f * s, 21f * s); close()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TEXT; style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.BG; style = Paint.Style.STROKE; strokeWidth = 1.6f * s; strokeJoin = Paint.Join.ROUND
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.ACCENT; style = Paint.Style.STROKE; strokeWidth = d.f(3)
    }
    var px = 0f; private set
    var py = 0f; private set
    private var ringT = 0f // 0..1 click pulse progress, 0 = none

    init {
        isFocusable = false
        isClickable = false
        setWillNotDraw(false)
    }

    fun moveTo(x: Float, y: Float) {
        px = x; py = y
        invalidate()
    }

    fun pulse() {
        ValueAnimator.ofFloat(0.01f, 1f).apply {
            duration = 260
            addUpdateListener { ringT = it.animatedValue as Float; invalidate() }
            doOnEnd { ringT = 0f; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        if (ringT > 0f) {
            ring.alpha = ((1f - ringT) * 220).toInt()
            c.drawCircle(px, py, d.f(10 + 18 * ringT), ring)
        }
        c.save()
        c.translate(px, py)
        c.drawPath(arrow, fill)
        c.drawPath(arrow, stroke)
        c.restore()
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) = addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = block()
    })
}

/**
 * Pointer mode: the D-pad moves a pointer (accelerating while held), OK taps under it,
 * holding OK calls [onHoldOk], and pushing the pointer against an edge scrolls whatever is under it.
 * Taps, hovers and scrolls are injected as real touch/mouse events, so they also work inside
 * video players embedded from other sites.
 */
class PointerController(
    private val view: PointerView,
    private val web: () -> WebView?,
    private val onHoldOk: () -> Unit,
) {
    private val d = Dims(view.context)
    private val choreographer = Choreographer.getInstance()

    /** Pointer speed setting: 0.6 slow, 1 normal, 1.5 fast. */
    var speedFactor = 1f
    private val scrollFactor = ViewConfiguration.get(view.context).scaledVerticalScrollFactor

    private var left = false
    private var right = false
    private var up = false
    private var down = false
    private var holdStartNs = 0L
    private var lastFrameNs = 0L
    private var running = false
    private var okHandled = false
    private var placed = false

    private val hide = Runnable { view.animate().alpha(0f).setDuration(300).start() }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            step(now)
            if (left || right || up || down) choreographer.postFrameCallback(this) else running = false
        }
    }

    /** Returns true if the key was used by pointer mode. */
    fun handleKey(e: KeyEvent): Boolean {
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                val pressed = e.action == KeyEvent.ACTION_DOWN
                if (pressed && e.repeatCount > 0) return true // movement is time-based, ignore repeats
                when (e.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> left = pressed
                    KeyEvent.KEYCODE_DPAD_RIGHT -> right = pressed
                    KeyEvent.KEYCODE_DPAD_UP -> up = pressed
                    KeyEvent.KEYCODE_DPAD_DOWN -> down = pressed
                }
                if (pressed) {
                    show()
                    holdStartNs = System.nanoTime()
                    nudge()
                    if (!running) {
                        running = true
                        lastFrameNs = 0L
                        choreographer.postFrameCallback(frame)
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                when {
                    e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 -> okHandled = false
                    e.action == KeyEvent.ACTION_DOWN -> if (!okHandled && (e.isLongPress || e.eventTime - e.downTime >= HOLD_MS)) {
                        okHandled = true
                        onHoldOk()
                    }
                    e.action == KeyEvent.ACTION_UP -> {
                        if (!okHandled) tap()
                        okHandled = false
                    }
                }
                return true
            }
        }
        return false
    }

    /** Call when the page area changes size or a new page shows, to keep the pointer on screen. */
    fun ensurePlaced() {
        if (view.width == 0) return
        if (!placed) {
            placed = true
            view.moveTo(view.width / 2f, view.height / 2f)
        } else {
            view.moveTo(view.px.coerceIn(0f, view.width - 1f), view.py.coerceIn(0f, view.height - 1f))
        }
        show()
    }

    fun stop() {
        left = false; right = false; up = false; down = false
        okHandled = false
    }

    /** Pointer position as fractions of the page area (0..1), for handing over to link mode. */
    fun fraction(): Pair<Float, Float> =
        if (view.width == 0) 0.5f to 0.5f else view.px / view.width to view.py / view.height

    /** Tap at a page position (view px) and leave the pointer there. Used by link mode. */
    fun tapAt(x: Float, y: Float) {
        view.moveTo(x.coerceIn(0f, view.width - 1f), y.coerceIn(0f, view.height - 1f))
        tap()
    }

    private fun show() {
        view.removeCallbacks(hide)
        view.animate().cancel()
        view.alpha = 1f
        view.postDelayed(hide, IDLE_HIDE_MS)
    }

    private fun nudge() {
        val n = d.f(NUDGE)
        moveBy((if (right) n else 0f) - (if (left) n else 0f), (if (down) n else 0f) - (if (up) n else 0f))
    }

    private fun step(now: Long) {
        val dt = if (lastFrameNs == 0L) 0.016f else ((now - lastFrameNs) / 1e9f).coerceAtMost(0.05f)
        lastFrameNs = now
        val held = (now - holdStartNs) / 1e9f
        val v = d.f(min(MAX_SPEED, START_SPEED + ACCEL * held) * speedFactor)
        val dx = ((if (right) 1 else 0) - (if (left) 1 else 0)) * v * dt
        val dy = ((if (down) 1 else 0) - (if (up) 1 else 0)) * v * dt
        moveBy(dx, dy)
        show()
    }

    private fun moveBy(dx: Float, dy: Float) {
        if (view.width == 0) return
        val wantX = view.px + dx
        val wantY = view.py + dy
        val x = wantX.coerceIn(0f, view.width - 1f)
        val y = wantY.coerceIn(0f, view.height - 1f)
        view.moveTo(x, y)
        hover(x, y)
        // Whatever didn't fit on screen becomes a scroll of the content under the pointer.
        val overX = wantX - x
        val overY = wantY - y
        if (overX != 0f || overY != 0f) scroll(x, y, overX * SCROLL_GAIN, overY * SCROLL_GAIN)
    }

    // ---- event injection --------------------------------------------------------------------

    private val mouseProps = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
    private val coords = arrayOf(MotionEvent.PointerCoords())

    private fun mouseEvent(action: Int, x: Float, y: Float, hScroll: Float = 0f, vScroll: Float = 0f): MotionEvent {
        val t = SystemClock.uptimeMillis()
        coords[0].clear()
        coords[0].x = x
        coords[0].y = y
        coords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
        coords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
        return MotionEvent.obtain(t, t, action, 1, mouseProps, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
    }

    private fun hover(x: Float, y: Float) {
        val w = web() ?: return
        val e = mouseEvent(MotionEvent.ACTION_HOVER_MOVE, x, y)
        w.dispatchGenericMotionEvent(e)
        e.recycle()
    }

    private fun scroll(x: Float, y: Float, dxPx: Float, dyPx: Float) {
        val w = web() ?: return
        // Wheel axes: +V scrolls up, +H scrolls right; one unit = scrollFactor pixels.
        val e = mouseEvent(MotionEvent.ACTION_SCROLL, x, y, dxPx / scrollFactor, -dyPx / scrollFactor)
        w.dispatchGenericMotionEvent(e)
        e.recycle()
    }

    private fun tap() {
        val w = web() ?: return
        val x = view.px
        val y = view.py
        val downAt = SystemClock.uptimeMillis()
        val downEv = MotionEvent.obtain(downAt, downAt, MotionEvent.ACTION_DOWN, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        w.dispatchTouchEvent(downEv)
        downEv.recycle()
        w.postDelayed({
            val upEv = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            w.dispatchTouchEvent(upEv)
            upEv.recycle()
        }, TAP_MS)
        view.pulse()
        show()
    }

    companion object {
        const val HOLD_MS = 450L
        private const val TAP_MS = 50L
        private const val IDLE_HIDE_MS = 4000L
        // Speeds in design px per second (1280-wide screen).
        private const val NUDGE = 4f
        private const val START_SPEED = 260f
        private const val ACCEL = 1400f
        private const val MAX_SPEED = 1300f
        private const val SCROLL_GAIN = 2.2f
    }
}
