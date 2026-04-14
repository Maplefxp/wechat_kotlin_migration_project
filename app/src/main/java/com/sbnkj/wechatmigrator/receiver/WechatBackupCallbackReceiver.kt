package com.sbnkj.wechatmigrator.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sbnkj.wechatmigrator.core.backup.WechatBackupStateMachine

class WechatBackupCallbackReceiver : BroadcastReceiver() {
    private val stateMachine = WechatBackupStateMachine()

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getIntExtra("requestId", Int.MIN_VALUE)
        if (requestId == Int.MIN_VALUE) return
        stateMachine.nextForRequestId(requestId)
        // 这里保留给你后续接真实厂商备份回调：
        // systemInfo -> account.mapping -> EnMicroMsg -> SnsMicroMsg -> parse/upload
    }
}
