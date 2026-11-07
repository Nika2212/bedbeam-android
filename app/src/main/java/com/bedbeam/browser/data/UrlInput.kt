package com.bedbeam.browser.data

import android.net.Uri
import java.net.URLEncoder

/** Turns what the user typed into a URL: a web address as-is, anything else as a search. */
object UrlInput {
    /** The search engine in use; set from [Settings] at start and whenever it changes. */
    @Volatile
    var engine: Settings.Engine = Settings.ENGINES[0]

    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val hostLike = Regex("^[^\\s/]+\\.[a-zA-Z]{2,}(:\\d+)?(/.*)?$")
    private val ipLike = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$")

    fun looksLikeUrl(input: String): Boolean {
        val s = input.trim()
        if (s.isEmpty() || s.contains(' ')) return false
        return s.startsWith("http://") || s.startsWith("https://") ||
            hostLike.matches(s) || ipLike.matches(s) || s.startsWith("localhost")
    }

    fun toUrl(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return ""
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        if (looksLikeUrl(s)) return "https://$s"
        if (scheme.containsMatchIn(s) && !s.contains(' ')) return s
        return searchUrl(s)
    }

    fun searchUrl(query: String): String = engine.searchUrl + URLEncoder.encode(query.trim(), "UTF-8")

    /** "https://www.kinovod.pro/film/1" → "kinovod.pro" */
    fun displayHost(url: String): String =
        (Uri.parse(url).host ?: url).removePrefix("www.")
}
