package com.sbnkj.assistant.oplus

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.oplus.enterprise.mdmcoreservice.manager.DeviceSecurityManager
import com.oplus.enterprise.mdmcoreservice.manager.DeviceSettingsManager
import com.sbnkj.assistant.MydbMigrationApp

object OplusManager {

    private const val TAG = "OplusManager"

    private var appContext: Context? = null
    private var adminComponent: ComponentName? = null

    private var deviceSettingsManager: DeviceSettingsManager? = null
    private var deviceSecurityManager: DeviceSecurityManager? = null

    fun init(context: Context) {
        if (appContext != null) return // 防止重复初始化
        appContext = context.applicationContext

        adminComponent = ComponentName(appContext!!, MydbMigrationApp::class.java)

        try {
            deviceSettingsManager = DeviceSettingsManager.getInstance(appContext!!)
        } catch (e: Throwable) {
            Log.e(TAG, "DeviceSettingsManager 初始化失败", e)
        }

        try {
            deviceSecurityManager = DeviceSecurityManager.getInstance(appContext!!)
        } catch (e: Throwable) {
            Log.e(TAG, "DeviceSecurityManager 初始化失败", e)
        }

        Log.d(TAG, "OplusManager 初始化完成")
    }

    /**
     * 核心拦截器：统一处理空指针与权限异常
     */
    private inline fun <T, M> safeCall(manager: M?, action: (M, ComponentName) -> T): T? {
        val admin = adminComponent
        if (manager == null || admin == null) {
            Log.e(TAG, "❌ 接口调用失败：Manager 未就绪或未调用 OplusManager.init(context)")
            return null
        }
        return try {
            action(manager, admin)
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ 证书鉴权失败！请确认包名、签名已在系统白名单中。", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ MDM 底层接口异常", e)
            null
        }
    }

    // 设置管理模块
    object MDMDeviceSettingManager {
        /**
         * 查询 API 版本信息
         */
        fun getApiVersion(): String? {
            return safeCall(deviceSettingsManager) { m, admin ->
                m.getAPIVersion(admin)
            }
        }
    }

    // 安全与权限管理模块
    object MDMDeviceSecurityManager {

        /**
         * 对应用动态授予运行时权限
         * @param appPackageName 应用包名
         * @param jsonPermissions 权限配置 JSON 字符串
         * @return 是否成功
         */
        fun setAppPermission(appPackageName: String, jsonPermissions: String): Boolean {
            return safeCall(deviceSecurityManager) { m, _ ->
                m.setAppPermission(appPackageName, jsonPermissions)
            } ?: false
        }

        /**
         * 获取当前的应用权限
         * @param appPackageName 应用包名
         * @return 权限配置 JSON 字符串
         */
        fun getAppPermission(appPackageName: String): String? {
            return safeCall(deviceSecurityManager) { m, _ ->
                m.getAppPermission(appPackageName)
            }
        }

        /**
         * 静默允许第三方应用的运行时权限
         * @param packageName 被授予权限的应用
         * @return 是否成功
         */
        fun grantAllRuntimePermission(packageName: String): Boolean {
            return safeCall(deviceSecurityManager) { m, _ ->
                m.grantAllRuntimePermission(packageName)
            } ?: false
        }

        /**
         * 为指定应用授予外部存储（MANAGE_EXTERNAL_STORAGE）权限
         * @param appPackageName 应用包名
         * @param policy 0: 授予&可改，1: 未授&可改，2: 授予&不可改，3: 未授&不可改
         * @return 是否成功
         */
        fun setAppExtStoragePolicies(appPackageName: String, policy: Int): Boolean {
            return safeCall(deviceSecurityManager) { m, _ ->
                m.setAppExtStoragePolicies(appPackageName, policy)
            } ?: false
        }

        /**
         * 查询应用是否被授予外部存储访问权限
         * @param appPackageName 应用包名
         * @return 返回 policy 状态，若调用失败则返回 -1
         */
        fun getAppExtStoragePolicies(appPackageName: String): Int {
            return safeCall(deviceSecurityManager) { m, _ ->
                m.getAppExtStoragePolicies(appPackageName)
            } ?: -1
        }
    }
}