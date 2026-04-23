package com.sbnkj.assistant.core.upload

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class FileUploadDeduplicator(context: Context) {
    private val uploading = ConcurrentHashMap.newKeySet<String>()
    private val cacheDir = File(context.filesDir, "upload_cache")
    private val cacheFile = File(cacheDir, "uploaded.txt")

    init {
        cacheDir.mkdirs()
        if (!cacheFile.exists()) cacheFile.createNewFile()
    }

    @Synchronized
    fun shouldSkip(md5: String): Boolean {
        if (md5.isBlank()) return false
        if (uploading.contains(md5)) return true
        if (cacheFile.useLines { lines -> lines.any { it.trim() == md5 } }) return true
        uploading += md5
        return false
    }

    @Synchronized
    fun markUploaded(md5: String) {
        if (md5.isBlank()) return
        if (!cacheFile.useLines { lines -> lines.any { it.trim() == md5 } }) {
            cacheFile.appendText(md5 + "")
        }
        uploading -= md5
    }

    @Synchronized
    fun markFailed(md5: String) {
        if (md5.isBlank()) return
        uploading -= md5
    }
}
