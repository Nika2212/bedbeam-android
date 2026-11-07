package com.bedbeam.browser.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import kotlin.math.roundToInt

/** Design tokens from the BedBeam design canvas. */
object Palette {
    const val BG = 0xFF0B0C0E.toInt()
    const val SURFACE = 0xFF16181C.toInt()
    const val SURFACE2 = 0xFF23272E.toInt()
    const val SURFACE3 = 0xFF252A33.toInt()
    const val BORDER = 0xFF2A2E35.toInt()
    const val BORDER2 = 0xFF3A3F48.toInt()
    const val TEXT = 0xFFF2F3F5.toInt()
    const val MUTED = 0xFFA3A8B3.toInt()
    const val ON_LIGHT_MUTED = 0xFF4A4F58.toInt()
    const val ACCENT = 0xFF5AA9FF.toInt()
    const val DANGER = 0xFFFF8A80.toInt()
}

/**
 * Sizes are written in design pixels (the 1280-wide design canvas) and scaled to the real
 * screen, so the projector's 1280×720 panel matches the design pixel for pixel.
 */
class Dims(context: Context) {
    private val k = context.resources.displayMetrics.widthPixels / 1280f
    fun px(v: Number): Int = (v.toFloat() * k).roundToInt()
    fun f(v: Number): Float = v.toFloat() * k
}

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0, dash: Float = 0f) =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (stroke > 0) {
            if (dash > 0f) setStroke(stroke, strokeColor, dash, dash) else setStroke(stroke, strokeColor)
        }
    }

fun oval(color: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
}

fun TextView.textStyle(d: Dims, size: Number, color: Int, weight: Int = 400) {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, d.f(size))
    setTextColor(color)
    typeface = Typeface.create(Typeface.SANS_SERIF, weight, false)
    includeFontPadding = false
}

fun View.onFocus(block: (Boolean) -> Unit) = setOnFocusChangeListener { _, has -> block(has) }

/** The focused element grows slightly, as in the design. */
fun View.focusScale(has: Boolean, scale: Float = 1.07f) {
    animate().scaleX(if (has) scale else 1f).scaleY(if (has) scale else 1f).setDuration(120).start()
}
