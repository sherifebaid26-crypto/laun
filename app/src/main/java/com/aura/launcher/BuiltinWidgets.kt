package com.aura.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BuiltinKinds = listOf(
    "clock" to ("ساعة وتاريخ" to "Clock & date"),
    "battery" to ("البطارية" to "Battery"),
    "search" to ("شريط البحث" to "Search bar"),
)

/** ودجتس داخلية من Aura: بتشتغل على أي جهاز من غير ما تعتمد على ودجتس النظام. */
@Composable
fun BuiltinWidget(kind: String, pal: AuraPalette, ui: Ui, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val card = modifier
        .clip(shape)
        .background(pal.panel)
        .border(1.dp, pal.border, shape)
    when (kind) {
        "battery" -> BatteryWidget(pal, card)
        "search" -> Row(
            card.clickable { ui.openDrawer(true) }.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔍", fontSize = 18.sp)
            Text(
                tr("ابحث في التطبيقات…", "Search apps…"),
                color = pal.onPanel.copy(alpha = 0.7f), fontSize = 16.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        else -> Box(card.clickable { ui.openDrawer(false) }, contentAlignment = Alignment.CenterStart) {
            ClockHeader(
                style = ClockStyle.BIG, color = pal.onPanel, shadow = androidx.compose.ui.graphics.Color.Transparent,
                scale = 0.85f,
            )
        }
    }
}

@Composable
private fun BatteryWidget(pal: AuraPalette, modifier: Modifier) {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        fun read(i: Intent?) {
            if (i == null) return
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && sc > 0) level = l * 100 / sc
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = read(intent)
        }
        read(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    Column(modifier.padding(horizontal = 22.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (level >= 0) "$level%" else "--",
                color = pal.onPanel, fontSize = 34.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (charging) tr("⚡ بيشحن", "⚡ Charging") else tr("البطارية", "Battery"),
                color = pal.onPanel.copy(alpha = 0.7f), fontSize = 14.sp,
            )
        }
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(pal.onPanel.copy(alpha = 0.18f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((level.coerceIn(0, 100)) / 100f)
                    .background(if (level in 0..15 && !charging) androidx.compose.ui.graphics.Color(0xFFFF5252) else pal.accent),
            )
        }
    }
}
