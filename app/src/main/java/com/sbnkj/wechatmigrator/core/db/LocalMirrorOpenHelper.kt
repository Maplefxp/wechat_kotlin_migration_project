package com.sbnkj.wechatmigrator.core.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.sbnkj.wechatmigrator.core.model.MediaTask
import com.sbnkj.wechatmigrator.core.model.WechatMessage

class LocalMirrorOpenHelper(context: Context) : SQLiteOpenHelper(context, "wechat_migration_local.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS message (msgId LONG PRIMARY KEY, msgSvrId TEXT, type INTEGER, status INTEGER, isSend INTEGER, createTime INTEGER, talker TEXT, content TEXT, transContent TEXT, talkerId INTEGER, flag INTEGER, imgPath TEXT, bigFileFlag INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS WxFileIndex3 (msgId LONG, msgSvrId TEXT, msgType INTEGER, msgSubType INTEGER, kind TEXT, srcPath TEXT, outPath TEXT, srcRefPath TEXT, outRefPath TEXT, requestId INTEGER, filename TEXT, md5 TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insertOrReplaceMessage(message: WechatMessage) {
        writableDatabase.insertWithOnConflict("message", null, ContentValues().apply {
            put("msgId", message.msgId)
            put("msgSvrId", message.normalizedMsgSvrId())
            put("type", message.type)
            put("status", message.status)
            put("isSend", message.isSend)
            put("createTime", message.createTime)
            put("talker", message.talker)
            put("content", message.content)
            put("transContent", message.transContent)
            put("talkerId", message.talkerId)
            put("flag", message.flag)
            put("imgPath", message.imgPath)
            put("bigFileFlag", message.bigFileFlag)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertMediaTasks(tasks: List<MediaTask>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            tasks.forEach { task ->
                db.insert("WxFileIndex3", null, ContentValues().apply {
                    put("msgId", task.msgId)
                    put("msgSvrId", task.msgSvrId)
                    put("msgType", task.msgType)
                    put("msgSubType", task.msgSubType)
                    put("kind", task.kind.name)
                    put("srcPath", task.srcPath)
                    put("outPath", task.outPath)
                    put("srcRefPath", task.srcRefPath)
                    put("outRefPath", task.outRefPath)
                    put("requestId", task.requestId)
                    put("filename", task.filename)
                    put("md5", task.md5)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
