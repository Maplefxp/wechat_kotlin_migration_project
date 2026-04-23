package com.sbnkj.assistant.core.db

import android.database.Cursor
import com.sbnkj.assistant.core.model.WechatMessage
import net.zetetic.database.sqlcipher.SQLiteDatabase

class WechatMessageQueries {
    private val selectFields = """
        msgId,msgSvrId,type,status,isSend,isShowTimer,createTime,talker,content,imgPath,
        reserved,lvbuffer,transContent,transBrandWording,talkerId,bizClientMsgId,bizChatId,
        bizChatUserId,msgSeq,flag,solitaireFoldInfo
    """.trimIndent().replace("\n", " ")

    // fun queryIncremental(
    //     db: SQLiteDatabase,
    //     oldMsgId: Long,
    //     createTime: Long = 0L,
    //     limit: Int = 300,
    // ): List<WechatMessage> {
    //     val sql = when {
    //         oldMsgId == 0L -> "select $selectFields from message ORDER BY msgId DESC limit 1"
    //         createTime > 0L -> "select $selectFields from message where msgId > ? AND createTime >= ? ORDER BY msgId ASC limit ?"
    //         else -> "select $selectFields from message where msgId > ? ORDER BY msgId ASC limit ?"
    //     }
    //     val args = when {
    //         oldMsgId == 0L -> emptyArray()
    //         createTime > 0L -> arrayOf(oldMsgId.toString(), createTime.toString(), limit.toString())
    //         else -> arrayOf(oldMsgId.toString(), limit.toString())
    //     }
    //     return db.rawQuery(sql, args).useCursor(::mapMessages)
    // }

    fun queryIncremental(
        db: SQLiteDatabase,
        oldMsgId: Long,
        createTime: Long = 0L,
        limit: Int = 300,
    ): List<WechatMessage> {
        val sql = when {
            oldMsgId == 0L -> "select $selectFields from message ORDER BY msgId ASC limit ?"
            createTime > 0L -> "select $selectFields from message where msgId > ? AND createTime >= ? ORDER BY msgId ASC limit ?"
            else -> "select $selectFields from message where msgId > ? ORDER BY msgId ASC limit ?"
        }
        val args = when {
            oldMsgId == 0L -> arrayOf(limit.toString())
            createTime > 0L -> arrayOf(oldMsgId.toString(), createTime.toString(), limit.toString())
            else -> arrayOf(oldMsgId.toString(), limit.toString())
        }
        return db.rawQuery(sql, args).useCursor(::mapMessages)
    }

    fun queryByMsgSvrId(db: SQLiteDatabase, msgSvrId: String): List<WechatMessage> {
        return db.rawQuery(
            "select * from message where msgSvrId = ? ORDER BY msgId DESC",
            arrayOf(msgSvrId)
        )
            .useCursor(::mapMessages)
    }

    fun queryByCreateTime(db: SQLiteDatabase, createTime: String): List<WechatMessage> {
        return db.rawQuery(
            "select * from message where createTime = ? ORDER BY msgId DESC",
            arrayOf(createTime)
        )
            .useCursor(::mapMessages)
    }

    fun queryByTimeRange(
        db: SQLiteDatabase,
        from: Long,
        to: Long,
        limit: Int = 300
    ): List<WechatMessage> {
        return db.rawQuery(
            "select * from message where createTime between ? and ? AND talker NOT LIKE '%@chatroom' AND talker NOT LIKE '%@im.chatroom' limit 0, ?",
            arrayOf(from.toString(), to.toString(), limit.toString())
        ).useCursor(::mapMessages)
    }

    private fun mapMessages(cursor: Cursor): List<WechatMessage> {
        val result = mutableListOf<WechatMessage>()
        while (cursor.moveToNext()) {
            result += WechatMessage(
                msgId = cursor.getLong(cursor.getColumnIndexOrThrow("msgId")),
                msgSvrId = cursor.getLong(cursor.getColumnIndexOrThrow("msgSvrId")),
                type = cursor.getInt(cursor.getColumnIndexOrThrow("type")),
                status = cursor.getInt(cursor.getColumnIndexOrThrow("status")),
                isSend = cursor.getInt(cursor.getColumnIndexOrThrow("isSend")),
                isShowTimer = cursor.getInt(cursor.getColumnIndexOrThrow("isShowTimer")),
                createTime = cursor.getLong(cursor.getColumnIndexOrThrow("createTime")),
                talker = cursor.getStringOrNull("talker"),
                content = cursor.getStringOrNull("content"),
                imgPath = cursor.getStringOrNull("imgPath"),
                reserved = cursor.getStringOrNull("reserved"),
                lvbuffer = cursor.getBlobOrNull("lvbuffer"),
                transContent = cursor.getStringOrNull("transContent"),
                transBrandWording = cursor.getStringOrNull("transBrandWording"),
                talkerId = cursor.getIntOrZero("talkerId"),
                bizClientMsgId = cursor.getStringOrNull("bizClientMsgId"),
                bizChatId = cursor.getIntOrZero("bizChatId"),
                bizChatUserId = cursor.getStringOrNull("bizChatUserId"),
                msgSeq = cursor.getIntOrZero("msgSeq"),
                flag = cursor.getIntOrZero("flag"),
                solitaireFoldInfo = cursor.getBlobOrNull("solitaireFoldInfo"),
            )
        }
        return result
    }
}

private inline fun <T> Cursor.useCursor(block: (Cursor) -> T): T = use { block(it) }

private fun Cursor.getStringOrNull(column: String): String? =
    if (getColumnIndex(column) >= 0 && !isNull(getColumnIndexOrThrow(column))) getString(
        getColumnIndexOrThrow(column)
    ) else null

private fun Cursor.getBlobOrNull(column: String): ByteArray? =
    if (getColumnIndex(column) >= 0 && !isNull(getColumnIndexOrThrow(column))) getBlob(
        getColumnIndexOrThrow(column)
    ) else null

private fun Cursor.getIntOrZero(column: String): Int =
    if (getColumnIndex(column) >= 0 && !isNull(getColumnIndexOrThrow(column))) getInt(
        getColumnIndexOrThrow(column)
    ) else 0
