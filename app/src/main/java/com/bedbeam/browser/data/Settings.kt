package com.bedbeam.browser.data

import android.content.Context
import com.bedbeam.browser.R

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

    /** Index into [LANGUAGES]. Saved synchronously: the activity restarts right after a change. */
    var language: Int
        get() = p.getInt("language", 0).coerceIn(0, LANGUAGES.lastIndex)
        set(v) { p.edit().putInt("language", v).commit() }

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
        val POINTER_SPEEDS = listOf(R.string.speed_slow to 0.6f, R.string.speed_normal to 1f, R.string.speed_fast to 1.5f)

        /** UI languages: locale code (null = follow the system) and its name. Add new ones here. */
        val LANGUAGES = listOf<Pair<String?, Int>>(null to R.string.lang_system, "en" to R.string.lang_en, "ru" to R.string.lang_ru)

        /** The chosen UI language code, read before the activity exists (for attachBaseContext). */
        fun languageCode(context: Context): String? =
            LANGUAGES[context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("language", 0).coerceIn(0, LANGUAGES.lastIndex)].first
    }
}
