package com.aura.launcher

import androidx.compose.foundation.layout.Arrangement
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
