package com.sbnkj.wechatmigrator.core.backup

interface BackupGateway {
    suspend fun backup(job: BackupJob)
}
