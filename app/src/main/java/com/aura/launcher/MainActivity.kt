package com.aura.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    // يزيد كل ما اللانشر يرجع للواجهة، علشان قائمة التطبيقات تتحدث (تثبيت/حذف تطبيق)
    private var refreshTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuraTheme {
                HomeScreen(refreshKey = refreshTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // زر Home ضغطة تانية: نرجع للشاشة الرئيسية (التوسّع لاحقاً: الصفحة الأولى / إغلاق الدروار)
    }
}
