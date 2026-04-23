package com.sbnkj.assistant.core.backup

import java.io.File

data class BackupJob(
    val requestId: Int,
    val packageName: String = "com.tencent.mm",
    val src: File,
    val dst: File,
)
