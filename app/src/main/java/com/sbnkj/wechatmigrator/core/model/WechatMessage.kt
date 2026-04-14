package com.sbnkj.wechatmigrator.core.model

data class WechatMessage(
    val msgId: Long,
    val msgSvrId: Long,
    val type: Int,
    val status: Int,
    val isSend: Int,
    val isShowTimer: Int,
    val createTime: Long,
    val talker: String?,
    val content: String?,
    val imgPath: String?,
    val reserved: String?,
    val lvbuffer: ByteArray?,
    val transContent: String?,
    val transBrandWording: String?,
    val talkerId: Int,
    val bizClientMsgId: String?,
    val bizChatId: Int,
    val bizChatUserId: String?,
    val msgSeq: Int,
    val flag: Int,
    val solitaireFoldInfo: ByteArray?,
    val bigFileFlag: Int = 0,
) {
    fun normalizedMsgSvrId(): String {
        val normalized = if (type == 50 || type == 64 || isSend == 1) createTime else msgSvrId
        return if (normalized > 0) normalized.toString() else "P-$createTime"
    }
}
