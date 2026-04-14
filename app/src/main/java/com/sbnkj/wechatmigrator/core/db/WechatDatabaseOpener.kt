package com.sbnkj.wechatmigrator.core.db

import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

class WechatDatabaseOpener {
    fun openEncrypted(file: File, password: String): SQLiteDatabase {
        require(file.exists()) { "数据库不存在: ${file.absolutePath}" }
        SqlCipherLoader.load()
        return SQLiteDatabase.openOrCreateDatabase(file, password, null, null, null)
    }

    fun openMaybePlain(file: File, password: String?): SQLiteDatabase {
        require(file.exists()) { "数据库不存在: ${file.absolutePath}" }
        SqlCipherLoader.load()
        return SQLiteDatabase.openOrCreateDatabase(file, password.orEmpty(), null, null, null)
    }
}
