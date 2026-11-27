package com.aura.launcher

import android.content.ComponentName
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

// ───────────── شبكة بسيطة + بحث المدينة ─────────────

private const val MAX_HTTP_CHARS = 2_000_000

/** https بس، وبحد أقصى للحجم (الأخبار/الطقس/الاقتراحات كلها جاية من الإنترنت). */
internal fun httpText(url: String): String {
    require(url.startsWith("https://")) { "https only" }
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10_000
    c.readTimeout = 15_000
    c.setRequestProperty("User-Agent", "AuraLauncher")
    try {
        if (c.responseCode != 200) error("HTTP ${c.responseCode}")
        c.inputStream.bufferedReader().use { r ->
            val sb = StringBuilder()
            val buf = CharArray(8192)
            while (true) {
                val n = r.read(buf)
                if (n < 0) break
                sb.append(buf, 0, n)
                if (sb.length > MAX_HTTP_CHARS) error("too large")
            }
            return sb.toString()
        }
    } finally {
        c.disconnect()
    }
}

object Geo {
    data class Place(val name: String, val lat: Double, val lon: Double)

    private val cache = HashMap<String, Place>()

    suspend fun find(city: String): Place? {
        val key = city.trim().lowercase()
        if (key.isEmpty()) return null
        cache[key]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val q = URLEncoder.encode(city.trim(), "UTF-8")
                val lang = if (Lang.isAr) "ar" else "en"
                val r = JSONObject(
                    httpText("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=$lang&format=json"),
                ).getJSONArray("results").getJSONObject(0)
                Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"))
                    .also { cache[key] = it }
            }.getOrNull()
        }
    }
}

// ───────────── الصورة ─────────────

@Composable
fun PhotoWidget(item: HomeItem.Builtin, ui: Ui, fg: Color) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, item.uid, item.data) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(File(ctx.filesDir, "photos/${item.uid}.jpg").path)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.fillMaxSize().clickable { ui.pickPhoto(item.uid) }, contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text("🖼  " + tr("اضغط لاختيار صورة", "Tap to choose a photo"), color = fg.copy(alpha = 0.7f), fontSize = 14.sp)
        }
    }
}

// ───────────── الطقس ─────────────

private data class WeatherData(val temp: Int, val code: Int, val hi: Int, val lo: Int, val place: String)

private suspend fun fetchWeather(p: Geo.Place): WeatherData? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val j = JSONObject(
                httpText(
                    "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
                        "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
                        "&timezone=auto&forecast_days=1",
                ),
            )
            val cur = j.getJSONObject("current")
            val daily = j.getJSONObject("daily")
            WeatherData(
                cur.getDouble("temperature_2m").roundToInt(),
                cur.getInt("weather_code"),
                daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                p.name,
            )
        }.getOrNull()
    }
}

private fun weatherLook(code: Int): Pair<String, String> = when (code) {
    0 -> "☀️" to tr("صافي", "Clear")
    1, 2 -> "🌤️" to tr("غائم جزئياً", "Partly cloudy")
    3 -> "☁️" to tr("غائم", "Cloudy")
    45, 48 -> "🌫️" to tr("ضباب", "Fog")
    in 51..57 -> "🌦️" to tr("رذاذ", "Drizzle")
    in 61..67, in 80..82 -> "🌧️" to tr("مطر", "Rain")
    in 71..77, 85, 86 -> "❄️" to tr("ثلج", "Snow")
    in 95..99 -> "⛈️" to tr("عاصفة", "Storm")
    else -> "🌡️" to ""
}

