package com.bedbeam.browser.adblock

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Built-in ad blocker. Network-level: requests to listed ad/tracker domains are cut before they
 * download. Lists are fetched from AdGuard, compiled to a compact hash array and refreshed weekly.
 * Can be turned off per site.
 */
class AdBlocker(context: Context) {

    private val dir = context.filesDir
    private val listFile = File(dir, "blocklist.bin")
    private val prefs = context.getSharedPreferences("adblock", Context.MODE_PRIVATE)
    private val io = Executors.newSingleThreadExecutor()

    @Volatile
    private var list: FilterList = FilterList.EMPTY

    @Volatile
    private var disabledSites: Set<String> = prefs.getStringSet(KEY_DISABLED, emptySet())!!.toSet()

    val domainCount get() = list.size

    /** When the lists were last downloaded (ms), 0 if never. */
    val updatedAt get() = prefs.getLong(KEY_UPDATED, 0L)

    /** Download fresh lists now; [done] runs on the main thread with success/failure. */
    fun updateNow(done: (Boolean) -> Unit) = io.execute {
        val ok = update()
        android.os.Handler(android.os.Looper.getMainLooper()).post { done(ok) }
    }

    /** Load the compiled list (fast, background) and refresh it if older than a week or missing. */
    fun start() = io.execute {
        FilterList.load(listFile)?.let { list = it }
        Log.i(TAG, "loaded ${list.size} blocked domains")
        val age = System.currentTimeMillis() - prefs.getLong(KEY_UPDATED, 0L)
        if (list.size == 0 || age > UPDATE_EVERY_MS) update()
    }

    fun isEnabledFor(siteHost: String?): Boolean = siteHost == null || site(siteHost) !in disabledSites

    fun setEnabledFor(siteHost: String, enabled: Boolean) {
        val s = site(siteHost)
        disabledSites = if (enabled) disabledSites - s else disabledSites + s
        prefs.edit().putStringSet(KEY_DISABLED, disabledSites).apply()
    }

    /** Sites with ad-block off, as a JS array literal for the ad-box CSS script. */
    fun disabledSitesJson(): String =
        disabledSites.joinToString(",", "[", "]") { "'" + it.replace("'", "") + "'" }

    /** Should a request to [host] be blocked while showing a page from [pageHost]? */
    fun shouldBlock(host: String?, pageHost: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        if (!isEnabledFor(pageHost)) return false
        return list.isBlocked(host)
    }

    private fun update(): Boolean {
        return try {
            val t0 = System.currentTimeMillis()
            val streams = SOURCES.map { url ->
                val c = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "BedBeam")
                }
                if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode} for $url")
                c.inputStream
            }
            val fresh = FilterList.parse(streams)
            if (fresh.size < 1000) throw IllegalStateException("list too small: ${fresh.size}")
            fresh.save(listFile)
            list = fresh
            prefs.edit().putLong(KEY_UPDATED, System.currentTimeMillis()).apply()
            Log.i(TAG, "updated: ${fresh.size} domains in ${System.currentTimeMillis() - t0} ms")
            true
        } catch (e: Exception) {
            Log.w(TAG, "list update failed, keeping ${list.size} domains", e)
            false
        }
    }

    companion object {
        private const val TAG = "BedBeamAdblock"
        private const val KEY_DISABLED = "disabled_sites"
        private const val KEY_UPDATED = "updated_at"
        private const val UPDATE_EVERY_MS = 7L * 24 * 60 * 60 * 1000

        val SOURCES = listOf(
            "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt", // AdGuard DNS filter
            "https://filters.adtidy.org/android/filters/2_optimized.txt", // AdGuard Base
            "https://filters.adtidy.org/android/filters/1_optimized.txt", // AdGuard Russian
        )

        /** Registrable-ish site key: "player.kinovod.pro" → "kinovod.pro". */
        fun site(host: String): String {
            val parts = host.lowercase().removePrefix("www.").split('.')
            return if (parts.size <= 2) parts.joinToString(".") else parts.takeLast(2).joinToString(".")
        }
    }
}
