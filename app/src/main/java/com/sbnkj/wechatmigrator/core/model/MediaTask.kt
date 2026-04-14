package com.sbnkj.wechatmigrator.core.model

enum class MediaKind {
    IMAGE,
    VOICE,
    VIDEO,
    DOCUMENT,
    EMOJI,
    VOIP,
}

data class MediaTask(
    val slot: WechatUserSlot,
    val msgId: Long,
    val msgSvrId: String,
    val msgType: Int,
    val msgSubType: Int,
    val kind: MediaKind,
    val srcPath: String,
    val outPath: String,
    val srcRefPath: String? = null,
    val outRefPath: String? = null,
    val requestId: Int,
    val filename: String,
    val md5: String? = null,
)
