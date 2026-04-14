package com.sbnkj.wechatmigrator.core.util

import java.io.File
import java.io.FileInputStream
import java.math.BigInteger
import java.security.MessageDigest

object HashUtils {
    fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray())
        return BigInteger(1, digest).toString(16).padStart(32, '0')
    }

    fun md5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
            }
        }
        return BigInteger(1, md.digest()).toString(16).padStart(32, '0')
    }
}
