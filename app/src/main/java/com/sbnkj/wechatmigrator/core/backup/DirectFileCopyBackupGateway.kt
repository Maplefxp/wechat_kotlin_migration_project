package com.sbnkj.wechatmigrator.core.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DirectFileCopyBackupGateway : BackupGateway {
    override suspend fun backup(job: BackupJob) = withContext(Dispatchers.IO) {
        job.dst.parentFile?.mkdirs()
        if (!job.src.exists()) return@withContext
        job.src.copyTo(job.dst, overwrite = true)
    }
}
