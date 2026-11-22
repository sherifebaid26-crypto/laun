package com.aura.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class TourPage(val icon: String, val title: String, val body: String)

@Composable
fun TourDialog(onDone: () -> Unit) {
    val pages = remember(Lang.code) {
        listOf(
            TourPage("👋", tr("أهلاً في Aura", "Welcome to Aura"),
                tr("لانشر عربي قابل للتخصيص بالكامل. جولة سريعة (٣٠ ثانية) علشان تعرف أهم الحركات.", "A fully customizable launcher. A 30-second tour of the key moves.")),
            TourPage("☝️", tr("الحركات الأساسية", "Key gestures"),
                tr("• اسحب لأعلى: درج التطبيقات\n• اسحب لتحت: البحث أو الإشعارات (من الإعدادات)\n• اضغط مطولاً على الخلفية: ودجتس، ثيمات، تعديل الشاشة", "• Swipe up: app drawer\n• Swipe down: search/notifications\n• Long-press background: widgets, themes, edit")),
            TourPage("🧩", tr("الودجتس والستاك", "Widgets & stacks"),
                tr("ضيف ودجت من القائمة. «الستاك» بيجمع أكتر من ودجت في مكان واحد، وتبدّل بينهم بالسحب لفوق وتحت. اسحب الحافة لتغيير الحجم.", "Add widgets from the menu. A stack holds several widgets: swipe up/down to switch. Drag the edge to resize.")),
            TourPage("🖐", tr("اسحب وأفلت", "Drag & drop"),
                tr("اسحب أي تطبيق من الدرج للشاشة، أو اضغط مطولاً على أيقونة لتغيير اسمها وشكلها أو نقلها لمجلد. اسحبها لفوق للإزالة.", "Drag apps from the drawer to home; long-press an icon to rename, restyle or move. Drag up to remove.")),
            TourPage("🔔", tr("الأذونات", "Permissions"),
                tr("كل إذن اختياري (إشعارات، موقع، تقويم…). هنعرضهم لك دلوقتي وتفعّل اللي محتاجه بس.", "Every permission is optional. Next you can enable only what you need.")),
        )
    }
    var i by remember { mutableIntStateOf(0) }
    val p = pages[i]
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(p.icon + "  " + p.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.body, fontSize = 15.sp, textAlign = TextAlign.Start)
                Text("${i + 1} / ${pages.size}", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { if (i == pages.lastIndex) onDone() else i++ }) {
                Text(if (i == pages.lastIndex) tr("يلا نبدأ", "Let's go") else tr("التالي", "Next"))
            }
        },
        dismissButton = {
            if (i > 0) TextButton(onClick = { i-- }) { Text(tr("السابق", "Back")) }
            else TextButton(onClick = onDone) { Text(tr("تخطي", "Skip")) }
        },
    )
}

// ───────────── وضع تعديل الشاشة الرئيسية ─────────────

@Composable
fun EditPanel(ui: Ui, state: AuraState, pal: AuraPalette, modifier: Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier
            .padding(12.dp)
            .androidx_clip(24)
            .androidx_bg(pal.panel)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tr("تعديل الشاشة الرئيسية", "Edit home screen"), color = pal.onPanel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditBtn("🖼", tr("الخلفية", "Wallpaper"), pal, Modifier.weight(1f)) { ui.editMode = false; ui.actions.openWallpaperPicker() }
            EditBtn("▦", tr("ودجت", "Widget"), pal, Modifier.weight(1f)) { ui.editMode = false; ui.widgetPicker = true }
            EditBtn("✦", tr("ثيمات", "Themes"), pal, Modifier.weight(1f)) { ui.editMode = false; ui.storeOpen = true }
            EditBtn("⚙", tr("إعدادات", "Settings"), pal, Modifier.weight(1f)) { ui.editMode = false; ui.settingsOpen = true }
        }
        Text(tr("الأعمدة", "Columns"), color = pal.onPanel.copy(alpha = 0.7f), fontSize = 12.sp)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (3..6).forEach { n -> EditChip(n.toString(), state.columns == n, pal) { ui.vm.update { it.copy(columns = n).reflow() } } }
        }
        Text(tr("الصفوف", "Rows"), color = pal.onPanel.copy(alpha = 0.7f), fontSize = 12.sp)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (4..7).forEach { n -> EditChip(n.toString(), state.rows == n, pal) { ui.vm.update { it.copy(rows = n).reflow() } } }
        }
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditBtn("＋", tr("صفحة جديدة", "Add page"), pal, Modifier.weight(1f)) {
                ui.vm.update { it.copy(pages = it.pages + listOf(emptyList())) }
            }
            EditBtn("🗑", tr("حذف الصفحة الحالية (لو فاضية)", "Delete page (if empty)"), pal, Modifier.weight(1f)) {
                val idx = ui.page
                val s = ui.vm.state.value
                if (s.pages.size > 1 && s.pages.getOrNull(idx)?.isEmpty() == true) {
                    ui.vm.update { st ->
                        st.copy(
                            pages = st.pages.filterIndexed { i, _ -> i != idx },
                            lockedPages = st.lockedPages.filter { it != idx }.map { if (it > idx) it - 1 else it }.toSet(),
                        )
                    }
                } else ui.vm.postMessage(tr("الصفحة مش فاضية أو هي الوحيدة", "Page isn't empty or is the only one"))
            }
        }
        TextButton(onClick = { ui.editMode = false }, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
            Text(tr("تم", "Done"))
        }
    }
}

