package com.aura.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

// ───────────── معاينة الإشعارات (آخر رسايل كل تطبيق) ─────────────

data class PreviewMsg(val who: String, val text: String, val time: Long)

object Previews {
    private val map = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, List<PreviewMsg>>>()

    fun put(pkg: String, key: String, msgs: List<PreviewMsg>) {
        if (msgs.isEmpty()) { remove(pkg, key); return }
        map.getOrPut(pkg) { java.util.concurrent.ConcurrentHashMap() }[key] = msgs
    }

    fun remove(pkg: String, key: String) { map[pkg]?.remove(key) }

    fun latest(pkg: String, n: Int = 3): List<PreviewMsg> =
        map[pkg]?.values?.flatten()?.sortedByDescending { it.time }?.distinctBy { it.who + it.text }?.take(n) ?: emptyList()
}

// ───────────── الـ Feed: أخبار + مهام + طقس + تقويم ─────────────

private data class NewsItem(val title: String, val link: String, val source: String)

private fun parseRss(xml: String, limit: Int = 12): List<NewsItem> {
    val out = ArrayList<NewsItem>()
    runCatching {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(StringReader(xml))
        var ev = p.eventType
        var inItem = false
        var title = ""; var link = ""
        var channel = ""
        var tag = ""
        while (ev != XmlPullParser.END_DOCUMENT && out.size < limit) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "item" || tag == "entry") { inItem = true; title = ""; link = "" }
                    if (inItem && tag == "link") {
                        val href = p.getAttributeValue(null, "href")   // Atom
                        if (!href.isNullOrBlank()) link = href
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val t = p.text.trim()
                    if (t.isNotEmpty()) {
                        if (inItem) { if (tag == "title") title += t else if (tag == "link" && link.isBlank()) link = t }
                        else if (tag == "title" && channel.isEmpty()) channel = t
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item" || p.name == "entry") {
                        inItem = false
                        if (title.isNotBlank()) out += NewsItem(title, link, channel)
                    }
                    tag = ""
                }
            }
            ev = p.next()
        }
    }
    return out
}

private val DefaultFeeds = listOf(
    "https://feeds.bbci.co.uk/arabic/rss.xml",
    "https://feeds.bbci.co.uk/news/rss.xml",
)

@Composable
fun FeedScreen(ui: Ui, state: AuraState, pal: AuraPalette) {
    val ctx = LocalContext.current
    val vm = ui.vm
    var reload by remember { mutableIntStateOf(0) }
    val feeds = remember(state.feedUrl, Lang.code) {
        if (state.feedUrl.isNotBlank()) listOf(state.feedUrl.trim()) else listOf(if (Lang.isAr) DefaultFeeds[0] else DefaultFeeds[1])
    }
    val news by produceState<List<NewsItem>?>(null, feeds, reload) {
        value = null
        value = withContext(Dispatchers.IO) {
            feeds.flatMap { u -> runCatching { parseRss(httpText(u)) }.getOrDefault(emptyList()) }
        }
    }
    val card = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(pal.panel).border(1.dp, pal.border, RoundedCornerShape(22.dp))
    var newTask by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(pal.drawerBg)
            .pointerInput(Unit) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (dx < -120f) ui.feedOpen = false },
                    onDragCancel = {},
                    onHorizontalDrag = { _, d -> dx += d },
                )
            },
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("لك اليوم", "Today"), color = pal.onPanel, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("⟳", color = pal.accent, fontSize = 22.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { reload++ }.padding(8.dp))
                Text("✕", color = pal.onPanel, fontSize = 20.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { ui.feedOpen = false }.padding(8.dp))
            }

            // الطقس
            Box(card.height(110.dp)) {
                WeatherWidget(HomeItem.Builtin("weather", "feed-w", data = "gps"), ui, pal.onPanel, pal.accent)
            }
            // التقويم
            Box(card.height(150.dp)) { AgendaWidget(ui, pal.onPanel, pal.accent) }

            // المهام
            Column(card.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("✓ " + tr("المهام", "Tasks"), color = pal.onPanel, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                state.tasks.forEachIndexed { i, raw ->
                    val done = raw.startsWith("1|")
                    val text = raw.drop(2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (done) "☑" else "☐", color = pal.accent, fontSize = 22.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                vm.update { s -> s.copy(tasks = s.tasks.toMutableList().also { it[i] = (if (done) "0|" else "1|") + text }) }
                            }.padding(end = 10.dp),
                        )
                        Text(
                            text, color = pal.onPanel.copy(alpha = if (done) 0.45f else 1f), fontSize = 15.sp,
                            textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f),
                        )
                        Text("✕", color = pal.onPanel.copy(alpha = 0.5f), modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            vm.update { s -> s.copy(tasks = s.tasks.filterIndexed { j, _ -> j != i }) }
                        }.padding(6.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = newTask, onValueChange = { newTask = it }, singleLine = true,
                        textStyle = TextStyle(color = pal.onPanel, fontSize = 15.sp),
                        cursorBrush = SolidColor(pal.accent),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(pal.field).padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            if (newTask.isEmpty()) Text(tr("مهمة جديدة…", "New task…"), color = pal.onPanel.copy(alpha = 0.45f), fontSize = 15.sp)
                            inner()
                        },
                    )
                    Text("＋", color = pal.accent, fontSize = 26.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                        val t = newTask.trim()
                        if (t.isNotEmpty()) { vm.update { s -> s.copy(tasks = s.tasks + "0|$t") }; newTask = "" }
                    }.padding(horizontal = 12.dp))
                }
            }

            // الأخبار
            Column(card.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📰 " + tr("الأخبار", "News"), color = pal.onPanel, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                val list = news
                when {
                    list == null -> Text(tr("جاري التحميل…", "Loading…"), color = pal.onPanel.copy(alpha = 0.6f), fontSize = 14.sp)
                    list.isEmpty() -> Text(
                        tr("تعذّر تحميل الأخبار. تأكد من الإنترنت أو غيّر رابط الـ RSS من الإعدادات.", "Couldn't load news. Check your connection or change the RSS URL in Settings."),
                        color = pal.onPanel.copy(alpha = 0.6f), fontSize = 14.sp,
                    )
                    else -> list.forEach { n ->
                        Text(
                            n.title, color = pal.onPanel, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                if (n.link.isNotBlank()) ui.actions.openUrl(n.link)
                            }.padding(vertical = 4.dp),
                        )
                    }
                }
            }
            Box(Modifier.size(24.dp))
        }
    }
}
