package com.bedbeam.browser.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class Visit(val url: String, val title: String)

/** The last [MAX] visited pages, newest first, for address-bar suggestions. */
class History(context: Context) {
    private val file = File(context.filesDir, "history.json")
    private val items = mutableListOf<Visit>()
    private val io = Executors.newSingleThreadExecutor()

    init {
        try {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items += Visit(o.optString("url"), o.optString("title"))
                }
            }
        } catch (e: Exception) {
            items.clear()
        }
    }

    fun all(): List<Visit> = items

    fun add(url: String, title: String?) {
        if (!url.startsWith("http")) return
        items.removeAll { it.url == url }
        items.add(0, Visit(url, title?.takeIf { it.isNotBlank() } ?: url))
        while (items.size > MAX) items.removeAt(items.lastIndex)
        val json = JSONArray().also { arr ->
            items.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title)) }
        }.toString()
        io.execute { AtomicFile.write(file, json) }
    }

    companion object {
        const val MAX = 100
    }
}
