package com.aura.launcher

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

val BuiltinKinds = listOf(
    "clock" to ("ساعة رقمية" to "Digital clock"),
    "analog" to ("ساعة عقارب" to "Analog clock"),
    "date" to ("التاريخ" to "Date"),
    "calendar" to ("تقويم الشهر" to "Month calendar"),
    "battery" to ("البطارية" to "Battery"),
    "storage" to ("التخزين والذاكرة" to "Storage & memory"),
    "notes" to ("ملاحظات سريعة" to "Quick notes"),
    "apps" to ("الأكثر استخداماً" to "Most used apps"),
    "search" to ("شريط البحث" to "Search bar"),
)

val WidgetBgChoices = listOf(
    0 to ("لوحة" to "Panel"),
    1 to ("شفاف" to "Clear"),
    2 to ("زجاجي" to "Glass"),
    3 to ("لون Aura" to "Accent"),
)

val WidgetRadiusChoices = listOf(
    0 to ("مربع" to "Square"),
    14 to ("ناعم" to "Soft"),
    24 to ("مدوّر" to "Round"),
    40 to ("كبسولة" to "Pill"),
)

@Composable
private fun rememberNow(periodMs: Long): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = LocalDateTime.now()
            delay(periodMs)
        }
    }
    return now
}

/** ودجتس داخلية من Aura: بتشتغل على أي جهاز، ومتخصصة (حجم/شكل/خلفية) من قائمة الضغط المطوّل. */
@Composable
fun BuiltinWidget(
    item: HomeItem.Builtin,
    state: AuraState,
    apps: Map<String, AppEntry>,
    pal: AuraPalette,
    ui: Ui,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(item.radius.dp)
    val bgColor = when (item.bg) {
        0 -> pal.panel
        1 -> Color.Transparent
        2 -> Color.White.copy(alpha = 0.14f)
        else -> pal.accent.copy(alpha = 0.9f)
    }
    val fg = when (item.bg) {
        1 -> pal.homeText
        3 -> if (pal.accent.luminance() > 0.5f) Color.Black else Color.White
        else -> pal.onPanel
    }
    var box = modifier.clip(shape).background(bgColor)
    if (item.bg == 0 || item.bg == 2) box = box.border(1.dp, pal.border, shape)

    Box(box) {
        when (item.kind) {
            "battery" -> BatteryWidget(fg, pal.accent)
            "search" -> Row(
                Modifier.fillMaxSize().clickable { ui.openDrawer(true) }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔍", fontSize = 18.sp)
                Text(
                    tr("ابحث في التطبيقات…", "Search apps…"),
                    color = fg.copy(alpha = 0.7f), fontSize = 16.sp,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            "analog" -> Box(
                Modifier.fillMaxSize().clickable { ui.openDrawer(false) }.padding(10.dp),
                contentAlignment = Alignment.Center,
            ) { AnalogClock(fg, pal.accent, Modifier.fillMaxSize()) }
            "date" -> DateWidget(fg, pal.accent)
            "calendar" -> CalendarWidget(fg, pal.accent, if (item.bg == 3) fg else pal.accent)
            "storage" -> StorageWidget(fg, if (item.bg == 3) fg else pal.accent)
            "notes" -> Box(
                Modifier.fillMaxSize().clickable { ui.editNote(item) }.padding(16.dp),
            ) {
                Text(
                    item.data.ifBlank { tr("اضغط لكتابة ملاحظة…", "Tap to write a note…") },
                    color = if (item.data.isBlank()) fg.copy(alpha = 0.55f) else fg,
                    fontSize = 15.sp,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            "apps" -> FrequentApps(item, state, apps, ui, fg)
            else -> Box(
                Modifier.fillMaxSize().clickable { ui.openDrawer(false) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                ClockHeader(
                    style = ClockStyle.BIG, color = fg, shadow = Color.Transparent, scale = 0.85f,
                )
            }
        }
    }
}

@Composable
private fun AnalogClock(fg: Color, accent: Color, modifier: Modifier) {
    val now = rememberNow(1000L)
    Canvas(modifier) {
        val r = size.minDimension / 2f * 0.94f
        val c = center
        drawCircle(fg.copy(alpha = 0.3f), radius = r, center = c, style = Stroke(width = 3f))
        for (i in 0 until 12) {
            val a = (i * 30 - 90) * (Math.PI / 180.0)
            val inner = if (i % 3 == 0) 0.80f else 0.88f
            drawLine(
                fg.copy(alpha = 0.7f),
                Offset(c.x + cos(a).toFloat() * r * inner, c.y + sin(a).toFloat() * r * inner),
                Offset(c.x + cos(a).toFloat() * r * 0.96f, c.y + sin(a).toFloat() * r * 0.96f),
                strokeWidth = if (i % 3 == 0) 5f else 3f, cap = StrokeCap.Round,
            )
        }
        fun hand(deg: Double, len: Float, w: Float, col: Color) {
            val a = (deg - 90) * (Math.PI / 180.0)
            drawLine(
                col, c,
                Offset(c.x + cos(a).toFloat() * r * len, c.y + sin(a).toFloat() * r * len),
                strokeWidth = w, cap = StrokeCap.Round,
            )
        }
        val h = (now.hour % 12) + now.minute / 60.0
        hand(h * 30.0, 0.5f, 9f, fg)
        hand(now.minute * 6.0 + now.second / 10.0, 0.74f, 6f, fg)
        hand(now.second * 6.0, 0.82f, 3f, accent)
        drawCircle(accent, radius = 7f, center = c)
    }
}

@Composable
private fun DateWidget(fg: Color, accent: Color) {
    val now = rememberNow(60_000L)
    val loc = Locale.getDefault()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(now.format(DateTimeFormatter.ofPattern("EEEE", loc)), color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(now.dayOfMonth.toString(), color = fg, fontSize = 52.sp, fontWeight = FontWeight.Light)
        Text(now.format(DateTimeFormatter.ofPattern("MMMM yyyy", loc)), color = fg.copy(alpha = 0.75f), fontSize = 14.sp)
    }
}

@Composable
private fun CalendarWidget(fg: Color, accent: Color, onAccent: Color) {
    val now = rememberNow(60_000L)
    val loc = Locale.getDefault()
    val ym = YearMonth.from(now)
    val fdow = WeekFields.of(loc).firstDayOfWeek
    val offset = (ym.atDay(1).dayOfWeek.value - fdow.value + 7) % 7
    val days = ym.lengthOfMonth()
    val weeks = (offset + days + 6) / 7
    val onToday = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            now.format(DateTimeFormatter.ofPattern("MMMM yyyy", loc)),
            color = onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    fdow.plus(i.toLong()).getDisplayName(java.time.format.TextStyle.NARROW, loc),
                    color = fg.copy(alpha = 0.55f), fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (d in 0 until 7) {
                    val day = w * 7 + d - offset + 1
                    val isToday = day == now.dayOfMonth
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            if (isToday) Box(Modifier.size(22.dp).clip(CircleShape).background(accent))
                            Text(
                                day.toString(), fontSize = 12.sp,
                                color = if (isToday) onToday else fg,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, fg: Color, fill: Color) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(fg.copy(alpha = 0.18f)),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(fill))
    }
}

private fun gb(bytes: Long): String = String.format(Locale.US, "%.1f", bytes / 1_073_741_824.0)

@Composable
private fun StorageWidget(fg: Color, fill: Color) {
    val context = LocalContext.current
    val info = remember {
        val sf = StatFs(Environment.getDataDirectory().path)
        val total = sf.blockCountLong * sf.blockSizeLong
        val free = sf.availableBlocksLong * sf.blockSizeLong
        val mi = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(mi)
        longArrayOf(total, total - free, mi.totalMem, mi.totalMem - mi.availMem)
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            tr("التخزين  ${gb(info[1])} / ${gb(info[0])} GB", "Storage  ${gb(info[1])} / ${gb(info[0])} GB"),
            color = fg, fontSize = 13.sp,
        )
        Bar(if (info[0] > 0) info[1].toFloat() / info[0] else 0f, fg, fill)
        Text(
            tr("الذاكرة  ${gb(info[3])} / ${gb(info[2])} GB", "Memory  ${gb(info[3])} / ${gb(info[2])} GB"),
            color = fg, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp),
        )
        Bar(if (info[2] > 0) info[3].toFloat() / info[2] else 0f, fg, fill)
    }
}

@Composable
private fun FrequentApps(item: HomeItem.Builtin, state: AuraState, apps: Map<String, AppEntry>, ui: Ui, fg: Color) {
    val n = state.colsOf(item).coerceAtLeast(2)
    val list = remember(apps, state.usage, state.hidden, n) {
        val used = apps.values
            .filter { it.key !in state.hidden && (state.usage[it.key]?.count ?: 0) > 0 }
            .sortedByDescending { state.usage[it.key]?.count ?: 0 }
        (used + state.dock.mapNotNull { apps[it] }).distinctBy { it.key }.take(n)
    }
    Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (app in list) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { ui.launch(app, null) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(app.icon, app.label, Modifier.size(44.dp))
                Text(
                    app.label, color = fg, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun BatteryWidget(fg: Color, accent: Color) {
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

    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (level >= 0) "$level%" else "--",
                color = fg, fontSize = 34.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (charging) tr("⚡ بيشحن", "⚡ Charging") else tr("البطارية", "Battery"),
                color = fg.copy(alpha = 0.7f), fontSize = 14.sp,
            )
        }
        Bar(
            level.coerceIn(0, 100) / 100f, fg,
            if (level in 0..15 && !charging) Color(0xFFFF5252) else accent,
        )
    }
}

/** حوار تعديل الودجت: الحجم والشكل والخلفية. */
@Composable
fun WidgetEditDialog(ui: Ui, state: AuraState, item: HomeItem.Builtin, onDismiss: () -> Unit) {
    val vm = ui.vm
    val maxCols = state.columns
    val maxRows = minOf(4, state.rows)
    fun edit(f: (HomeItem.Builtin) -> HomeItem.Builtin) = vm.update { it.updateBuiltin(item.uid, f) }
    val curCols = if (item.cols <= 0) maxCols else item.cols.coerceAtMost(maxCols)
    val curRows = if (item.rows > 0) item.rows else defaultBuiltinRows(item.kind)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("تعديل الودجت", "Edit widget")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(tr("العرض (أعمدة)", "Width (columns)"), fontSize = 13.sp)
                ChipRow(
                    (2..maxCols).map { it to (if (it == maxCols) tr("كامل", "Full") else it.toString()) },
                    curCols,
                ) { v -> edit { it.copy(cols = if (v >= maxCols) 0 else v) } }
                Text(tr("الارتفاع (صفوف)", "Height (rows)"), fontSize = 13.sp)
                ChipRow((1..maxRows).map { it to it.toString() }, curRows) { v -> edit { it.copy(rows = v) } }
                Text(tr("الحواف", "Corners"), fontSize = 13.sp)
                ChipRow(WidgetRadiusChoices.map { (v, n) -> v to tr(n.first, n.second) }, item.radius) { v ->
                    edit { it.copy(radius = v) }
                }
                Text(tr("الخلفية", "Background"), fontSize = 13.sp)
                ChipRow(WidgetBgChoices.map { (v, n) -> v to tr(n.first, n.second) }, item.bg) { v ->
                    edit { it.copy(bg = v) }
                }
                Text(
                    tr("لتغيير المكان: اضغط مطولاً على الودجت واسحبها.", "To move it: long-press the widget and drag."),
                    fontSize = 12.sp, color = Color.Gray,
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(tr("تم", "Done")) } },
    )
}

@Composable
private fun ChipRow(options: List<Pair<Int, String>>, selected: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (v, label) ->
            val on = v == selected
            Text(
                label,
                fontSize = 13.sp,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPick(v) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
