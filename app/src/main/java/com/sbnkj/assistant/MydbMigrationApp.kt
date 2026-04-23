package com.sbnkj.assistant

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.oplus.enterprise.mdmcoreservice.manager.DeviceApplicationManager
import com.sbnkj.assistant.core.db.SqlCipherLoader
import com.sbnkj.assistant.oplus.OplusManager

class MydbMigrationApp : Application() {

    companion object {
        private const val TAG = "MydbMigrationApp"
    }

    override fun onCreate() {
        super.onCreate()
        SqlCipherLoader.load()
        OplusManager.init(this)
        removeFromPersistentBackground(this, packageName)
    }

    fun removeFromPersistentBackground(context: Context, targetPackage: String) {
        try {
            val appManager = DeviceApplicationManager.getInstance(context)
            val adminComponent = ComponentName(context, MydbMigrationApp::class.java)
            val packageList = listOf(targetPackage)
            appManager.removePersistentApp(adminComponent, packageList)
            Log.i("MDM_Log", "成功将 $targetPackage 移出常驻后台名单")
        } catch (e: SecurityException) {
            // 5. 核心权限拦截处理
            Log.e("MDM_Log", "权限被拒绝 (SecurityException): 缺少 OPPO MDM 权限", e)
            // 这里可以执行 UI 提示，告知用户或管理员设备未激活管控权限
        } catch (e: Exception) {
            Log.e("MDM_Log", "调用接口发生未知异常", e)
        }
    }

}
