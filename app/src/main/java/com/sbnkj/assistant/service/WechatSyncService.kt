package com.sbnkj.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sbnkj.assistant.core.backup.OppoBackupGateway
import com.sbnkj.assistant.core.model.WechatUserSlot
import com.sbnkj.assistant.core.sync.WechatFullSyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WechatSyncService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(1001, buildNotification("等待执行"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BACKUP -> {
                val slot = WechatUserSlot.fromUserId(intent.getIntExtra(EXTRA_SLOT_ID, 0))
                updateNotification("开始备份 ${if (slot.isClone) "分身微信" else "主微信"}")
                serviceScope.launch {
                    val coordinator = WechatFullSyncCoordinator(
                        context = applicationContext,
                        backupGateway = OppoBackupGateway(applicationContext),
                    )
                    runCatching { coordinator.syncOnce(slot) }
                        .onSuccess {
                            updateNotification("完成：消息 ${it.messageCount} 条，媒体任务 ${it.mediaTaskCount} 条")
                            stopSelf()
                        }
                        .onFailure {
                            updateNotification("失败：${it.message}")
                            stopSelf()
                        }
                }
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "wechat_sync", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("WeChat Kotlin Sync")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(1001, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "wechat_sync_channel"
        // private const val ACTION_START_BACKUP = "com.sbnkj.wechatmigrator.action.START_BACKUP"
        // private const val ACTION_STOP = "com.sbnkj.wechatmigrator.action.STOP"
        private const val ACTION_START_BACKUP = "com.sbnkj.assistant.action.START_BACKUP"
        private const val ACTION_STOP = "com.sbnkj.assistant.action.STOP"
        private const val EXTRA_SLOT_ID = "slot_id"

        fun startBackupIntent(context: Context, slot: WechatUserSlot): Intent =
            Intent(context, WechatSyncService::class.java).apply {
                action = ACTION_START_BACKUP
                putExtra(EXTRA_SLOT_ID, slot.userId)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, WechatSyncService::class.java).apply { action = ACTION_STOP }
    }
}
