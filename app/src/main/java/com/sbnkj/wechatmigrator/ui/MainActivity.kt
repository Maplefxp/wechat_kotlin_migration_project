package com.sbnkj.wechatmigrator.ui

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            text = buildString {
                appendLine("WeChat Kotlin Migration Project")
                appendLine()
                appendLine("已经接入的关键链路：")
                appendLine("1. systemInfo.cfg 解析")
                appendLine("2. 固定必备文件备份编排")
                appendLine("3. SQLCipher 打开 EnMicroMsg.db")
                appendLine("4. message 表增量读取")
                appendLine("5. 文本/图片/语音/视频/文件/VoIP 分流")
                appendLine("6. 本地 message / WxFileIndex3 队列表")
                appendLine()
                appendLine("下一步请接入真实厂商 BackupGateway。")
            }
            textSize = 16f
            setPadding(48, 72, 48, 72)
        }
        setContentView(textView)
    }
}
