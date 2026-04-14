package com.sbnkj.wechatmigrator.core.media

import com.sbnkj.wechatmigrator.core.classify.MessageClass
import com.sbnkj.wechatmigrator.core.config.WechatBackupPaths
import com.sbnkj.wechatmigrator.core.model.MediaKind
import com.sbnkj.wechatmigrator.core.model.MediaTask
import com.sbnkj.wechatmigrator.core.model.WechatIdentity
import com.sbnkj.wechatmigrator.core.model.WechatMessage
import com.sbnkj.wechatmigrator.core.util.HashUtils
import org.json.JSONObject

class WechatMediaTaskBuilder {

    fun build(
        paths: WechatBackupPaths,
        identity: WechatIdentity,
        message: WechatMessage,
        classification: MessageClass,
    ): List<MediaTask> = when (classification) {
        MessageClass.Text, MessageClass.Ignore -> emptyList()
        MessageClass.Image -> resolveImageCandidates(paths, identity, message)
        MessageClass.Voice -> buildVoiceTask(paths, identity, message)
        is MessageClass.Video -> buildVideoTask(paths, identity, message)
        MessageClass.Emoji -> buildEmojiTask(paths, identity, message)
        MessageClass.Voip -> buildVoipTask(identity, message)
        is MessageClass.Document -> buildDocumentTask(paths, identity, message, classification.fileName)
    }

    private fun resolveImageCandidates(
        paths: WechatBackupPaths,
        identity: WechatIdentity,
        message: WechatMessage,
    ): List<MediaTask> {
        val imgPath = message.imgPath ?: return emptyList()
        // 这里只做第一层占位：原包的图片定位逻辑更复杂，后续要结合 ImgInfo2 / ImageMsgHandle 继续补完。
        val talkerHash = HashUtils.md5(message.talker.orEmpty())
        val tail = message.normalizedMsgSvrId().takeLast(4)
        val subPath = "message/media/image/$talkerHash/$tail/${message.normalizedMsgSvrId()}_m"
        return listOf(
            MediaTask(
                slot = identity.slot,
                msgId = message.msgId,
                msgSvrId = message.normalizedMsgSvrId(),
                msgType = message.type,
                msgSubType = 26,
                kind = MediaKind.IMAGE,
                srcPath = paths.wechatVoicePath(identity.accountDir, subPath),
                outPath = paths.outVoicePath(identity.accountDir, subPath),
                requestId = 0x200000 or message.msgId.toInt(),
                filename = imgPath,
            )
        )
    }

    private fun buildVoiceTask(
        paths: WechatBackupPaths,
        identity: WechatIdentity,
        message: WechatMessage,
    ): List<MediaTask> {
        val imgPath = message.imgPath ?: return emptyList()
        val voiceMd5 = HashUtils.md5(imgPath)
        val subPath = "voice2/${voiceMd5.substring(0, 2)}/${voiceMd5.substring(2, 4)}/msg_${imgPath}.amr"
        return listOf(
            MediaTask(
                slot = identity.slot,
                msgId = message.msgId,
                msgSvrId = message.normalizedMsgSvrId(),
                msgType = message.type,
                msgSubType = -1,
                kind = MediaKind.VOICE,
                srcPath = paths.wechatVoicePath(identity.accountDir, subPath),
                outPath = paths.outVoicePath(identity.accountDir, subPath),
                requestId = (if (identity.slot.isClone) 0x10000000 else 0x00800000) or message.msgId.toInt(),
                filename = "msg_${imgPath}.amr",
            )
        )
    }

    private fun buildVideoTask(
        paths: WechatBackupPaths,
        identity: WechatIdentity,
        message: WechatMessage,
    ): List<MediaTask> {
        val imgPath = message.imgPath ?: return emptyList()
        val subPath = "video/$imgPath.mp4"
        val srcPath = paths.wechatVoicePath(identity.accountDir, subPath)
        val outPath = paths.outVoicePath(identity.accountDir, subPath)
        return listOf(
            MediaTask(
                slot = identity.slot,
                msgId = message.msgId,
                msgSvrId = message.normalizedMsgSvrId(),
                msgType = message.type,
                msgSubType = 0,
                kind = MediaKind.VIDEO,
                srcPath = srcPath,
                outPath = outPath,
                srcRefPath = "$srcPath⌖",
                outRefPath = "$outPath⌖",
                requestId = (if (identity.slot.isClone) 0x20000000 else 0x01000000) or message.msgId.toInt(),
                filename = "$imgPath.mp4",
            )
        )
    }

    private fun buildDocumentTask(
        paths: WechatBackupPaths,
        identity: WechatIdentity,
        message: WechatMessage,
        fileName: String?,
    ): List<MediaTask> {
        val title = fileName ?: return emptyList()
        val subPath = "attachment/$title"
        val srcPath = paths.wechatVoicePath(identity.accountDir, subPath)
        val outPath = paths.outVoicePath(identity.accountDir, subPath)
        return listOf(
            MediaTask(
                slot = identity.slot,
                msgId = message.msgId,
                msgSvrId = message.normalizedMsgSvrId(),
                msgType = message.type,
                msgSubType = 0,
                kind = MediaKind.DOCUMENT,
                srcPath = srcPath,
                outPath = outPath,
                srcRefPath = "$srcPath⌖",
                outRefPath = "$outPath⌖",
                requestId = (if (identity.slot.isClone) 0x20000000 else 0x01000000) or message.msgId.toInt(),
                filename = title,
            )
        )
    }

    private fun buildEmojiTask(
        paths: WechatBackupPaths,
        identity: WechatIdentity,
        message: WechatMessage,
    ): List<MediaTask> {
        val name = message.imgPath ?: message.normalizedMsgSvrId()
        val subPath = "emoji/$name"
        return listOf(
            MediaTask(
                slot = identity.slot,
                msgId = message.msgId,
                msgSvrId = message.normalizedMsgSvrId(),
                msgType = message.type,
                msgSubType = 0,
                kind = MediaKind.EMOJI,
                srcPath = paths.wechatVoicePath(identity.accountDir, subPath),
                outPath = paths.outVoicePath(identity.accountDir, subPath),
                requestId = (if (identity.slot.isClone) 0x04000000 else 0x02000000) or message.msgId.toInt(),
                filename = name,
            )
        )
    }

    private fun buildVoipTask(identity: WechatIdentity, message: WechatMessage): List<MediaTask> {
        val content = message.content ?: return emptyList()
        val fileName = runCatching { JSONObject(content).optString("fileName") }.getOrNull()
            ?.takeIf { it.endsWith(".mp3", ignoreCase = true) }
            ?: return emptyList()

        val wechatFile = "/storage/emulated/0/backup_wechat/wechatFile/$fileName"
        val voipFile = "/storage/emulated/0/backup_voip/$fileName"
        return listOf(
            MediaTask(
                slot = identity.slot,
                msgId = message.msgId,
                msgSvrId = message.createTime.toString(),
                msgType = message.type,
                msgSubType = 5,
                kind = MediaKind.VOIP,
                srcPath = wechatFile,
                outPath = wechatFile,
                srcRefPath = voipFile,
                outRefPath = voipFile,
                requestId = 0,
                filename = fileName,
            )
        )
    }
}
