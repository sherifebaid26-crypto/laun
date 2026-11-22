package com.aura.launcher

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

data class DeviceStatus(val charging: Boolean, val headphones: Boolean, val wifi: Boolean)

// ───────────── أيقونات حيّة (ساعة / تقويم) ─────────────

@Composable
fun LiveIconOverlay(app: AppEntry, size: Dp) {
    val p = app.packageName.lowercase()
    val isCal = p.contains("calendar")
    val isClock = !isCal && (p.contains("deskclock") || p.endsWith(".clock") || p.contains("clock"))
    if (!isCal && !isClock) return
    if (isCal) {
        val day = rememberNow(60_000L).dayOfMonth
        Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(Color.White)) {
            Box(Modifier.fillMaxSize().padding(bottom = size * 0.72f).background(Color(0xFFE53935)))
            Text(
                day.toString(), color = Color(0xFF222222), fontSize = (size.value * 0.42f).sp,
                modifier = Modifier.align(Alignment.Center).padding(top = size * 0.16f),
            )
        }
    } else {
        val now = rememberNow(1000L)
        Canvas(Modifier.size(size)) {
            val c = center
            val r = this.size.minDimension / 2f
            drawCircle(Color(0xFF14141C), radius = r)
            drawCircle(Color(0x33FFFFFF), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.05f))
            val h = (now.hour % 12) + now.minute / 60f
            val m = now.minute + now.second / 60f
            fun hand(angle: Double, len: Float, w: Float, col: Color) {
                val a = Math.toRadians(angle - 90.0)
                drawLine(col, c, Offset(c.x + cos(a).toFloat() * r * len, c.y + sin(a).toFloat() * r * len), w, StrokeCap.Round)
            }
            hand(h * 30.0, 0.5f, r * 0.09f, Color.White)
            hand(m * 6.0, 0.75f, r * 0.06f, Color.White)
            hand(now.second * 6.0, 0.8f, r * 0.03f, Color(0xFFFF7043))
            drawCircle(Color(0xFFFF7043), radius = r * 0.06f, center = c)
        }
    }
}

// ───────────── اقتراحات ذكية ─────────────

fun slotOf(hour: Int): Int = hour / 6

/** التطبيقات المقترحة دلوقتي: لو سماعة موصّلة نبدأ بتطبيقات الصوت، وبعدين الأكثر استخداماً في نفس الفترة من اليوم. */
fun smartSuggestions(state: AuraState, visible: List<AppEntry>, hour: Int, headphones: Boolean, n: Int): List<AppEntry> {
    val slot = slotOf(hour)
    val out = LinkedHashSet<AppEntry>()
    if (headphones) {
        visible.filter { it.category == android.content.pm.ApplicationInfo.CATEGORY_AUDIO }
            .sortedByDescending { state.usage[it.key]?.count ?: 0 }.take(2).forEach { out += it }
    }
    visible.filter { (state.usage[it.key]?.slots?.getOrNull(slot) ?: 0) > 0 }
        .sortedByDescending { state.usage[it.key]?.slots?.getOrNull(slot) ?: 0 }
        .forEach { if (out.size < n) out += it }
    if (out.size < n) {
        visible.filter { (state.usage[it.key]?.count ?: 0) > 0 }
            .sortedByDescending { state.usage[it.key]?.count ?: 0 }
            .forEach { if (out.size < n) out += it }
    }
    return out.take(n)
}

// ───────────── بحث إعدادات النظام ─────────────

data class SysSetting(val ar: String, val en: String, val words: String, val action: String)

