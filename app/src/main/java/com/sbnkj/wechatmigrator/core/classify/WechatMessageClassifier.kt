package com.sbnkj.wechatmigrator.core.classify

import com.sbnkj.wechatmigrator.core.model.WechatMessage
import com.sbnkj.wechatmigrator.core.util.WechatXmlUtils

sealed interface MessageClass {
    data object Text : MessageClass
    data object Image : MessageClass
    data object Voice : MessageClass
    data class Video(val big: Boolean) : MessageClass
    data object Emoji : MessageClass
    data object Voip : MessageClass
    data class Document(val fileName: String?, val big: Boolean) : MessageClass
    data object Ignore : MessageClass
}

class WechatMessageClassifier(
    private val fileUploadLimitMax: Long = 52_428_800L,
) {
    fun classify(message: WechatMessage): MessageClass = when (message.type) {
        1 -> MessageClass.Text
        3 -> MessageClass.Image
        34 -> MessageClass.Voice
        43 -> MessageClass.Video(big = (WechatXmlUtils.extractLengthFromReserved(message.reserved) ?: 0L) > fileUploadLimitMax)
        47 -> MessageClass.Emoji
        50, 64 -> MessageClass.Voip
        1090519089 -> {
            val name = WechatXmlUtils.extractAttachmentTitle(message.content)
            val big = (WechatXmlUtils.extractLengthFromReserved(message.reserved) ?: 0L) > fileUploadLimitMax
            MessageClass.Document(name, big)
        }
        else -> MessageClass.Ignore
    }
}
