package com.sbnkj.assistant.core.backup

interface BackupGateway {
    suspend fun backup(job: BackupJob): Int
}
