package com.sbnkj.assistant.core.backup

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.oplus.enterprise.mdmcoreservice.manager.DeviceSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OppoBackupGateway(
    private val context: Context,
    private val packageName: String = context.packageName
) : BackupGateway {

    companion object {
        private const val TAG = "OppoBackupGateway"

        // 备份结果广播 Action
        private const val ACTION_BACKUP_SUCCESS = "action.backup.app.data.success"
        private const val ACTION_BACKUP_FAILED = "action.backup.app.data.failed"

        // 微信包名常量
        const val WECHAT_PACKAGE_MAIN = "com.tencent.mm"
    }

    private val deviceSecurityManager: DeviceSecurityManager by lazy {
        DeviceSecurityManager.getInstance(context)
    }

    override suspend fun backup(job: BackupJob) = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始备份任务: requestId=${job.requestId}")
        Log.d(TAG, "源文件: ${job.src}")
        Log.d(TAG, "目标文件: ${job.dst}")

        try {
            // 确保目标目录存在
            job.dst.parentFile?.mkdirs()

            // 计算 rootPathMode 和 src
            val (rootPathMode, relativeSrc) = calculateRootPathMode(job.src)

            // 根据源路径判断要备份的应用包名
            val targetPackageName = determineTargetPackage(job.src)

            // 目标路径必须在 /storage/emulated/0/ 下
            val destPath = job.dst.absolutePath

            Log.d(TAG, "调用 SDK: rootPathMode=$rootPathMode, src=$relativeSrc, packageName=$targetPackageName, dest=$destPath, requestId=${job.requestId}")

            // 执行备份并等待结果
            backupWithCallback(rootPathMode, relativeSrc, targetPackageName, destPath, job.requestId)

            Log.d(TAG, "备份任务完成: requestId=${job.requestId}")

        } catch (e: Exception) {
            Log.e(TAG, "备份任务失败: requestId=${job.requestId}", e)
            throw e
        }
    }

    /**
     * 根据源路径判断目标应用包名
     */
    private fun determineTargetPackage(srcPath: java.io.File): String {
        val absolutePath = srcPath.absolutePath

        return when {
            absolutePath.startsWith("/data/data/${WECHAT_PACKAGE_MAIN}/") -> {
                WECHAT_PACKAGE_MAIN  // 主微信
            }
            absolutePath.startsWith("/data/user/999/${WECHAT_PACKAGE_MAIN}/") -> {
                WECHAT_PACKAGE_MAIN  // 分身微信
            }
            absolutePath.startsWith("/data/data/") -> {
                // 其他应用，提取包名
                val parts = absolutePath.substringAfter("/data/data/").split("/")
                if (parts.isNotEmpty()) parts[0] else context.packageName
            }
            else -> {
                // 默认使用本应用包名
                context.packageName
            }
        }
    }

    /**
     * 计算 rootPathMode 和相对路径
     */
    private fun calculateRootPathMode(srcPath: java.io.File): Pair<Int, String> {
        val absolutePath = srcPath.absolutePath

        return when {
            // /data/data/{packageName}/...
            absolutePath.startsWith("/data/data/${WECHAT_PACKAGE_MAIN}/") -> {
                val relativePath = absolutePath.substringAfter("/data/data/${WECHAT_PACKAGE_MAIN}/")
                Pair(0, "/$relativePath")
            }

            // /data/user/999/{packageName}/...
            absolutePath.startsWith("/data/user/999/${WECHAT_PACKAGE_MAIN}/") -> {
                val relativePath = absolutePath.substringAfter("/data/user/999/${WECHAT_PACKAGE_MAIN}/")
                Pair(1, "/$relativePath")
            }

            // /storage/emulated/999/Android/data/{packageName}/...
            absolutePath.startsWith("/storage/emulated/999/Android/data/${WECHAT_PACKAGE_MAIN}/") -> {
                val relativePath = absolutePath.substringAfter("/storage/emulated/999/Android/data/${WECHAT_PACKAGE_MAIN}/")
                Pair(2, "/$relativePath")
            }

            // /storage/emulated/0/Android/data/{packageName}/...
            absolutePath.startsWith("/storage/emulated/0/Android/data/${WECHAT_PACKAGE_MAIN}/") -> {
                val relativePath = absolutePath.substringAfter("/storage/emulated/0/Android/data/${WECHAT_PACKAGE_MAIN}/")
                Pair(3, "/$relativePath")
            }

            else -> {
                // 默认当作完整路径处理，使用 rootPathMode=0 和完整路径
                Log.w(TAG, "未知路径类型，使用默认模式: $absolutePath")
                Pair(0, absolutePath)
            }
        }
    }

    /**
     * 执行备份并等待回调结果
     */
    private suspend fun backupWithCallback(
        rootPathMode: Int,
        src: String,
        packageName: String,
        dest: String,
        requestId: Int
    ) = suspendCancellableCoroutine { continuation ->

        var successReceiver: android.content.BroadcastReceiver? = null
        var failedReceiver: android.content.BroadcastReceiver? = null

        // 【唯一修改点】：将局部函数提前声明，解决编译报错
        fun unregisterReceivers() {
            try {
                successReceiver?.let { context.unregisterReceiver(it) }
                failedReceiver?.let { context.unregisterReceiver(it) }
            } catch (e: Exception) {
                Log.w(TAG, "取消注册广播接收器失败", e)
            }
        }

        // 注册成功广播接收器
        successReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedRequestId = intent?.getIntExtra("requestId", -1) ?: -1
                if (receivedRequestId == requestId) {
                    Log.d(TAG, "备份成功: requestId=$requestId")
                    unregisterReceivers()
                    if (!continuation.isCompleted) {
                        continuation.resume(Unit)
                    }
                }
            }
        }

        // 注册失败广播接收器
        failedReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedRequestId = intent?.getIntExtra("requestId", -1) ?: -1
                if (receivedRequestId == requestId) {
                    val errorMsg = intent?.getStringExtra("errorMsg") ?: "未知错误"
                    Log.e(TAG, "备份失败: requestId=$requestId, error=$errorMsg")
                    unregisterReceivers()
                    if (!continuation.isCompleted) {
                        continuation.resumeWithException(Exception("备份失败: $errorMsg"))
                    }
                }
            }
        }

        // 注册广播接收器
        val successFilter = IntentFilter(ACTION_BACKUP_SUCCESS)
        val failedFilter = IntentFilter(ACTION_BACKUP_FAILED)

        // 使用 RECEIVER_NOT_EXPORTED 因为是我们自己发送的广播
        context.registerReceiver(successReceiver, successFilter, Context.RECEIVER_NOT_EXPORTED)
        context.registerReceiver(failedReceiver, failedFilter, Context.RECEIVER_NOT_EXPORTED)

        // 清理函数
        continuation.invokeOnCancellation {
            unregisterReceivers()
        }

        try {
            // 调用 OPPO SDK 的备份接口
            deviceSecurityManager.backupAppData(
                rootPathMode,
                src,
                packageName,
                dest,
                requestId
            )
            Log.d(TAG, "已提交备份任务到 SDK: requestId=$requestId")

        } catch (e: Exception) {
            Log.e(TAG, "调用 SDK 备份接口失败", e)
            unregisterReceivers()
            if (!continuation.isCompleted) {
                continuation.resumeWithException(e)
            }
        }
    }
}
