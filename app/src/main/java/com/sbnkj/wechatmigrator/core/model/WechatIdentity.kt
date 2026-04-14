package com.sbnkj.wechatmigrator.core.model

data class WechatIdentity(
    val slot: WechatUserSlot,
    val imei: String,
    val uin: String,
    val dbKey: String,
    val accountDir: String,
    val accountMapping: String? = null
)
