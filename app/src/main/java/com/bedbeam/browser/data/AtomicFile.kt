package com.bedbeam.browser.data

import java.io.File

/** Writes via a temp file + rename, so a crash mid-write never leaves a half-written file. */
object AtomicFile {
    @Synchronized
    fun write(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
