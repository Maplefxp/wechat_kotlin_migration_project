package com.sbnkj.assistant.ui

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.sbnkj.assistant.oplus.OplusManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            text = buildString {
                appendLine("WeChat Kotlin Migration Project")
            }
            textSize = 16f
            setPadding(48, 72, 48, 72)
        }
        setContentView(textView)
        requestNormalPermissions() //全部普通权限
        checkAndGrantAllFilesPermission() //特殊应用权限

    }

    fun requestNormalPermissions() {
        OplusManager.MDMDeviceSettingManager.getApiVersion()?.let {
            Log.d("MDM_API", "当前 API 版本：$it")
        }

        val success = OplusManager.MDMDeviceSecurityManager.grantAllRuntimePermission(packageName)
        if (success) {
            Log.d("MDM_API", "已授权所有运行时权限")
        } else {
            Log.d("MDM_API", "授权运行时权限失败")
        }
    }

    private fun checkAndGrantAllFilesPermission() {
        val targetPackage = packageName // 即 com.sbnkj.assistant
        val currentPolicy = OplusManager.MDMDeviceSecurityManager.getAppExtStoragePolicies(targetPackage)
        Log.d("MDM_Perm", "当前外部存储权限策略值为: $currentPolicy")
        val hasPermission = (currentPolicy == 0 || currentPolicy == 2)
        if (!hasPermission) {
            Log.w("MDM_Perm", "发现尚未拥有外部存储（所有文件）访问权限，准备静默授权...")
            val success = OplusManager.MDMDeviceSecurityManager.setAppExtStoragePolicies(targetPackage, 0)

            if (success) {
                Log.d("MDM_Perm", "✅ 外部存储权限静默授权调用成功！")
                Log.d("MDM_Perm", "⚠️ 注意：系统即将杀死当前进程以应用权限，App 会闪退并自动重启！")
            } else {
                Log.e("MDM_Perm", "❌ 外部存储权限授权调用失败。")
            }
        } else {
            Log.d("MDM_Perm", "✅ 已经拥有外部存储权限，安全跳过授权步骤。")

        }
    }

}
