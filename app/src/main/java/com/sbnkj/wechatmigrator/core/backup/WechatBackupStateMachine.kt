package com.sbnkj.wechatmigrator.core.backup

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

        WechatBackupRequestIds.EN_MICRO_MSG_FIRST_MAIN,
        WechatBackupRequestIds.EN_MICRO_MSG_FIRST_CLONE -> BackupStage.EnMicroMsgReady

        WechatBackupRequestIds.SNS_MICRO_MSG_MAIN,
        WechatBackupRequestIds.SNS_MICRO_MSG_CLONE -> BackupStage.SnsMicroMsgReady

        else -> null
    }
}
