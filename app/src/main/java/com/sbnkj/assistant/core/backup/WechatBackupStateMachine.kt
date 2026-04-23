package com.sbnkj.assistant.core.backup

sealed interface BackupStage {
    data object SystemInfoReady : BackupStage
    data object AccountMappingReady : BackupStage
    data object EnMicroMsgReady : BackupStage
    data object SnsMicroMsgReady : BackupStage
    data object Finished : BackupStage
}

class WechatBackupStateMachine {
    fun nextForRequestId(requestId: Int): BackupStage? = when (requestId) {
        WechatBackupRequestIds.SYSTEM_INFO_MAIN,
        WechatBackupRequestIds.SYSTEM_INFO_CLONE -> BackupStage.SystemInfoReady

        WechatBackupRequestIds.ACCOUNT_MAPPING_MAIN,
        WechatBackupRequestIds.ACCOUNT_MAPPING_CLONE -> BackupStage.AccountMappingReady

        // 更新为新的常量名：EN_MICRO_MSG_DB_*
        WechatBackupRequestIds.EN_MICRO_MSG_DB_MAIN,
        WechatBackupRequestIds.EN_MICRO_MSG_DB_CLONE -> BackupStage.EnMicroMsgReady

        // 更新为新的常量名：SNS_MICRO_MSG_DB_*
        WechatBackupRequestIds.SNS_MICRO_MSG_DB_MAIN,
        WechatBackupRequestIds.SNS_MICRO_MSG_DB_CLONE -> BackupStage.SnsMicroMsgReady

        else -> null
    }
}
