package com.bedbeam.browser.ui

import android.content.Context
import com.bedbeam.browser.R
import kotlin.math.roundToInt

/**
 * Colour tokens. The source of truth is res/values/colors.xml (change colours there, not here);
 * [load] reads it once at start-up. The defaults below only apply before [load] runs.
 */
object Palette {
    var BG = 0xFF0B0C0E.toInt(); private set
    var SURFACE = 0xFF16181C.toInt(); private set
    var SURFACE2 = 0xFF23272E.toInt(); private set
    var SURFACE3 = 0xFF252A33.toInt(); private set
    var BORDER = 0xFF2A2E35.toInt(); private set
    var BORDER2 = 0xFF3A3F48.toInt(); private set
    var TEXT = 0xFFF2F3F5.toInt(); private set
    var MUTED = 0xFFA3A8B3.toInt(); private set
    var ON_LIGHT_MUTED = 0xFF4A4F58.toInt(); private set
    var ACCENT = 0xFF5AA9FF.toInt(); private set
    var DANGER = 0xFFFF8A80.toInt(); private set
    var SURFACE4 = 0xFF2E333B.toInt(); private set
    var ACCENT_ON_LIGHT = 0xFF1A5FB4.toInt(); private set
    var SCRIM = 0xBF0B0C0E.toInt(); private set
    var SCRIM_STRONG = 0xC70B0C0E.toInt(); private set
    var HINT_TEXT = 0xFFD5D8DE.toInt(); private set
    var HINT_BORDER = 0xFF5A606B.toInt(); private set
    var VIDEO_BG = 0xFF000000.toInt(); private set
    var OSD_BUBBLE = 0xB80B0C0E.toInt(); private set
    var OSD_BAR = 0x990B0C0E.toInt(); private set
    var OSD_TRACK = 0x4DF2F3F5; private set

    fun load(c: Context) {
        BG = c.getColor(R.color.bg)
        SURFACE = c.getColor(R.color.surface)
        SURFACE2 = c.getColor(R.color.surface2)
        SURFACE3 = c.getColor(R.color.surface3)
        BORDER = c.getColor(R.color.border)
        BORDER2 = c.getColor(R.color.border2)
        TEXT = c.getColor(R.color.text)
        MUTED = c.getColor(R.color.muted)
        ON_LIGHT_MUTED = c.getColor(R.color.on_light_muted)
        ACCENT = c.getColor(R.color.accent)
        DANGER = c.getColor(R.color.danger)
        SURFACE4 = c.getColor(R.color.surface4)
        ACCENT_ON_LIGHT = c.getColor(R.color.accent_on_light)
        SCRIM = c.getColor(R.color.scrim)
        SCRIM_STRONG = c.getColor(R.color.scrim_strong)
        HINT_TEXT = c.getColor(R.color.hint_text)
        HINT_BORDER = c.getColor(R.color.hint_border)
        VIDEO_BG = c.getColor(R.color.video_bg)
        OSD_BUBBLE = c.getColor(R.color.osd_bubble)
        OSD_BAR = c.getColor(R.color.osd_bar)
        OSD_TRACK = c.getColor(R.color.osd_track)
    }
}

/**
 * Design-pixel scaling for views drawn in code (the pointer): sizes on the 1280-wide design
 * canvas, scaled to the real screen. XML layouts use dp instead (dp = design px × 0.75).
 */
class Dims(context: Context) {
    private val k = context.resources.displayMetrics.widthPixels / 1280f
    fun px(v: Number): Int = (v.toFloat() * k).roundToInt()
    fun f(v: Number): Float = v.toFloat() * k
}
