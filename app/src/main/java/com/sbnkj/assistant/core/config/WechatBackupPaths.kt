package com.sbnkj.assistant.core.config

import com.sbnkj.assistant.core.model.WechatUserSlot
import java.io.File

data class WechatBackupPaths(
    val slot: WechatUserSlot,
    val backupRoot: File = File("/storage/emulated/0/backup_wechat"),
    val exportRoot: File = File("/storage/emulated/0/yunke_file"),
) {
    val slotBackupRoot: File = File(backupRoot, slot.dirName)
    val slotBackupMicroMsgRoot: File = File(slotBackupRoot, "new/MicroMsg")
    val slotExportMicroMsgRoot: File = File(exportRoot, "${slot.dirName}/MicroMsg")

    val wechatDataRoot: File = when (slot) {
        WechatUserSlot.MAIN -> File("/data/data/com.tencent.mm")
        WechatUserSlot.CLONE -> File("/data/user/999/com.tencent.mm")
    }

    val microMsgRoot: File = File(wechatDataRoot, "MicroMsg")
    val systemInfoSrc: File = File(microMsgRoot, "systemInfo.cfg")
    val systemInfoOut: File = File(slotBackupRoot, "systemInfo.cfg")

    fun accountRoot(accountDir: String): File = File(microMsgRoot, accountDir)
    fun backupAccountRoot(accountDir: String): File = File(slotBackupMicroMsgRoot, accountDir)

    fun wechatVoicePath(accountDir: String, subPath: String): String =
        File(accountRoot(accountDir), subPath).absolutePath

    fun outVoicePath(accountDir: String, subPath: String): String =
        File(backupAccountRoot(accountDir), subPath).absolutePath
}
