package com.sbnkj.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sbnkj.assistant.core.model.RemoteCommand
import com.sbnkj.assistant.core.model.WechatUserSlot
import com.sbnkj.assistant.service.WechatSyncService
import org.json.JSONObject

/**
 * 接收远程广播命令（补传/全量备份等）
 */
class WechatCommandReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WechatCommandReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "========== 收到广播 ==========")
        Log.d(TAG, "Action: ${intent.action}")
        Log.d(TAG, "Extras: ${intent.extras}")

        if (intent.action != "com.yunke.wechat.reupload.time") {
            Log.w(TAG, "Action 不匹配，忽略")
            return
        }

        val content = intent.getStringExtra("content")
        if (content == null) {
            Log.e(TAG, "content 为空，无法解析")
            return
        }

        Log.d(TAG, "Content: $content")

        try {
            val command = parse(content)
            Log.d(TAG, "解析命令: $command")

            when (command) {
                is RemoteCommand.StartBackup -> {
                    Log.d(TAG, "启动备份服务: slot=${command.slot}")
                    context.startService(WechatSyncService.startBackupIntent(context, command.slot))
                    Log.d(TAG, "备份服务已启动")
                }
                else -> {
                    Log.w(TAG, "未实现的命令类型: $command")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理命令时发生异常", e)
        }

        Log.d(TAG, "========== 广播处理完成 ==========")
    }

    private fun parse(content: String): RemoteCommand {
        val json = JSONObject(content)
        val type = json.optInt("type")
        Log.d(TAG, "解析 JSON: type=$type")

        return when (type) {
            120 -> {
                Log.d(TAG, "命令类型: 主微信备份")
                RemoteCommand.StartBackup(WechatUserSlot.MAIN)
            }
            121 -> {
                Log.d(TAG, "命令类型: 分身微信备份")
                RemoteCommand.StartBackup(WechatUserSlot.CLONE)
            }
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
            else -> {
                Log.w(TAG, "未知命令类型: $type")
                RemoteCommand.Unknown(type)
            }
        }
    }
}
