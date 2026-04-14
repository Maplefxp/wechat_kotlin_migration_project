package com.sbnkj.wechatmigrator.core.model

sealed interface RemoteCommand {
    data class StartBackup(val slot: WechatUserSlot) : RemoteCommand
    data class ReuploadByMsgSvrId(val wxId: String, val msgSvrId: String) : RemoteCommand
    data class ReuploadByCreateTime(val wxId: String, val createTime: String) : RemoteCommand
    data class ReuploadSns(val wxId: String) : RemoteCommand
    data class ReuploadRedPacket(val wxId: String) : RemoteCommand
    data object StartRemoteService : RemoteCommand
    data class Unknown(val rawType: Int) : RemoteCommand
}
