package com.aura.launcher

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.provider.CalendarContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume

/** قياسات الأداء البسيطة اللي بتظهر في شاشة التشخيص. */
object Perf {
    @Volatile var loadMs = 0L
    @Volatile var appCount = 0
}

// ───────────── الموقع التلقائي ─────────────

object DeviceLoc {
    @Volatile private var last: Geo.Place? = null

    fun granted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    suspend fun get(ctx: Context): Geo.Place? = withContext(Dispatchers.IO) {
        if (!granted(ctx)) return@withContext last
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return@withContext last
        var loc: Location? = null
        for (p in runCatching { lm.getProviders(true) }.getOrDefault(emptyList())) {
            val l = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (loc == null || l.time > loc.time) loc = l
        }
        if (loc == null) {
            val provider = runCatching { lm.getProviders(true).firstOrNull { it != LocationManager.PASSIVE_PROVIDER } }.getOrNull()
            if (provider != null) {
                loc = withTimeoutOrNull(12_000L) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val sig = CancellationSignal()
                        cont.invokeOnCancellation { sig.cancel() }
                        runCatching {
                            lm.getCurrentLocation(provider, sig, ctx.mainExecutor) { cont.resume(it) }
                        }.onFailure { cont.resume(null) }
                    }
                }
            }
        }
        val l = loc ?: return@withContext last
        val name = runCatching {
            android.location.Geocoder(ctx, if (Lang.isAr) Locale("ar") else Locale.ENGLISH)
                .getFromLocation(l.latitude, l.longitude, 1)?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull() ?: tr("موقعك", "Your location")
        Geo.Place(name, l.latitude, l.longitude).also { last = it }
    }
}

/** "gps" = موقع الجهاز، غير كده اسم مدينة. */
suspend fun resolvePlace(ctx: Context, data: String): Geo.Place? =
    if (data == "gps") DeviceLoc.get(ctx) else Geo.find(data)

// ───────────── آيات ─────────────

private val Ayat = listOf(
    "إِنَّ مَعَ الْعُسْرِ يُسْرًا" to "الشرح ٦",
    "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ" to "الرعد ٢٨",
    "وَمَن يَتَوَكَّلْ عَلَى اللَّهِ فَهُوَ حَسْبُهُ" to "الطلاق ٣",
    "فَاذْكُرُونِي أَذْكُرْكُمْ" to "البقرة ١٥٢",
    "وَقُل رَّبِّ زِدْنِي عِلْمًا" to "طه ١١٤",
    "إِنَّ اللَّهَ مَعَ الصَّابِرِينَ" to "البقرة ١٥٣",
    "وَلَسَوْفَ يُعْطِيكَ رَبُّكَ فَتَرْضَىٰ" to "الضحى ٥",
    "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ" to "البقرة ٢٠١",
    "وَاصْبِرْ فَإِنَّ اللَّهَ لَا يُضِيعُ أَجْرَ الْمُحْسِنِينَ" to "هود ١١٥",
    "لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا" to "البقرة ٢٨٦",
    "وَهُوَ مَعَكُمْ أَيْنَ مَا كُنتُمْ" to "الحديد ٤",
    "إِنَّ اللَّهَ لَا يُغَيِّرُ مَا بِقَوْمٍ حَتَّىٰ يُغَيِّرُوا مَا بِأَنفُسِهِمْ" to "الرعد ١١",
    "وَرَحْمَتِي وَسِعَتْ كُلَّ شَيْءٍ" to "الأعراف ١٥٦",
    "ادْعُونِي أَسْتَجِبْ لَكُمْ" to "غافر ٦٠",
    "وَإِذَا سَأَلَكَ عِبَادِي عَنِّي فَإِنِّي قَرِيبٌ" to "البقرة ١٨٦",
    "حَسْبُنَا اللَّهُ وَنِعْمَ الْوَكِيلُ" to "آل عمران ١٧٣",
    "وَلَا تَهِنُوا وَلَا تَحْزَنُوا وَأَنتُمُ الْأَعْلَوْنَ إِن كُنتُم مُّؤْمِنِينَ" to "آل عمران ١٣٩",
    "فَاللَّهُ خَيْرٌ حَافِظًا وَهُوَ أَرْحَمُ الرَّاحِمِينَ" to "يوسف ٦٤",
    "إِنَّ رَحْمَتَ اللَّهِ قَرِيبٌ مِّنَ الْمُحْسِنِينَ" to "الأعراف ٥٦",
    "وَقُولُوا لِلنَّاسِ حُسْنًا" to "البقرة ٨٣",
)

