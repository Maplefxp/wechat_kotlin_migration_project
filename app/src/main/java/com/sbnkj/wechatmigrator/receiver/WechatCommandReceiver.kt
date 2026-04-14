package com.sbnkj.wechatmigrator.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sbnkj.wechatmigrator.core.model.RemoteCommand
import com.sbnkj.wechatmigrator.core.model.WechatUserSlot
import com.sbnkj.wechatmigrator.service.WechatSyncService
import org.json.JSONObject

class WechatCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.yunke.wechat.reupload.time") return
        val content = intent.getStringExtra("content") ?: return
        val command = parse(content)
        when (command) {
            is RemoteCommand.StartBackup -> {
                context.startService(WechatSyncService.startBackupIntent(context, command.slot))
            }
            else -> {
                // 其余类型先留给后续 websocket / 补传模块继续接。
            }
        }
    }

    private fun parse(content: String): RemoteCommand {
        val json = JSONObject(content)
        return when (json.optInt("type")) {
            120 -> RemoteCommand.StartBackup(WechatUserSlot.MAIN)
            121 -> RemoteCommand.StartBackup(WechatUserSlot.CLONE)
            10 -> {
                val raw = json.optString("wxId")
                val parts = raw.split("###")
                if (parts.size == 2) RemoteCommand.ReuploadByMsgSvrId(parts[0], parts[1]) else RemoteCommand.Unknown(10)
            }
            11 -> {
                val raw = json.optString("wxId")
                val parts = raw.split("###")
                if (parts.size == 2) RemoteCommand.ReuploadByCreateTime(parts[0], parts[1]) else RemoteCommand.Unknown(11)
            }
            15 -> RemoteCommand.ReuploadSns(json.optString("wxId"))
            16 -> RemoteCommand.ReuploadRedPacket(json.optString("wxId"))
            130 -> RemoteCommand.StartRemoteService
            else -> RemoteCommand.Unknown(json.optInt("type"))
        }
    }
}
