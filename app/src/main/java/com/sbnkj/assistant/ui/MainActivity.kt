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

    }

}