@Composable
fun AyatWidget(fg: Color, accent: Color) {
    var idx by remember { mutableIntStateOf(java.time.LocalDate.now().dayOfYear % Ayat.size) }
    val (text, ref) = Ayat[idx]
    Column(
        Modifier.fillMaxSize().clickable { idx = (idx + 1) % Ayat.size }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("﴿ $text ﴾", color = fg, fontSize = 18.sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Text(ref, color = accent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

// ───────────── أقرب المواعيد ─────────────

private data class Appt(val title: String, val begin: Long, val allDay: Boolean)

private fun queryAppointments(ctx: Context): List<Appt> = runCatching {
    val now = System.currentTimeMillis()
    val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(b, now)
    ContentUris.appendId(b, now + 7L * 24 * 3600 * 1000)
    val out = ArrayList<Appt>()
    ctx.contentResolver.query(
        b.build(),
        arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
        null, null, CalendarContract.Instances.BEGIN + " ASC",
    )?.use { c ->
        while (c.moveToNext() && out.size < 4) {
            out += Appt(c.getString(0) ?: "", c.getLong(1), c.getInt(2) == 1)
        }
    }
    out
}.getOrDefault(emptyList())

@Composable
fun AgendaWidget(ui: Ui, fg: Color, accent: Color) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val has = remember(tick) { ui.actions.hasCalendarPermission() }
    val events by produceState(emptyList<Appt>(), tick, has) {
        value = if (has) withContext(Dispatchers.IO) { queryAppointments(ctx) } else emptyList()
    }
    val fmt = remember(Lang.code) {
        DateTimeFormatter.ofPattern(tr("EEE h:mm a", "EEE h:mm a"), if (Lang.isAr) Locale("ar") else Locale.ENGLISH)
    }
    Column(
        Modifier.fillMaxSize().clickable { if (!has) ui.actions.requestCalendar { tick++ } else tick++ }.padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            !has -> Text(tr("اضغط لإعطاء إذن التقويم", "Tap to allow calendar access"), color = fg.copy(alpha = 0.7f), fontSize = 14.sp)
            events.isEmpty() -> Text(tr("مفيش مواعيد الأسبوع ده 🎉", "No events this week 🎉"), color = fg.copy(alpha = 0.7f), fontSize = 14.sp)
            else -> {
                Text(tr("مواعيدك القادمة", "Upcoming"), color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                events.take(3).forEach { e ->
                    val whenText = if (e.allDay) tr("طول اليوم", "All day") else
                        Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).format(fmt)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.title.ifBlank { tr("بدون عنوان", "Untitled") }, color = fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(whenText, color = fg.copy(alpha = 0.65f), fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

// ───────────── ستاك ودجت ─────────────

val StackKinds = listOf(
    "weather" to ("الطقس" to "Weather"),
    "prayer" to ("الصلاة" to "Prayer"),
    "ayat" to ("آية" to "Verse"),
    "agenda" to ("مواعيد" to "Agenda"),
    "calendar" to ("تقويم" to "Calendar"),
    "date" to ("التاريخ" to "Date"),
    "clock" to ("ساعة" to "Clock"),
    "battery" to ("بطارية" to "Battery"),
    "storage" to ("تخزين" to "Storage"),
    "music" to ("موسيقى" to "Music"),
    "usage7" to ("وقت الشاشة" to "Screen time"),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StackWidget(
    item: HomeItem.Builtin,
    state: AuraState,
    apps: Map<String, AppEntry>,
    pal: AuraPalette,
    ui: Ui,
    modifier: Modifier,
) {
    val kinds = item.data.split(",").filter { k -> StackKinds.any { it.first == k } }
        .ifEmpty { listOf("weather", "prayer", "ayat") }
    val pager = rememberPagerState { kinds.size }
    Box(modifier) {
        VerticalPager(pager, Modifier.fillMaxSize()) { i ->
            val k = kinds[i]
            BuiltinWidget(
                item.copy(kind = k, data = if (k == "weather" || k == "prayer") "gps" else "", cols = 0, rows = 0),
                state, apps, pal, ui, Modifier.fillMaxSize(),
            )
        }
        if (kinds.size > 1) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                kinds.indices.forEach { i ->
                    Box(
                        Modifier.size(if (i == pager.currentPage) 6.dp else 4.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) pal.accent else pal.onPanel.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

// ───────────── شاشة الأذونات ─────────────

@Composable
fun PermissionsDialog(ui: Ui, onDismiss: () -> Unit) {
    val a = ui.actions
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    data class P(val title: String, val why: String, val ok: Boolean, val go: () -> Unit)
    val list = remember(tick) {
        listOf(
            P(tr("الوصول للإشعارات", "Notification access"),
                tr("علشان الأرقام على أيقونات التطبيقات وودجت الموسيقى. بنعدّ بس ومبنقرأش المحتوى.", "For badge counts and the music widget. We only count, never read."),
                a.notificationAccessEnabled()) { a.openNotificationAccess() },
            P(tr("بيانات الاستخدام", "Usage access"),
                tr("علشان حدود الاستخدام اليومي للتطبيقات.", "For daily app usage limits."),
                a.hasUsageAccess()) { a.openUsageAccess() },
            P(tr("الموقع", "Location"),
                tr("علشان مواقيت الصلاة والطقس يتحدّدوا تلقائي على مكانك.", "So prayer times and weather follow your location."),
                a.hasLocationPermission()) { a.requestLocation { tick++ } },
            P(tr("التقويم", "Calendar"),
                tr("علشان ودجت المواعيد القادمة.", "For the upcoming events widget."),
                a.hasCalendarPermission()) { a.requestCalendar { tick++ } },
            P(tr("جهات الاتصال", "Contacts"),
                tr("علشان البحث عن الأسماء من الدرج.", "To search contacts from the drawer."),
                a.hasContactsPermission()) { a.requestContacts { tick++ } },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("الأذونات", "Permissions")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(tr("كل إذن اختياري، وبتفعّل اللي محتاجه بس.", "Every permission is optional — enable only what you need."), fontSize = 13.sp)
                list.forEach { p ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text((if (p.ok) "✅ " else "⬜ ") + p.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (!p.ok) TextButton(onClick = p.go) { Text(tr("تفعيل", "Enable")) }
                        }
                        Text(p.why, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("تم", "Done")) } },
    )
}

// ───────────── التشخيص + الأخطاء + التحديث ─────────────

object CrashLog {
    fun install(ctx: Context) {
        val f = java.io.File(ctx.filesDir, "crash.txt")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { f.writeText(java.util.Date().toString() + "\n" + android.util.Log.getStackTraceString(e)) }
            runCatching {
                val p = ctx.getSharedPreferences("aura_crash", Context.MODE_PRIVATE)
                val recent = (p.getString("t", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }
                p.edit().putString("t", (recent + System.currentTimeMillis()).takeLast(5).joinToString(",")).commit()
            }
            prev?.uncaughtException(t, e)
        }
    }
    /** كراشين أو أكتر في آخر دقيقتين = الوضع الآمن. */
    fun safeModeNeeded(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences("aura_crash", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        return (p.getString("t", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.count { now - it < 120_000L } >= 2
    }
    fun clearRecent(ctx: Context) { ctx.getSharedPreferences("aura_crash", Context.MODE_PRIVATE).edit().clear().apply() }
    fun read(ctx: Context): String? = java.io.File(ctx.filesDir, "crash.txt").takeIf { it.exists() }?.readText()
}

object FrameMonitor {
    @Volatile var frames = 0
    @Volatile var slow = 0
    private var lastNs = 0L
    private val cb = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(ns: Long) {
            if (lastNs != 0L) {
                frames++
                if ((ns - lastNs) > 33_000_000L) slow++
            }
            lastNs = ns
            if (running) android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }
    @Volatile private var running = false
    fun start() { if (running) return; running = true; frames = 0; slow = 0; lastNs = 0; android.view.Choreographer.getInstance().postFrameCallback(cb) }
    fun stop() { running = false }
}

private fun dirSize(f: java.io.File): Long =
    if (f.isFile) f.length() else f.listFiles()?.sumOf { dirSize(it) } ?: 0L

@Composable
fun DiagnosticsDialog(ui: Ui, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        FrameMonitor.start()
        onDispose { FrameMonitor.stop() }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(1000); tick++ }
    }
    val rt = Runtime.getRuntime()
    val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
    val cacheMb = dirSize(ctx.filesDir) / 1_048_576.0
    val crash = remember { CrashLog.read(ctx) }
    val a = ui.actions
    val ok = { b: Boolean -> if (b) "✅" else "⬜" }
    val slowPct = if (FrameMonitor.frames > 0) FrameMonitor.slow * 100 / FrameMonitor.frames else 0
    val report = buildString {
        append("Aura ${a.appVersion()}\n")
        append(tr("التطبيقات المحمّلة: ", "Apps loaded: ")).append(Perf.appCount).append('\n')
        append(tr("زمن تحميل الأيقونات: ", "Icon load time: ")).append(Perf.loadMs).append(" ms\n")
        append(tr("الذاكرة المستخدمة: ", "Memory used: ")).append(usedMb).append(" MB\n")
        append(tr("حجم ملفات Aura: ", "Aura files: ")).append(String.format(Locale.US, "%.1f MB\n", cacheMb))
        append(tr("الفريمات (آخر لحظات): ", "Frames: ")).append(FrameMonitor.frames)
            .append(tr(" — بطيئة ", " — slow ")).append(slowPct).append("%\n")
        append("${ok(a.notificationAccessEnabled())} ").append(tr("إشعارات", "Notifications")).append('\n')
        append("${ok(a.hasUsageAccess())} ").append(tr("بيانات الاستخدام", "Usage")).append('\n')
        append("${ok(a.hasLocationPermission())} ").append(tr("الموقع", "Location")).append('\n')
        append("${ok(a.hasCalendarPermission())} ").append(tr("التقويم", "Calendar")).append('\n')
        append("${ok(a.hasContactsPermission())} ").append(tr("جهات الاتصال", "Contacts")).append('\n')
    }
    tick.let { }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("التشخيص", "Diagnostics")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(report, fontSize = 13.sp)
                if (crash != null) {
                    Text(tr("آخر خطأ مسجّل:", "Last crash:"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                    Text(crash.take(600), fontSize = 10.sp, color = Color.Gray)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("تم", "Done")) } },
        dismissButton = {
            TextButton(onClick = { a.shareText(report + (crash?.let { "\n\n$it" } ?: "")) }) { Text(tr("مشاركة التقرير", "Share report")) }
        },
    )
}

/** آخر إصدار منشور على GitHub: (الرقم، الرابط) */
suspend fun latestRelease(): Pair<String, String>? = withContext(Dispatchers.IO) {
    runCatching {
        val j = JSONObject(httpText("https://api.github.com/repos/sherifebaid26-crypto/laun/releases/latest"))
        j.getString("tag_name").trimStart('v', 'V') to j.getString("html_url")
    }.getOrNull()
}

fun isNewer(latest: String, current: String): Boolean {
    fun p(s: String) = s.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val a = p(latest); val b = p(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
