package com.sbnkj.assistant.core.backup

import com.sbnkj.assistant.core.model.WechatUserSlot

object WechatBackupRequestIds {
    const val SYSTEM_INFO_MAIN = 20001
    const val SYSTEM_INFO_CLONE = 30001
    const val ACCOUNT_MAPPING_MAIN = 20010
    const val ACCOUNT_MAPPING_CLONE = 30010

    // EnMicroMsg.db 相关文件
    const val EN_MICRO_MSG_SHM_MAIN = 20002
    const val EN_MICRO_MSG_WAL_MAIN = 20003
    const val EN_MICRO_MSG_DB_MAIN = 20004

    const val EN_MICRO_MSG_SHM_CLONE = 30002
    const val EN_MICRO_MSG_WAL_CLONE = 30003
    const val EN_MICRO_MSG_DB_CLONE = 30004

    // SnsMicroMsg.db 相关文件
    const val SNS_MICRO_MSG_SHM_MAIN = 20005
    const val SNS_MICRO_MSG_WAL_MAIN = 20006
    const val SNS_MICRO_MSG_DB_MAIN = 20007

    const val SNS_MICRO_MSG_SHM_CLONE = 30005
    const val SNS_MICRO_MSG_WAL_CLONE = 30006
    const val SNS_MICRO_MSG_DB_CLONE = 30007

    fun systemInfo(slot: WechatUserSlot): Int = if (slot.isClone) SYSTEM_INFO_CLONE else SYSTEM_INFO_MAIN
    fun accountMapping(slot: WechatUserSlot): Int = if (slot.isClone) ACCOUNT_MAPPING_CLONE else ACCOUNT_MAPPING_MAIN

    fun enMicroMsgShm(slot: WechatUserSlot): Int = if (slot.isClone) EN_MICRO_MSG_SHM_CLONE else EN_MICRO_MSG_SHM_MAIN
    fun enMicroMsgWal(slot: WechatUserSlot): Int = if (slot.isClone) EN_MICRO_MSG_WAL_CLONE else EN_MICRO_MSG_WAL_MAIN
    fun enMicroMsg(slot: WechatUserSlot): Int = if (slot.isClone) EN_MICRO_MSG_DB_CLONE else EN_MICRO_MSG_DB_MAIN

    fun snsMicroMsgShm(slot: WechatUserSlot): Int = if (slot.isClone) SNS_MICRO_MSG_SHM_CLONE else SNS_MICRO_MSG_SHM_MAIN
    fun snsMicroMsgWal(slot: WechatUserSlot): Int = if (slot.isClone) SNS_MICRO_MSG_WAL_CLONE else SNS_MICRO_MSG_WAL_MAIN
    fun snsMicroMsg(slot: WechatUserSlot): Int = if (slot.isClone) SNS_MICRO_MSG_DB_CLONE else SNS_MICRO_MSG_DB_MAIN
}