@Composable
fun WeatherWidget(item: HomeItem.Builtin, ui: Ui, fg: Color, accent: Color) {
    val ctx = LocalContext.current
    val city = item.data
    var tick by remember { mutableIntStateOf(0) }
    val needPerm = city == "gps" && !ui.actions.hasLocationPermission()
    val w by produceState<WeatherData?>(null, city, tick) {
        value = null
        if (city.isNotBlank() && !(city == "gps" && !ui.actions.hasLocationPermission())) {
            while (true) {
                resolvePlace(ctx, city)?.let { p -> fetchWeather(p)?.let { value = it } }
                delay(30 * 60_000L)
            }
        }
    }
    Box(
        Modifier.fillMaxSize().clickable {
            if (needPerm) ui.actions.requestLocation { tick++ } else ui.editCity(item)
        }.padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val d = w
        when {
            needPerm -> Text(tr("اضغط لإعطاء إذن الموقع", "Tap to allow location"), color = fg.copy(alpha = 0.7f), fontSize = 15.sp)
            city.isBlank() -> Text(tr("اضغط لتحديد المدينة", "Tap to set your city"), color = fg.copy(alpha = 0.7f), fontSize = 15.sp)
            d == null -> Text(tr("جاري التحميل…", "Loading…"), color = fg.copy(alpha = 0.7f), fontSize = 14.sp)
            else -> {
                val (emoji, desc) = weatherLook(d.code)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 40.sp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("${d.temp}°", color = fg, fontSize = 38.sp, fontWeight = FontWeight.Light)
                        Text("${d.place} · $desc", color = fg.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("↑${d.hi}°  ↓${d.lo}°", color = accent, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ───────────── مواعيد الصلاة (الهيئة المصرية: فجر 19.5° وعشاء 17.5°، عصر شافعي) ─────────────

object Prayer {
    val names: List<String>
        get() = listOf(
            tr("الفجر", "Fajr"), tr("الشروق", "Sunrise"), tr("الظهر", "Dhuhr"),
            tr("العصر", "Asr"), tr("المغرب", "Maghrib"), tr("العشاء", "Isha"),
        )

    private fun fix(a: Double, m: Double) = a - m * floor(a / m)
    private const val D2R = Math.PI / 180.0

    /** بترجّع [فجر، شروق، ظهر، عصر، مغرب، عشاء] بالساعات (توقيت محلي). */
    fun times(date: LocalDate, lat: Double, lon: Double, tz: Double): DoubleArray {
        var y = date.year
        var m = date.monthValue
        val d = date.dayOfMonth
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        val jd = floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5 - lon / (15 * 24)
        val dd = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * dd, 360.0)
        val q = fix(280.459 + 0.98564736 * dd, 360.0)
        val l = fix(q + 1.915 * sin(g * D2R) + 0.020 * sin(2 * g * D2R), 360.0)
        val e = 23.439 - 0.00000036 * dd
        val ra = fix(atan2(cos(e * D2R) * sin(l * D2R), cos(l * D2R)) / D2R / 15, 24.0)
        val eqt = q / 15 - ra
        val decl = asin(sin(e * D2R) * sin(l * D2R)) / D2R
        val dhuhr = fix(12 - eqt, 24.0) + tz - lon / 15

        fun at(angle: Double, ccw: Boolean): Double {
            val c = (-sin(angle * D2R) - sin(decl * D2R) * sin(lat * D2R)) / (cos(decl * D2R) * cos(lat * D2R))
            val t = acos(max(-1.0, min(1.0, c))) / D2R / 15
            return dhuhr + if (ccw) -t else t
        }
        val asrAngle = -atan(1.0 / (1.0 + tan(abs(lat - decl) * D2R))) / D2R
        return doubleArrayOf(at(19.5, true), at(0.833, true), dhuhr, at(asrAngle, false), at(0.833, false), at(17.5, false))
    }

    fun fmt(h: Double): String {
        val total = (fix(h, 24.0) * 60).roundToInt() % 1440
        val hh = total / 60
        val mm = total % 60
        val h12 = if (hh % 12 == 0) 12 else hh % 12
        return String.format("%d:%02d %s", h12, mm, if (hh < 12) tr("ص", "AM") else tr("م", "PM"))
    }
}

@Composable
fun PrayerWidget(item: HomeItem.Builtin, ui: Ui, fg: Color, accent: Color) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val needPerm = item.data == "gps" && !ui.actions.hasLocationPermission()
    val place by produceState<Geo.Place?>(null, item.data, tick) {
        value = if (item.data.isBlank() || needPerm) null else resolvePlace(ctx, item.data)
    }
    val now = rememberNow(30_000L)
    val p = place
    Box(
        Modifier.fillMaxSize().clickable {
            if (needPerm) ui.actions.requestLocation { tick++ } else ui.editCity(item)
        }.padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (needPerm) {
            Text(tr("اضغط لإعطاء إذن الموقع", "Tap to allow location"), color = fg.copy(alpha = 0.7f))
        } else if (item.data.isBlank()) {
            Text(tr("اضغط لتحديد المدينة", "Tap to set your city"), color = fg.copy(alpha = 0.7f))
        } else if (p == null) {
            Text(tr("جاري تحديد المدينة…", "Locating city…"), color = fg.copy(alpha = 0.7f))
        } else {
            val tz = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 3600.0
            val t = remember(p, now.toLocalDate()) { Prayer.times(now.toLocalDate(), p.lat, p.lon, tz) }
            val nowH = now.hour + now.minute / 60.0
            val order = intArrayOf(0, 2, 3, 4, 5)
            val nextIdx = order.firstOrNull { t[it] > nowH } ?: 0
            val remain = (if (t[nextIdx] > nowH) t[nextIdx] - nowH else t[nextIdx] + 24 - nowH)
            val rm = (remain * 60).roundToInt()
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Prayer.names[nextIdx] + " " + Prayer.fmt(t[nextIdx]),
                            color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            tr("بعد ${rm / 60}س ${rm % 60}د", "in ${rm / 60}h ${rm % 60}m") + " · " + p.name,
                            color = accent, fontSize = 12.sp, maxLines = 1,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    for (i in order) {
                        val on = i == nextIdx
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Prayer.names[i], color = if (on) accent else fg.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
                            Text(
                                Prayer.fmt(t[i]).replace(" ", "\n"),
                                color = if (on) accent else fg, fontSize = 11.sp, textAlign = TextAlign.Center,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────── أذكار / مسبحة ─────────────

private val Adhkar = listOf(
    "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ",
    "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ",
    "أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ وَأَتُوبُ إِلَيْهِ",
    "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ",
    "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",
    "حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ",
    "سُبْحَانَ اللَّهِ، وَالْحَمْدُ لِلَّهِ، وَلَا إِلَهَ إِلَّا اللَّهُ، وَاللَّهُ أَكْبَرُ",
    "رَبِّ اغْفِرْ لِي وَتُبْ عَلَيَّ",
    "يَا مُقَلِّبَ الْقُلُوبِ ثَبِّتْ قَلْبِي عَلَى دِينِكَ",
    "اللَّهُمَّ أَعِنِّي عَلَى ذِكْرِكَ وَشُكْرِكَ وَحُسْنِ عِبَادَتِكَ",
)

@Composable
fun DhikrWidget(item: HomeItem.Builtin, ui: Ui, fg: Color, accent: Color) {
    val parts = item.data.split("|")
    val idx = (parts.getOrNull(0)?.toIntOrNull() ?: 0).mod(Adhkar.size)
    val count = parts.getOrNull(1)?.toIntOrNull() ?: 0
    fun save(i: Int, c: Int) = ui.vm.update { it.updateBuiltin(item.uid) { b -> b.copy(data = "$i|$c") } }
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).clickable { save(idx, count + 1) },
        ) {
            Text(Adhkar[idx], color = fg, fontSize = 15.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text(tr("اضغط للعد: $count", "Tap to count: $count"), color = accent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            "↻", color = fg, fontSize = 24.sp,
            modifier = Modifier.clickable { save((idx + 1) % Adhkar.size, 0) }.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        )
    }
}

// ───────────── مشغّل الموسيقى ─────────────

@Composable
fun MusicWidget(ui: Ui, fg: Color, accent: Color) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf<String?>(null) }
    var artist by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<MediaController?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val msm = ctx.getSystemService(MediaSessionManager::class.java)
                val list = msm.getActiveSessions(ComponentName(ctx, AuraNotificationService::class.java))
                val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
                controller = c
                title = c?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                artist = c?.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING
                denied = false
            } catch (e: SecurityException) {
                denied = true
                controller = null
            } catch (e: Exception) {
                // مفيش جلسة صوت شغالة
            }
            delay(1500)
        }
    }

    if (denied) {
        Box(Modifier.fillMaxSize().clickable { ui.actions.openNotificationAccess() }.padding(16.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                tr("🎵 فعّل «الوصول للإشعارات» لـ Aura علشان تتحكم في الموسيقى", "🎵 Enable Notification access for Aura to control music"),
                color = fg.copy(alpha = 0.8f), fontSize = 13.sp,
            )
        }
    } else {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title ?: tr("مفيش موسيقى شغالة", "Nothing playing"),
                    color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (artist.isNotBlank()) Text(artist, color = fg.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val btn = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            Text("⏮", color = fg, fontSize = 22.sp, modifier = Modifier.clickable { controller?.transportControls?.skipToPrevious() }.then(btn))
            Text(
                if (playing) "⏸" else "▶", color = accent, fontSize = 26.sp,
                modifier = Modifier.clickable {
                    val t = controller?.transportControls
                    if (playing) t?.pause() else t?.play()
                }.then(btn),
            )
            Text("⏭", color = fg, fontSize = 22.sp, modifier = Modifier.clickable { controller?.transportControls?.skipToNext() }.then(btn))
        }
    }
}
