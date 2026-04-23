package com.sbnkj.assistant.core.sync

import android.content.Context
import android.util.Log
import com.sbnkj.assistant.core.backup.BackupGateway
import com.sbnkj.assistant.core.backup.WechatFixedBackupCoordinator
import com.sbnkj.assistant.core.classify.MessageClass
import com.sbnkj.assistant.core.classify.WechatMessageClassifier
import com.sbnkj.assistant.core.db.LocalMirrorOpenHelper
import com.sbnkj.assistant.core.db.WechatDatabaseOpener
import com.sbnkj.assistant.core.db.WechatMessageQueries
import com.sbnkj.assistant.core.media.WechatMediaTaskBuilder
import com.sbnkj.assistant.core.model.WechatUserSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WechatFullSyncCoordinator(
    private val context: Context,
    private val backupGateway: BackupGateway,
    private val dbOpener: WechatDatabaseOpener = WechatDatabaseOpener(),
    private val messageQueries: WechatMessageQueries = WechatMessageQueries(),
    private val classifier: WechatMessageClassifier = WechatMessageClassifier(),
    private val mediaTaskBuilder: WechatMediaTaskBuilder = WechatMediaTaskBuilder(),
) {

    companion object {
        private const val TAG = "WechatFullSyncCoordinator"
    }

    suspend fun syncOnce(
        slot: WechatUserSlot,
        oldMsgId: Long = 0L,
        createTime: Long = 0L,
    ): SyncResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "========== 开始同步流程 ==========")

        val mirror = LocalMirrorOpenHelper(context)
        Log.d(TAG, "✅ 本地镜像数据库初始化完成")

        val backupCoordinator = WechatFixedBackupCoordinator(backupGateway)
        Log.d(TAG, "📦 开始备份微信文件...")

        val plan = backupCoordinator.runFixedBackup(slot)
        Log.d(TAG, "✅ 备份完成，accountDir=${plan.identity.accountDir}")

        val enDb = plan.enMicroMsgJobs.last().dst

        Log.d(TAG, "========== 检查备份文件 ==========")
        Log.d(TAG, "EnMicroMsg.db: ${enDb.absolutePath}")
        Log.d(TAG, "文件大小: ${enDb.length()} bytes")
        Log.d(TAG, "文件存在: ${enDb.exists()}")

        // 检查 SHM 和 WAL 文件
        val shmFile = File(enDb.parent, "${enDb.name}-shm")
        val walFile = File(enDb.parent, "${enDb.name}-wal")
        Log.d(TAG, "SHM 文件: ${shmFile.exists()}, 大小: ${shmFile.length()} bytes")
        Log.d(TAG, "WAL 文件: ${walFile.exists()}, 大小: ${walFile.length()} bytes")
        Log.d(TAG, "DB Key: ${plan.identity.dbKey}")
        Log.d(TAG, "IMEI + UIN: ${plan.identity.imei} + ${plan.identity.uin}")
        Log.d(TAG, "Account Dir: ${plan.identity.accountDir}")
        Log.d(TAG, "====================================")

        // 尝试打开加密数据库
        Log.d(TAG, "🔐 开始打开加密数据库...")
        val db = dbOpener.openEncrypted(enDb, plan.identity.dbKey)
        Log.d(TAG, "✅ 数据库打开成功，准备查询消息")

        db.use { database ->
            Log.d(TAG, "📊 开始查询消息 (oldMsgId=$oldMsgId, createTime=$createTime)")

            val startTime = System.currentTimeMillis()
            val messages = messageQueries.queryIncremental(database, oldMsgId, createTime)
            val queryTime = System.currentTimeMillis() - startTime

            Log.d(TAG, "✅ 查询完成：耗时 ${queryTime}ms, 消息数 ${messages.size}")

            var mediaCount = 0
            Log.d(TAG, "🔄 开始处理消息并生成媒体任务...")

            messages.forEachIndexed { index, message ->
                Log.d(TAG, "📩 查询到第 ${index + 1} 条消息: $message")
                mirror.insertOrReplaceMessage(message)
                val classification = classifier.classify(message)

                if (classification !is MessageClass.Text && classification !is MessageClass.Ignore) {
                    val tasks =
                        mediaTaskBuilder.build(plan.paths, plan.identity, message, classification)
                    mediaCount += tasks.size
                    mirror.insertMediaTasks(tasks)

                    if (index % 50 == 0) {
                        Log.d(
                            TAG,
                            "  处理进度: ${index + 1}/${messages.size}, 媒体任务: $mediaCount"
                        )
                    }
                }
            }

            Log.d(TAG, "✅ 所有消息处理完成")

            val result = SyncResult(
                slot = slot,
                accountDir = plan.identity.accountDir,
                dbKey = plan.identity.dbKey,
                messageCount = messages.size,
                mediaTaskCount = mediaCount,
            )

            Log.d(TAG, "========== 同步完成 ==========")
            Log.d(
                TAG,
                "📈 结果: 消息 ${result.messageCount} 条, 媒体任务 ${result.mediaTaskCount} 条"
            )
            Log.d(TAG, "================================")

            result
        }
    }

}

data class SyncResult(
    val slot: WechatUserSlot,
    val accountDir: String,
    val dbKey: String,
    val messageCount: Int,
    val mediaTaskCount: Int,
)
