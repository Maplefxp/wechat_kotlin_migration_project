package com.sbnkj.assistant

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.oplus.enterprise.mdmcoreservice.manager.DeviceApplicationManager
import com.sbnkj.assistant.core.db.SqlCipherLoader
import com.sbnkj.assistant.oplus.OplusManager

/**
 * 应用 Application
 * 负责在应用启动初期完成底层依赖项（如加密数据库）的初始化，
 * OPPO 企业级 MDM 权限静默提权和后台状态管理
 */
class MydbMigrationApp : Application() {

    companion object {
        private const val TAG = "MydbMigrationApp"
    }

    override fun onCreate() {
        super.onCreate()
        SqlCipherLoader.load() // 加载 SQLCipher 加密库
        OplusManager.init(this) // 初始化 OPPO MDM 管理器

        removeFromPersistentBackground(this, packageName)
        requestNormalPermissions() // 静默授予全部普通运行时权限
        checkAndGrantAllFilesPermission() // 静默授予特殊应用权限 (所有文件访问权)
    }



    /**
     * 将指定应用从系统的“常驻后台（保活）”白名单中移除。
     * @param context       上下文
     * @param targetPackage 需要被移出保活名单的目标包名
     */
    fun removeFromPersistentBackground(context: Context, targetPackage: String) {
        try {
            val appManager = DeviceApplicationManager.getInstance(context)
            val adminComponent = ComponentName(context, MydbMigrationApp::class.java)
            val packageList = listOf(targetPackage)
            appManager.removePersistentApp(adminComponent, packageList)
            Log.i(TAG, "成功将 $targetPackage 移出常驻后台名单")
        } catch (e: SecurityException) {
            // 核心权限拦截处理
            Log.e(TAG, "权限被拒绝 (SecurityException): 缺少 OPPO MDM 权限", e)
            // 这里可以执行 UI 提示，告知用户或管理员设备未激活管控权限
        } catch (e: Exception) {
            Log.e(TAG, "调用接口发生未知异常", e)
        }
    }

    /**
     * 静默请求并授予所有的普通运行时权限,无需弹窗让用户手动点击授权。
     *
     */
    fun requestNormalPermissions() {
        OplusManager.MDMDeviceSettingManager.getApiVersion()?.let {
            Log.d(TAG, "当前 API 版本：$it")
        }

        // 调用 MDM 安全接口静默授予所有运行时权限
        val success = OplusManager.MDMDeviceSecurityManager.grantAllRuntimePermission(packageName)
        if (success) {
            Log.d(TAG, "已授权所有运行时权限")
        } else {
            Log.d(TAG, "授权运行时权限失败")
        }
    }

    /**
     * 检查并静默授予“所有文件访问权限”（Manage External Storage）。
     * 由于 Android 系统的安全机制，如果该权限发生了状态变更（从无到有），
     * 系统会自动杀死该应用进程以使权限生效，因此应用会出现闪退并由系统重新拉起的现象。
     */
    private fun checkAndGrantAllFilesPermission() {
        val targetPackage = packageName // 即 com.sbnkj.assistant

        // 获取外部存储权限的策略值：0 授予且可改，1 未授且可改，2 授予且不可改，3 未授且不可改
        val currentPolicy =
            OplusManager.MDMDeviceSecurityManager.getAppExtStoragePolicies(targetPackage)
        Log.d(TAG, "当前外部存储权限策略值为: $currentPolicy")

        // 判断是否已经拥有权限 (策略值为 0 或 2 代表已授予)
        val hasPermission = (currentPolicy == 0 || currentPolicy == 2)
        if (!hasPermission) {
            Log.w(TAG, "发现尚未拥有外部存储（所有文件）访问权限，准备静默授权...")

            // 尝试静默设置为 0（授予且可改）
            val success = OplusManager.MDMDeviceSecurityManager.setAppExtStoragePolicies(targetPackage, 0)

            if (success) {
                // 关键业务提示：特殊权限变更会导致系统强制杀进程
                Log.d(TAG, "✅ 外部存储权限静默授权调用成功！⚠️ 注意：系统即将杀死当前进程以应用权限，App 会闪退并自动重启！")
            } else {
                Log.e(TAG, "❌ 外部存储权限授权调用失败。")
            }
        } else {
            Log.d(TAG, "✅ 已经拥有外部存储权限，安全跳过授权步骤。")
        }
    }
}