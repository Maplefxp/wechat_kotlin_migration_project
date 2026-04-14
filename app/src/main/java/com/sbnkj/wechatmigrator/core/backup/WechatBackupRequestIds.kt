package com.sbnkj.wechatmigrator.core.backup

import com.sbnkj.wechatmigrator.core.model.WechatUserSlot

object WechatBackupRequestIds {
    const val SYSTEM_INFO_MAIN = 20001
    const val SYSTEM_INFO_CLONE = 30001
    const val ACCOUNT_MAPPING_MAIN = 20010
    const val ACCOUNT_MAPPING_CLONE = 30010
    const val EN_MICRO_MSG_FIRST_MAIN = 20003
    const val EN_MICRO_MSG_FIRST_CLONE = 30003
    const val SNS_MICRO_MSG_MAIN = 20007
    const val SNS_MICRO_MSG_CLONE = 30007

    fun systemInfo(slot: WechatUserSlot): Int = if (slot.isClone) SYSTEM_INFO_CLONE else SYSTEM_INFO_MAIN
    fun accountMapping(slot: WechatUserSlot): Int = if (slot.isClone) ACCOUNT_MAPPING_CLONE else ACCOUNT_MAPPING_MAIN
    fun enMicroMsg(slot: WechatUserSlot): Int = if (slot.isClone) EN_MICRO_MSG_FIRST_CLONE else EN_MICRO_MSG_FIRST_MAIN
    fun snsMicroMsg(slot: WechatUserSlot): Int = if (slot.isClone) SNS_MICRO_MSG_CLONE else SNS_MICRO_MSG_MAIN
}
