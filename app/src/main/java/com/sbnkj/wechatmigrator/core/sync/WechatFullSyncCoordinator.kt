package com.sbnkj.wechatmigrator.core.sync

import android.content.Context
import com.sbnkj.wechatmigrator.core.backup.BackupGateway
import com.sbnkj.wechatmigrator.core.backup.WechatFixedBackupCoordinator
import com.sbnkj.wechatmigrator.core.classify.MessageClass
import com.sbnkj.wechatmigrator.core.classify.WechatMessageClassifier
import com.sbnkj.wechatmigrator.core.db.LocalMirrorOpenHelper
import com.sbnkj.wechatmigrator.core.db.WechatDatabaseOpener
import com.sbnkj.wechatmigrator.core.db.WechatMessageQueries
import com.sbnkj.wechatmigrator.core.media.WechatMediaTaskBuilder
import com.sbnkj.wechatmigrator.core.model.WechatUserSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WechatFullSyncCoordinator(
    private val context: Context,
    private val backupGateway: BackupGateway,
    private val dbOpener: WechatDatabaseOpener = WechatDatabaseOpener(),
    private val messageQueries: WechatMessageQueries = WechatMessageQueries(),
    private val classifier: WechatMessageClassifier = WechatMessageClassifier(),
    private val mediaTaskBuilder: WechatMediaTaskBuilder = WechatMediaTaskBuilder(),
) {
    suspend fun syncOnce(
        slot: WechatUserSlot,
        oldMsgId: Long = 0L,
        createTime: Long = 0L,
    ): SyncResult = withContext(Dispatchers.IO) {
        val mirror = LocalMirrorOpenHelper(context)
        val backupCoordinator = WechatFixedBackupCoordinator(backupGateway)
        val plan = backupCoordinator.runFixedBackup(slot)
        val enDb = plan.enMicroMsgJobs.last().dst
        val db = dbOpener.openEncrypted(enDb, plan.identity.dbKey)
        db.use {
            val messages = messageQueries.queryIncremental(it, oldMsgId, createTime)
            var mediaCount = 0
            messages.forEach { message ->
                mirror.insertOrReplaceMessage(message)
                val classification = classifier.classify(message)
                if (classification !is MessageClass.Text && classification !is MessageClass.Ignore) {
                    val tasks = mediaTaskBuilder.build(plan.paths, plan.identity, message, classification)
                    mediaCount += tasks.size
                    mirror.insertMediaTasks(tasks)
                }
            }
            SyncResult(
                slot = slot,
                accountDir = plan.identity.accountDir,
                dbKey = plan.identity.dbKey,
                messageCount = messages.size,
                mediaTaskCount = mediaCount,
            )
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