val SysSettings = listOf(
    SysSetting("واي فاي", "Wi-Fi", "wifi واي فاي شبكه انترنت", Settings.ACTION_WIFI_SETTINGS),
    SysSetting("بلوتوث", "Bluetooth", "bluetooth بلوتوث", Settings.ACTION_BLUETOOTH_SETTINGS),
    SysSetting("الشاشة والسطوع", "Display", "display screen brightness الشاشه سطوع", Settings.ACTION_DISPLAY_SETTINGS),
    SysSetting("الصوت", "Sound", "sound volume صوت رنين", Settings.ACTION_SOUND_SETTINGS),
    SysSetting("البطارية", "Battery", "battery بطاريه شحن", Intent.ACTION_POWER_USAGE_SUMMARY),
    SysSetting("التخزين", "Storage", "storage تخزين مساحه", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    SysSetting("الموقع", "Location", "location gps موقع", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    SysSetting("اللغة", "Language", "language لغه", Settings.ACTION_LOCALE_SETTINGS),
    SysSetting("التاريخ والوقت", "Date & time", "date time تاريخ وقت", Settings.ACTION_DATE_SETTINGS),
    SysSetting("إمكانية الوصول", "Accessibility", "accessibility وصول", Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SysSetting("الإشعارات", "Notifications", "notification اشعارات", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
    SysSetting("التطبيقات", "Apps", "apps تطبيقات", Settings.ACTION_APPLICATION_SETTINGS),
    SysSetting("الإعدادات", "Settings", "settings اعدادات", Settings.ACTION_SETTINGS),
)

fun matchSettings(q: String): List<SysSetting> {
    if (q.length < 2) return emptyList()
    return SysSettings.filter { s ->
        normalizeForSearch(s.ar).contains(q) || s.en.lowercase().contains(q) || normalizeForSearch(s.words).contains(q)
    }.take(3)
}

// ───────────── ودجت وقت الشاشة (آخر 7 أيام) ─────────────

@Composable
fun ScreenTimeWidget(ui: Ui, fg: Color, accent: Color) {
    val mins by produceState<List<Int>?>(null, ui.hour) {
        value = withContext(Dispatchers.IO) { ui.actions.dailyScreenMinutes() }
    }
    val m = mins
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
        if (m == null || m.isEmpty()) {
            Text(
                tr("فعّل «بيانات الاستخدام» من الإعدادات > الأذونات", "Enable Usage access in Settings > Permissions"),
                color = fg.copy(alpha = 0.7f), fontSize = 13.sp,
            )
        } else {
            val today = m.last()
            Text(
                tr("وقت الشاشة اليوم: ", "Screen time today: ") + "${today / 60}${tr("س", "h")} ${today % 60}${tr("د", "m")}",
                color = fg, fontSize = 14.sp,
            )
            val mx = (m.maxOrNull() ?: 1).coerceAtLeast(1)
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                m.forEachIndexed { i, v ->
                    val isToday = i == m.lastIndex
                    val d = LocalDate.now().minusDays((m.lastIndex - i).toLong())
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight((v / mx.toFloat()).coerceIn(0.05f, 1f))
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (isToday) accent else fg.copy(alpha = 0.3f)),
                            )
                        }
                        Text(
                            d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, if (Lang.isAr) java.util.Locale("ar") else java.util.Locale.ENGLISH),
                            color = fg.copy(alpha = 0.7f), fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

// ───────────── قواعد تلقائية ─────────────

val RuleTriggers = listOf(
    tr("لو وصّلت سماعة", "When headphones connect"),
    tr("لو الموبايل بيشحن", "When charging"),
    tr("لو اتصلت بواي فاي", "When on Wi-Fi"),
    tr("بالليل (بعد ساعة الاسترخاء)", "At night (after relax hour)"),
)
val RuleActions = listOf(
    tr("شغّل وضع التركيز", "Turn on Focus"),
    tr("شغّل وضع الاسترخاء", "Turn on night relax"),
    tr("طبّق ثيم الليل", "Apply night theme"),
    tr("طبّق ثيم النهار", "Apply day theme"),
)

fun ruleLabel(r: String): String {
    val p = r.split("|")
    val t = RuleTriggers.getOrNull(p.getOrNull(0)?.toIntOrNull() ?: -1) ?: "?"
    val a = RuleActions.getOrNull(p.getOrNull(1)?.toIntOrNull() ?: -1) ?: "?"
    return "$t ← $a"
}

fun ruleActive(t: Int, st: DeviceStatus, s: AuraState, hour: Int): Boolean = when (t) {
    0 -> st.headphones
    1 -> st.charging
    2 -> st.wifi
    else -> hour >= s.relaxHour || hour < s.dayHour
}

@Composable
fun RuleDialog(ui: Ui, onDismiss: () -> Unit) {
    var t by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var a by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    @Composable
    fun chips(options: List<String>, sel: Int, on: (Int) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, label ->
                Text(
                    (if (i == sel) "● " else "○ ") + label,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable { on(i) }.padding(vertical = 6.dp, horizontal = 4.dp),
                )
            }
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("قاعدة جديدة", "New rule")) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("الشرط", "When"), fontSize = 13.sp)
                chips(RuleTriggers, t) { t = it }
                Text(tr("يعمل إيه", "Then"), fontSize = 13.sp)
                chips(RuleActions, a) { a = it }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                ui.vm.update { it.copy(rules = it.rules + "$t|$a") }
                onDismiss()
            }) { Text(tr("إضافة", "Add")) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(tr("إلغاء", "Cancel")) } },
    )
}