private fun Modifier.androidx_clip(r: Int): Modifier = this.then(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(r.dp)))
private fun Modifier.androidx_bg(c: androidx.compose.ui.graphics.Color): Modifier = this.then(Modifier.background(c))

@Composable
private fun EditBtn(icon: String, label: String, pal: AuraPalette, modifier: Modifier, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(pal.field)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 20.sp)
        Text(label, color = pal.onPanel, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EditChip(label: String, on: Boolean, pal: AuraPalette, onClick: () -> Unit) {
    Text(
        label, color = if (on) androidx.compose.ui.graphics.Color.Black else pal.onPanel, fontSize = 14.sp,
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(if (on) pal.accent else pal.field).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ───────────── اللوحة الجانبية ─────────────

@Composable
fun EdgeHandle(ui: Ui, modifier: Modifier) {
    Box(
        modifier.width(20.dp).fillMaxHeight(0.5f).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                var dx = 0f
                while (true) {
                    val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    dx = ch.position.x - down.position.x
                    if (kotlin.math.abs(dx) > 40f) {
                        ch.consume()
                        if (dx < 0) ui.edgeOpen = true
                        break
                    }
                }
            }
        },
    )
}

@Composable
fun EdgePanel(ui: Ui, state: AuraState, apps: List<AppEntry>, pal: AuraPalette) {
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val list = remember(state.edgeApps, state.usage, apps) {
        val chosen = state.edgeApps.mapNotNull { byKey[it] }
        chosen.ifEmpty {
            apps.filter { (state.usage[it.key]?.count ?: 0) > 0 }.sortedByDescending { state.usage[it.key]?.count ?: 0 }.take(7)
        }
    }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x66000000)).clickable { ui.edgeOpen = false }) {
        androidx.compose.foundation.layout.Column(
            Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
                .padding(vertical = 60.dp)
                .width(96.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                .background(pal.panel)
                .clickable(enabled = false) {}
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(vertical = 12.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            list.forEach { app ->
                androidx.compose.foundation.layout.Column(
                    Modifier.clickable { ui.edgeOpen = false; ui.launch(app) }.padding(4.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    androidx.compose.foundation.Image(app.icon, app.label, Modifier.size(48.dp))
                    Text(app.label, color = pal.onPanel, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            Text(
                "＋ " + tr("تعديل", "Edit"), color = pal.accent, fontSize = 12.sp,
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable {
                    ui.edgeOpen = false
                    ui.picker = PickerRequest(tr("تطبيقات اللوحة الجانبية", "Edge panel apps"), state.edgeApps.toSet()) { sel ->
                        ui.vm.update { it.copy(edgeApps = sel.toList()) }
                    }
                }.padding(8.dp),
            )
        }
    }
}

// ───────────── شريط الحروف في الدرج ─────────────

@Composable
fun AlphaScroller(letters: List<Char>, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onPick: (Char) -> Unit) {
    var h by remember { mutableIntStateOf(1) }
    androidx.compose.foundation.layout.Column(
        modifier.width(22.dp).fillMaxHeight(0.8f)
            .onSizeChanged { h = it.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun pick(y: Float) {
                        val i = (y / h * letters.size).toInt().coerceIn(0, letters.lastIndex)
                        onPick(letters[i])
                    }
                    pick(down.position.y)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        pick(ch.position.y)
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        letters.forEach { Text(it.toString(), color = color, fontSize = 10.sp) }
    }
}
