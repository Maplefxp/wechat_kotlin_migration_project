package com.sbnkj.wechatmigrator

import android.app.Application
import com.sbnkj.wechatmigrator.core.db.SqlCipherLoader

class MydbMigrationApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SqlCipherLoader.load()
    }
}
