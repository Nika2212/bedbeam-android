package com.bedbeam.browser.adblock

import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream

/**
 * Domain blocklist stored as sorted 64-bit hashes: ~8 bytes per domain (≈1.6 MB for 200k)
 * instead of 50+ bytes per String in a HashSet, and a lookup is a binary search (~1 µs).
 */
class FilterList(private val block: LongArray, private val allow: LongArray) {

    val size get() = block.size

    /** True if [host] or any parent domain is blocked and none is explicitly allowed. */
    fun isBlocked(host: String): Boolean {
        if (block.isEmpty()) return false
        var h = host.lowercase().trimEnd('.')
        if (h.startsWith("www.")) h = h.substring(4)
        // Allow rules win: check every suffix (a.b.c.com, b.c.com, c.com) against both lists.
        var blocked = false
        var start = 0
        while (true) {
            val hash = fnv1a(h, start)
            if (allow.isNotEmpty() && allow.binarySearch(hash) >= 0) return false
            if (!blocked && block.binarySearch(hash) >= 0) blocked = true
            val dot = h.indexOf('.', start)
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) break // stop at the registrable 2-label domain
            start = dot + 1
        }
        return blocked
    }

    fun save(file: File) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(block.size)
            out.writeInt(allow.size)
            block.forEach { out.writeLong(it) }
            allow.forEach { out.writeLong(it) }
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        private const val MAGIC = 0x42424C31 // "BBL1"
        val EMPTY = FilterList(LongArray(0), LongArray(0))

        fun load(file: File): FilterList? = try {
            DataInputStream(file.inputStream().buffered(1 shl 16)).use { inp ->
                if (inp.readInt() != MAGIC) return null
                // Layout: magic, block count, allow count, block hashes, allow hashes.
                val nBlock = inp.readInt()
                val nAllow = inp.readInt()
                val expected = 12L + (nBlock.toLong() + nAllow) * 8
                if (nBlock < 0 || nAllow < 0 || expected != file.length()) return null
                val b = LongArray(nBlock) { inp.readLong() }
                val a = LongArray(nAllow) { inp.readLong() }
                FilterList(b, a)
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null // a damaged file must never crash the app; it is rebuilt by the next update
        }

        /** Parse adblock-syntax / hosts-format lists, keeping only whole-domain network rules. */
        fun parse(sources: List<InputStream>): FilterList {
            val block = LongBuilder(1 shl 18)
            val allow = LongBuilder(1 shl 10)
            for (src in sources) {
                src.bufferedReader().use { r -> parseInto(r, block, allow) }
            }
            return FilterList(block.sortedUnique(), allow.sortedUnique())
        }

        private fun parseInto(r: BufferedReader, block: LongBuilder, allow: LongBuilder) {
            while (true) {
                val raw = r.readLine() ?: break
                val line = raw.trim()
                if (line.isEmpty() || line[0] == '!' || line[0] == '[' || line[0] == '#') continue
                if (line.contains('#') && (line.contains("##") || line.contains("#@#") || line.contains("#?#") ||
                        line.contains("#$#") || line.contains("#%#"))) continue
                when {
                    line.startsWith("@@||") -> domainRule(line, 4)?.let { allow.add(fnv1a(it, 0)) }
                    line.startsWith("||") -> domainRule(line, 2)?.let { block.add(fnv1a(it, 0)) }
                    line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ") -> {
                        val d = line.substringAfter(' ').trim().substringBefore(' ').substringBefore('#').lowercase()
                        if (isDomain(d) && d != "localhost") block.add(fnv1a(d.removePrefix("www."), 0))
                    }
                }
            }
        }

        /** "||ads.example.com^" or "||ads.example.com^$third-party" → "ads.example.com"; anything narrower → null. */
        private fun domainRule(line: String, prefix: Int): String? {
            val caret = line.indexOf('^', prefix)
            if (caret < 0) return null
            val domain = line.substring(prefix, caret).lowercase()
            if (!isDomain(domain)) return null
            var rest = line.substring(caret + 1)
            if (rest.startsWith("|")) rest = rest.substring(1)
            if (rest.isNotEmpty()) {
                if (!rest.startsWith("$")) return null // has a path part
                val opts = rest.substring(1).split(',')
                if (opts.any { o -> o.startsWith("~") || o.contains('=') || o in NARROWING }) return null
            }
            return domain.removePrefix("www.")
        }

        private val NARROWING = setOf("badfilter", "1p", "first-party", "removeparam", "csp", "redirect",
            "redirect-rule", "replace", "cookie", "header", "stealth", "urlblock", "content", "elemhide",
            "generichide", "specifichide", "jsinject", "extension", "document", "match-case")

        private fun isDomain(d: String): Boolean {
            if (d.length < 3 || d.indexOf('.') <= 0 || d.endsWith('.')) return false
            for (c in d) if (!(c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '-' || c == '_')) return false
            return true
        }

        /** FNV-1a 64-bit over the ASCII chars of [s] from [start]. */
        fun fnv1a(s: String, start: Int): Long {
            var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
            for (i in start until s.length) {
                h = h xor (s[i].code.toLong() and 0xff)
                h *= 0x100000001b3L
            }
            return h
        }
    }

    private class LongBuilder(cap: Int) {
        private var a = LongArray(cap)
        private var n = 0
        fun add(v: Long) {
            if (n == a.size) a = a.copyOf(a.size * 2)
            a[n++] = v
        }
        fun sortedUnique(): LongArray {
            val s = a.copyOf(n)
            s.sort()
            if (s.isEmpty()) return s
            var w = 1
            for (i in 1 until s.size) if (s[i] != s[w - 1]) s[w++] = s[i]
            return s.copyOf(w)
        }
    }
}
