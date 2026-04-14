package com.sbnkj.wechatmigrator.core.config

import com.sbnkj.wechatmigrator.core.model.WechatIdentity
import com.sbnkj.wechatmigrator.core.model.WechatUserSlot
import com.sbnkj.wechatmigrator.core.util.HashUtils
import java.io.File
import java.io.ObjectInputStream

class WechatSystemInfoParser {

    fun parse(slot: WechatUserSlot, cfgFile: File): WechatIdentity {
        require(cfgFile.exists()) { "systemInfo.cfg 不存在: ${cfgFile.absolutePath}" }

        val values = ObjectInputStream(cfgFile.inputStream().buffered()).use { input ->
            @Suppress("UNCHECKED_CAST")
            input.readObject() as? HashMap<Any?, Any?>
                ?: error("systemInfo.cfg 不是有效的序列化 HashMap")
        }

        val imei = values[258]?.toString().takeUnless { it.isNullOrBlank() || it == "null" }
            ?: "1234567890ABCDEF"
        val uin = values[1]?.toString().takeUnless { it.isNullOrBlank() || it == "0" || it == "null" }
            ?: error("微信未登录或 UIN 无效")

        return WechatIdentity(
            slot = slot,
            imei = imei,
            uin = uin,
            dbKey = HashUtils.md5(imei + uin).substring(0, 7),
            accountDir = HashUtils.md5("mm$uin"),
        )
    }

    fun parseAccountMapping(file: File): String? {
        if (!file.exists()) return null
        return file.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() && it != "0" }
    }
}
