package com.sbnkj.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sbnkj.assistant.core.backup.WechatBackupStateMachine

class WechatBackupCallbackReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BackupCallbackReceiver"

        // OPPO SDK 备份结果广播 Action
        private const val ACTION_BACKUP_SUCCESS = "action.backup.app.data.success"
        private const val ACTION_BACKUP_FAILED = "action.backup.app.data.failed"
    }

    private val stateMachine = WechatBackupStateMachine()

    // override fun onReceive(context: Context, intent: Intent) {
    //     val requestId = intent.getIntExtra("requestId", Int.MIN_VALUE)
    //     if (requestId == Int.MIN_VALUE) return
    //     stateMachine.nextForRequestId(requestId)
    //     // 这里保留给你后续接真实厂商备份回调：
    //     // systemInfo -> account.mapping -> EnMicroMsg -> SnsMicroMsg -> parse/upload
    // }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        when (action) {
            ACTION_BACKUP_SUCCESS -> {
                handleBackupSuccess(context, intent)
            }
            ACTION_BACKUP_FAILED -> {
                handleBackupFailed(context, intent)
            }
            else -> {
                // 兼容旧的 requestId 方式
                val requestId = intent.getIntExtra("requestId", Int.MIN_VALUE)
                if (requestId != Int.MIN_VALUE) {
                    stateMachine.nextForRequestId(requestId)
                }
            }
        }
    }

    private fun handleBackupSuccess(context: Context, intent: Intent) {
        val requestId = intent.getIntExtra("requestId", -1)
        Log.d(TAG, "收到备份成功回调: requestId=$requestId")

        if (requestId != -1) {
            stateMachine.nextForRequestId(requestId)
        }

        // TODO: 这里可以添加成功后的处理逻辑
        // 例如：通知用户、更新UI、触发下一步操作等
    }

    private fun handleBackupFailed(context: Context, intent: Intent) {
        val requestId = intent.getIntExtra("requestId", -1)
        val errorMsg = intent.getStringExtra("errorMsg") ?: "未知错误"
        Log.e(TAG, "收到备份失败回调: requestId=$requestId, error=$errorMsg")

        if (requestId != -1) {
            // TODO: 处理失败状态
            // stateMachine.markFailed(requestId)
            Log.e(TAG, "失败状态: requestId=$requestId")
        }

        // TODO: 这里可以添加失败后的处理逻辑
        // 例如：重试、通知用户、记录日志等
        Log.e(TAG, "失败后的处理逻辑")
    }




}
