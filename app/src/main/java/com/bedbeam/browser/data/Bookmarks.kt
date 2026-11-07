package com.bedbeam.browser.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Bookmark(val name: String, val url: String)

/** Start-screen bookmarks, kept in order in a small JSON file. */
class Bookmarks(context: Context) {
    private val file = File(context.filesDir, "bookmarks.json")
    private val items = mutableListOf<Bookmark>()

    init {
        load()
    }

    fun all(): List<Bookmark> = items

    fun add(b: Bookmark) {
        items += b
        save()
    }

    fun update(index: Int, b: Bookmark) {
        if (index in items.indices) {
            items[index] = b
            save()
        }
    }

    fun remove(index: Int) {
        if (index in items.indices) {
            items.removeAt(index)
            save()
        }
    }

    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val b = items.removeAt(from)
        items.add(to, b)
        save()
    }

    private fun load() {
        if (!file.exists()) {
            items += DEFAULTS
            save()
            return
        }
        try {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items += Bookmark(o.optString("name"), o.optString("url"))
            }
        } catch (e: Exception) {
            items.clear()
            items += DEFAULTS
        }
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        AtomicFile.write(file, arr.toString())
    }

    companion object {
        val DEFAULTS = listOf(
            Bookmark("Kinovod", "https://kinovod.pro"),
            Bookmark("YouTube", "https://m.youtube.com"),
            Bookmark("Wikipedia", "https://www.wikipedia.org"),
            Bookmark("Google", "https://www.google.com"),
        )
    }
}
