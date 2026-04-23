package com.sbnkj.assistant.core.backup

import com.sbnkj.assistant.core.config.WechatBackupPaths
import com.sbnkj.assistant.core.config.WechatSystemInfoParser
import com.sbnkj.assistant.core.model.WechatIdentity
import com.sbnkj.assistant.core.model.WechatUserSlot

data class FixedBackupPlan(
    val slot: WechatUserSlot,
    val paths: WechatBackupPaths,
    val identity: WechatIdentity,
    val systemInfoJob: BackupJob,
    val accountMappingJob: BackupJob,
    val enMicroMsgJobs: List<BackupJob>,
    val snsMicroMsgJobs: List<BackupJob>,
)

class WechatFixedBackupCoordinator(
    private val gateway: BackupGateway,
    private val parser: WechatSystemInfoParser = WechatSystemInfoParser(),
) {
    suspend fun backupSystemInfo(slot: WechatUserSlot): WechatIdentity {
        val paths = WechatBackupPaths(slot)
        val job = BackupJob(
            requestId = WechatBackupRequestIds.systemInfo(slot),
            src = paths.systemInfoSrc,
            dst = paths.systemInfoOut,
        )
        gateway.backup(job)
        return parser.parse(slot, paths.systemInfoOut)
    }

    suspend fun buildPlan(slot: WechatUserSlot): FixedBackupPlan {
        val paths = WechatBackupPaths(slot)
        val identity = backupSystemInfo(slot)
        val accountRoot = paths.accountRoot(identity.accountDir)
        val backupRoot = paths.backupAccountRoot(identity.accountDir)

        val systemInfoJob = BackupJob(
            requestId = WechatBackupRequestIds.systemInfo(slot),
            src = paths.systemInfoSrc,
            dst = paths.systemInfoOut,
        )
        val accountMappingJob = BackupJob(
            requestId = WechatBackupRequestIds.accountMapping(slot),
            src = accountRoot.resolve("account.mapping"),
            dst = backupRoot.resolve("account.mapping"),
        )
        // val enJobs = listOf(
        //     BackupJob(-1, src = accountRoot.resolve("EnMicroMsg.db-shm"), dst = backupRoot.resolve("EnMicroMsg.db-shm")),
        //     BackupJob(-1, src = accountRoot.resolve("EnMicroMsg.db-wal"), dst = backupRoot.resolve("EnMicroMsg.db-wal")),
        //     BackupJob(WechatBackupRequestIds.enMicroMsg(slot), src = accountRoot.resolve("EnMicroMsg.db"), dst = backupRoot.resolve("EnMicroMsg.db")),
        // )
        // val snsJobs = listOf(
        //     BackupJob(-1, src = accountRoot.resolve("SnsMicroMsg.db-shm"), dst = backupRoot.resolve("SnsMicroMsg.db-shm")),
        //     BackupJob(-1, src = accountRoot.resolve("SnsMicroMsg.db-wal"), dst = backupRoot.resolve("SnsMicroMsg.db-wal")),
        //     BackupJob(WechatBackupRequestIds.snsMicroMsg(slot), src = accountRoot.resolve("SnsMicroMsg.db"), dst = backupRoot.resolve("SnsMicroMsg.db")),
        // )
        val enJobs = listOf(
            BackupJob(WechatBackupRequestIds.enMicroMsgShm(slot), src = accountRoot.resolve("EnMicroMsg.db-shm"), dst = backupRoot.resolve("EnMicroMsg.db-shm")),
            BackupJob(WechatBackupRequestIds.enMicroMsgWal(slot), src = accountRoot.resolve("EnMicroMsg.db-wal"), dst = backupRoot.resolve("EnMicroMsg.db-wal")),
            BackupJob(WechatBackupRequestIds.enMicroMsg(slot), src = accountRoot.resolve("EnMicroMsg.db"), dst = backupRoot.resolve("EnMicroMsg.db")),
        )
        val snsJobs = listOf(
            BackupJob(WechatBackupRequestIds.snsMicroMsgShm(slot), src = accountRoot.resolve("SnsMicroMsg.db-shm"), dst = backupRoot.resolve("SnsMicroMsg.db-shm")),
            BackupJob(WechatBackupRequestIds.snsMicroMsgWal(slot), src = accountRoot.resolve("SnsMicroMsg.db-wal"), dst = backupRoot.resolve("SnsMicroMsg.db-wal")),
            BackupJob(WechatBackupRequestIds.snsMicroMsg(slot), src = accountRoot.resolve("SnsMicroMsg.db"), dst = backupRoot.resolve("SnsMicroMsg.db")),
        )
        return FixedBackupPlan(
            slot = slot,
            paths = paths,
            identity = identity,
            systemInfoJob = systemInfoJob,
            accountMappingJob = accountMappingJob,
            enMicroMsgJobs = enJobs,
            snsMicroMsgJobs = snsJobs,
        )
    }

    suspend fun runFixedBackup(slot: WechatUserSlot): FixedBackupPlan {
        val plan = buildPlan(slot)
        gateway.backup(plan.accountMappingJob)
        // plan.enMicroMsgJobs.forEach(gateway::backup)
        // plan.snsMicroMsgJobs.forEach(gateway::backup)
        // 修复：使用标准 for 循环逐个执行挂起函数
        for (job in plan.enMicroMsgJobs) {
            gateway.backup(job)
        }
        for (job in plan.snsMicroMsgJobs) {
            gateway.backup(job)
        }
        return plan
    }
}
