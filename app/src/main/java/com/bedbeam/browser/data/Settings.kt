package com.bedbeam.browser.data

import android.content.Context

/** User settings (settings screen), kept in SharedPreferences. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    data class Engine(val name: String, val searchUrl: String)

    var searchEngine: Int
        get() = p.getInt("search_engine", 0).coerceIn(0, ENGINES.lastIndex)
        set(v) = p.edit().putInt("search_engine", v).apply()

    /** Index into [POINTER_SPEEDS]. */
    var pointerSpeed: Int
        get() = p.getInt("pointer_speed", 1).coerceIn(0, POINTER_SPEEDS.lastIndex)
        set(v) = p.edit().putInt("pointer_speed", v).apply()

    var autoFullscreen: Boolean
        get() = p.getBoolean("auto_fullscreen", false)
        set(v) = p.edit().putBoolean("auto_fullscreen", v).apply()

    var desktopMode: Boolean
        get() = p.getBoolean("desktop_mode", false)
        set(v) = p.edit().putBoolean("desktop_mode", v).apply()

    val engine get() = ENGINES[searchEngine]
    val pointerSpeedFactor get() = POINTER_SPEEDS[pointerSpeed].second

    companion object {
        val ENGINES = listOf(
            Engine("Google", "https://www.google.com/search?q="),
            Engine("Yandex", "https://yandex.ru/search/?text="),
            Engine("DuckDuckGo", "https://duckduckgo.com/?q="),
            Engine("Bing", "https://www.bing.com/search?q="),
        )
        val POINTER_SPEEDS = listOf("Slow" to 0.6f, "Normal" to 1f, "Fast" to 1.5f)
    }
}
