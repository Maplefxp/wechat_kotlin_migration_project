package com.sbnkj.assistant.core.db

import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import java.io.File

class WechatDatabaseOpener {

    companion object {
        private const val TAG = "WechatDatabaseOpener"
    }

    fun openEncrypted(file: File, password: String): SQLiteDatabase {
        require(file.exists()) { "数据库不存在: ${file.absolutePath}" }

        Log.d(TAG, "========== 打开加密数据库 ==========")
        Log.d(TAG, "文件路径: ${file.absolutePath}")
        Log.d(TAG, "文件大小: ${file.length()} bytes")
        Log.d(TAG, "密码: $password")

        SqlCipherLoader.load()

        // ⚠️ 关键修复：使用 SQLiteDatabaseHook 执行 PRAGMA cipher_migrate
        // 这是为了确保能打开不同版本微信创建的数据库
        val hook = object : SQLiteDatabaseHook {
            override fun preKey(connection: SQLiteConnection) {
                // 密钥设置前的回调（通常为空）
                Log.d(TAG, "preKey: 准备设置密钥")
            }

            override fun postKey(connection: SQLiteConnection) {

                // 密钥设置后的回调
                // 迁移到最新的 cipher 版本，确保兼容性
                // Log.d(TAG, "postKey: 执行 PRAGMA cipher_migrate")
                // database?.rawExecSQL("PRAGMA cipher_migrate;")
                // Log.d(TAG, "postKey: cipher_migrate 完成")
                Log.d(TAG, "postKey: 注入微信专属 SQLCipher 解密参数")
                try {
                    connection.executeForLong("PRAGMA cipher_migrate;", null, null);
                    Log.d(TAG, "postKey: 参数注入完成")
                } catch (e: Exception) {
                    Log.e(TAG, "注入 PRAGMA 失败", e)
                }
            }
        }

        return try {
            val db = SQLiteDatabase.openOrCreateDatabase(
                file,
                password,
                null,
                null,
                hook  // ⚠️ 传入 hook，执行 cipher_migrate
            )
            Log.d(TAG, "✅ 数据库打开成功")
            Log.d(TAG, "=========================================")
            db
        } catch (e: Exception) {
            Log.e(TAG, "❌ 数据库打开失败", e)
            Log.e(TAG, "=========================================")
            throw e
        }
    }

    fun openMaybePlain(file: File, password: String?): SQLiteDatabase {
        require(file.exists()) { "数据库不存在: ${file.absolutePath}" }

        Log.d(TAG, "尝试打开数据库（可能未加密）: ${file.absolutePath}")

        SqlCipherLoader.load()

        // val hook = object : SQLiteDatabaseHook {
        //     override fun preKey(database: SQLiteDatabase?) {
        //         Log.d(TAG, "preKey: 准备设置密钥")
        //     }
        //
        //     override fun postKey(database: SQLiteDatabase?) {
        //         Log.d(TAG, "postKey: 执行 PRAGMA cipher_migrate")
        //         database?.rawExecSQL("PRAGMA cipher_migrate;")
        //     }
        // }
        val hook = object : SQLiteDatabaseHook {
            override fun preKey(connection: SQLiteConnection) {
            }

            override fun postKey(connection: SQLiteConnection) {
                // 如果是尝试打开未加密或旧版库，依然注入微信参数尝试
                try {
                    connection.execute("PRAGMA cipher_page_size = 1024;", null, null)
                    connection.execute("PRAGMA kdf_iter = 4000;", null, null)
                    connection.execute("PRAGMA cipher_use_hmac = OFF;", null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "注入 PRAGMA 失败", e)
                }
            }
        }

        return SQLiteDatabase.openOrCreateDatabase(
            file,
            password.orEmpty(),
            null,
            null,
            hook
        )
    }
}


