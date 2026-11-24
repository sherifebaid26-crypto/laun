package com.aura.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
private fun Section(title: String, pal: AuraPalette) {
    Text(
        title,
        color = pal.accent,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
    )
}

@Composable
private fun ActionRow(label: String, pal: AuraPalette, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (danger) Color(0xFFFF5252) else pal.onPanel,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, pal: AuraPalette, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = pal.onPanel, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    pal: AuraPalette,
    shown: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, color = pal.onPanel, modifier = Modifier.weight(1f))
            Text(shown(v), color = pal.onPanel.copy(alpha = 0.7f))
        }
        Slider(
            value = v,
            onValueChange = { v = it },
            valueRange = range,
            steps = steps,
            onValueChangeFinished = { onCommit(v) },
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, pal: AuraPalette, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else pal.onPanel,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) pal.accent else pal.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ScrollRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private fun gestureLabel(a: GestureAction): String = when (a) {
    GestureAction.NONE -> tr("بدون", "None")
    GestureAction.NOTIFICATIONS -> tr("الإشعارات", "Notifications")
    GestureAction.SEARCH -> tr("البحث", "Search")
    GestureAction.DRAWER -> tr("التطبيقات", "Apps")
    GestureAction.SETTINGS -> tr("الإعدادات", "Settings")
    GestureAction.LOCK -> tr("قفل الشاشة", "Lock screen")
    GestureAction.RECENTS -> tr("التطبيقات الأخيرة", "Recents")
    GestureAction.ONE_HAND -> tr("وضع اليد الواحدة", "One-handed")
    GestureAction.FEED -> tr("صفحة Feed", "Feed")
}

private enum class PinStage { NONE, NEW, CONFIRM }

@Composable
fun SettingsScreen(ui: Ui, state: AuraState, apps: List<AppEntry>, pal: AuraPalette) {
    val context = LocalContext.current
    val insets = WindowInsets.systemBars.asPaddingValues()
    val vm = ui.vm

    var pinStage by remember { mutableStateOf(PinStage.NONE) }
    var firstPin by remember { mutableStateOf("") }
    var pinError by remember { mutableIntStateOf(0) }
    var iconPackDialog by remember { mutableStateOf(false) }
    var resetDialog by remember { mutableStateOf(false) }
    var schedDialog by remember { mutableIntStateOf(0) }
    val installedThemes by vm.installed.collectAsState()
    val iconPacks = remember { IconPacks.list(context) }
    val byKey = remember(apps) { apps.associateBy { it.key } }

    Box(Modifier.fillMaxSize().background(pal.drawerBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = insets.calculateTopPadding() + 8.dp,
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "‹",
                        color = pal.onPanel,
                        fontSize = 30.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { ui.settingsOpen = false }
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                    )
                    Text(tr("الإعدادات", "Settings"), color = pal.onPanel, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            item {
                Section(tr("اللغة / Language", "Language"), pal)
                ScrollRow {
                    Chip("العربية", state.lang == "ar", pal) { vm.update { it.copy(lang = "ar") }; vm.reloadApps() }
                    Chip("English", state.lang == "en", pal) { vm.update { it.copy(lang = "en") }; vm.reloadApps() }
                    Chip(tr("لغة الجهاز", "Device language"), state.lang == "auto", pal) { vm.update { it.copy(lang = "auto") }; vm.reloadApps() }
                }
            }

            item {
                ActionRow(tr("تعيين Aura كلانشر افتراضي", "Set Aura as default launcher"), pal) {
                    ui.actions.requestDefaultLauncher()
                }
            }

            item {
                Section(tr("المظهر", "Appearance"), pal)
                ActionRow(tr("متجر الثيمات", "Theme store"), pal) { ui.storeOpen = true }
                if (state.themeId != null) {
                    ActionRow(tr("إلغاء الثيم والرجوع للستايل الافتراضي", "Remove theme (default style)"), pal) {
                        vm.resetTheme()
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeMode.DARK to "Dark",
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.GLASS to "Glass",
                        ThemeMode.COLOR to "Color",
                    ).forEach { (mode, name) ->
                        Chip(name, state.theme == mode, pal) { vm.update { it.copy(theme = mode) } }
                    }
                }
            }
            item {
                Text(
                    tr("لون التمييز", "Accent color"),
                    color = pal.onPanel, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentChoices.forEach { c ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (state.accent == c) 3.dp else 0.dp, pal.onPanel, CircleShape)
                                .clickable { vm.update { it.copy(accent = c) } }
                        )
                    }
                }
            }

            item {
                Text(
                    tr("شكل قائمة الضغط المطوّل", "Long-press menu style"),
                    color = pal.onPanel, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(MenuStyle.IOS to "iPhone", MenuStyle.SAMSUNG to "Samsung", MenuStyle.VIVO to "Vivo")
                        .forEach { (m, n) -> Chip(n, state.menuStyle == m, pal) { vm.update { it.copy(menuStyle = m) } } }
                }
                Text(
                    tr("الساعة على الشاشة الرئيسية", "Home screen clock"),
                    color = pal.onPanel, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ClockStyle.NONE to tr("بدون", "None"),
                        ClockStyle.SMALL to tr("صغيرة", "Small"),
                        ClockStyle.BIG to tr("كبيرة", "Big"),
                        ClockStyle.BIG_SERIF to tr("كلاسيك", "Classic"),
                    ).forEach { (c, n) -> Chip(n, state.clock == c, pal) { vm.update { it.copy(clock = c) } } }
                }
            }

            item {
                Text(
                    tr("أشكال أيقونات جاهزة", "Icon style presets"),
                    color = pal.onPanel, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                )
                ScrollRow {
                    IconPresets.forEach { p ->
                        Chip(tr(p.ar, p.en), state.iconStyle == p.style, pal) {
                            vm.update { it.copy(iconStyle = p.style) }
                            vm.reloadApps()
                        }
                    }
                }
                SwitchRow(tr("استخدام أيقونات الثيم الخاصة", "Use the theme's own icons"), state.themeIcons, pal) { b ->
                    vm.update { it.copy(themeIcons = b) }
                    vm.reloadApps()
                }
                ActionRow(tr("حزم أيقونات من Google Play", "Icon packs on Google Play"), pal) {
                    ui.actions.openUrl("https://play.google.com/store/search?q=icon%20pack&c=apps")
                }
            }

            item {
                Section(tr("وضع التركيز", "Focus mode"), pal)
                SwitchRow(tr("تشغيل التركيز الآن (يخفي تطبيقات التشتيت)", "Focus on now (hides distracting apps)"), state.focusOn, pal) { b ->
                    vm.update { it.copy(focusOn = b) }
                }
                ActionRow(
                    tr("تطبيقات التشتيت: ", "Distracting apps: ") + state.focusApps.size,
                    pal,
                ) {
                    ui.picker = PickerRequest(tr("تطبيقات التشتيت", "Distracting apps"), state.focusApps) { sel ->
                        vm.update { it.copy(focusApps = sel) }
                    }
                }
                SwitchRow(tr("تشغيل تلقائي بالساعة", "Auto by hour"), state.focusAuto, pal) { b ->
                    vm.update { it.copy(focusAuto = b) }
                }
                if (state.focusAuto) {
                    Text(tr("من", "From"), color = pal.onPanel, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                    ScrollRow { (0..23).forEach { h -> Chip("$h:00", state.focusFrom == h, pal) { vm.update { it.copy(focusFrom = h) } } } }
                    Text(tr("إلى", "To"), color = pal.onPanel, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    ScrollRow { (0..23).forEach { h -> Chip("$h:00", state.focusTo == h, pal) { vm.update { it.copy(focusTo = h) } } } }
                }
            }

            item {
                Section(tr("حد استخدام التطبيقات", "App usage limits"), pal)
                if (!ui.actions.hasUsageAccess()) {
                    ActionRow(tr("لازم تفعّل «الوصول لبيانات الاستخدام» علشان الحدود تشتغل", "Enable Usage access so limits work"), pal) {
                        ui.actions.openUsageAccess()
                    }
                }
                ActionRow(tr("إضافة حد لتطبيقات…", "Add a limit for apps…"), pal) {
                    ui.picker = PickerRequest(tr("اختار التطبيقات", "Pick apps"), state.limits.keys) { sel ->
                        vm.update { st ->
                            st.copy(limits = sel.associateWith { st.limits[it] ?: 60 })
                        }
                    }
                }
                val byKey = apps.associateBy { it.key }
                state.limits.forEach { (k, mins) ->
                    val label = byKey[k]?.label ?: return@forEach
                    ActionRow("$label — $mins " + tr("دقيقة/يوم (اضغط للتغيير)", "min/day (tap to change)"), pal) {
                        val steps = listOf(15, 30, 60, 90, 120, 180)
                        val next = steps.firstOrNull { it > mins }
                        vm.update { st ->
                            st.copy(limits = if (next == null) st.limits - k else st.limits + (k to next))
                        }
                    }
                }
            }

            item {
                Section(tr("قواعد تلقائية (لو … يبقى …)", "Automation rules (if … then …)"), pal)
                state.rules.forEachIndexed { i, r ->
                    ActionRow(ruleLabel(r) + "   ✕", pal) { vm.update { st -> st.copy(rules = st.rules.filterIndexed { j, _ -> j != i }) } }
                }
                ActionRow(tr("إضافة قاعدة…", "Add a rule…"), pal) { ui.ruleDialog = true }
            }

            item {
                Section(tr("مظهر ذكي", "Smart look"), pal)
                SwitchRow(tr("وضع الاسترخاء بالليل (لمسة دافئة على الشاشة)", "Night relax (warm tint)"), state.relaxOn, pal) { b ->
                    vm.update { it.copy(relaxOn = b) }
                }
                if (state.relaxOn) {
                    Text(tr("يبدأ الساعة", "Starts at"), color = pal.onPanel, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                    ScrollRow { (18..23).forEach { h -> Chip("$h:00", state.relaxHour == h, pal) { vm.update { it.copy(relaxHour = h) } } } }
                }
                SwitchRow(tr("اقتراحات بحث من الإنترنت (بيبعت اللي بتكتبه لجوجل)", "Web search suggestions (sends typing to Google)"), state.webSuggest, pal) { b ->
                    vm.update { it.copy(webSuggest = b) }
                }
                ActionRow(tr("إعادة الجولة التعريفية", "Replay intro tour"), pal) { ui.tourOpen = true }
                SwitchRow(tr("أيقونات حيّة للساعة والتقويم", "Live clock & calendar icons"), state.liveIcons, pal) { b ->
                    vm.update { it.copy(liveIcons = b) }
                }
                SwitchRow(tr("أيقونات أدفأ بالليل", "Warmer icons at night"), state.nightIcons, pal) { b ->
                    vm.update { it.copy(nightIcons = b) }
                }
                val wctx = LocalContext.current
                var motion by remember { androidx.compose.runtime.mutableStateOf(AuraWallpaperService.motion(wctx)) }
                SwitchRow(tr("حركة هادية للخلفية الحيّة", "Gentle motion for live wallpaper"), motion, pal) { b ->
                    motion = b
                    AuraWallpaperService.setMotion(wctx, b)
                }
            }

            if (state.removed.isNotEmpty()) {
                item {
                    Section(tr("اتحذفت مؤخراً (اضغط لإعادة التثبيت)", "Recently uninstalled (tap to reinstall)"), pal)
                    state.removed.forEach { e ->
                        val pkg = e.substringBefore('|')
                        val name = e.substringAfter('|', pkg)
                        ActionRow(name, pal) {
                            ui.actions.openUrl("https://play.google.com/store/apps/details?id=$pkg")
                        }
                    }
                }
            }

            item {
                Section(tr("ثيم مخصص وخلفية", "Custom theme & wallpaper"), pal)
                ActionRow(tr("حفظ الشكل الحالي كثيم جديد…", "Save current look as a new theme…"), pal) {
                    ui.textInput = TextRequest(tr("اسم الثيم", "Theme name"), "") { name ->
                        // بعد الاسم نختار خلفية من المعرض (لو لغيت بنستخدم الخلفية الحالية للثيم)
                        ui.actions.pickImage { uri -> vm.saveCurrentAsTheme(name, uri) }
                    }
                }
                ActionRow(tr("خلفية حيّة للقفل والرئيسية (فيفو)", "Live wallpaper for lock + home"), pal) {
                    vm.openLiveWallpaper(null)
                }
                SwitchRow(tr("جدول الثيم: نهاري / ليلي", "Theme schedule: day / night"), state.scheduleOn, pal) { b ->
                    vm.update { it.copy(scheduleOn = b) }
                }
                if (state.scheduleOn) {
                    val dayName = installedThemes[state.dayTheme]?.name ?: tr("غير محدد", "Not set")
                    val nightName = installedThemes[state.nightTheme]?.name ?: tr("غير محدد", "Not set")
                    ActionRow(tr("ثيم النهار: ", "Day theme: ") + dayName, pal) { schedDialog = 1 }
                    ActionRow(tr("ثيم الليل: ", "Night theme: ") + nightName, pal) { schedDialog = 2 }
                    Text(
                        tr("يبدأ النهار الساعة", "Day starts at"),
                        color = pal.onPanel, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                    )
                    ScrollRow {
                        (5..11).forEach { h ->
                            Chip("$h:00", state.dayHour == h, pal) { vm.update { it.copy(dayHour = h) } }
                        }
                    }
                    Text(
                        tr("يبدأ الليل الساعة", "Night starts at"),
                        color = pal.onPanel, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                    )
                    ScrollRow {
                        (16..23).forEach { h ->
                            Chip("$h:00", state.nightHour == h, pal) { vm.update { it.copy(nightHour = h) } }
                        }
                    }
                    Text(
                        tr("الثيمات لازم تكون محمّلة من المتجر. التبديل بيحصل والتطبيق شغال.",
                            "Themes must be downloaded from the store. Switching happens while the app is running."),
                        color = pal.onPanel.copy(alpha = 0.5f), fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            item {
                Section(tr("الشفافية والألوان", "Transparency & colors"), pal)
                SwitchRow(tr("لون الواجهة من الخلفية (Material You)", "Accent from wallpaper (Material You)"), state.dynamicColor, pal) { b ->
                    vm.update { it.copy(dynamicColor = b) }
                }
                SliderRow(tr("شفافية الدرج", "Drawer opacity"), state.drawerAlpha.toFloat(), 40f..100f, 5, pal, { it.toInt().toString() + "%" }) { n ->
                    vm.update { it.copy(drawerAlpha = n.toInt()) }
                }
                SliderRow(tr("شفافية الدوك", "Dock opacity"), state.dockAlpha.toFloat(), 0f..100f, 9, pal, { it.toInt().toString() + "%" }) { n ->
                    vm.update { it.copy(dockAlpha = n.toInt()) }
                }
                SwitchRow(tr("تمويه الخلفية خلف الدرج (لو الجهاز يدعم)", "Blur behind drawer (if supported)"), state.blurOn, pal) { b ->
                    vm.update { it.copy(blurOn = b) }
                }
                SwitchRow(tr("اللوحة الجانبية (اسحب من حافة الشاشة اليمنى)", "Edge panel (swipe from right edge)"), state.edgeOn, pal) { b ->
                    vm.update { it.copy(edgeOn = b) }
                }
                ActionRow(tr("أيقونات أحادية بلون الثيم", "Themed single-color icons"), pal) {
                    fun mix(c: Long, t: Int, f: Float): Long {
                        val r = ((c shr 16 and 0xFF) * (1 - f) + t * f).toInt()
                        val g = ((c shr 8 and 0xFF) * (1 - f) + t * f).toInt()
                        val b = ((c and 0xFF) * (1 - f) + t * f).toInt()
                        return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                    }
                    vm.update {
                        it.copy(
                            iconStyle = IconStyle(
                                shape = IconShape.SQUIRCLE, plate = mix(it.accent, 0, 0.78f),
                                mono = true, tint = mix(it.accent, 255, 0.7f),
                            ),
                        )
                    }
                    vm.reloadApps()
                }
            }

            item {
                Section(tr("الشبكة والأيقونات", "Grid & icons"), pal)
                SliderRow(
                    tr("عدد الأعمدة", "Columns"), state.columns.toFloat(), 3f..6f, 2, pal,
                    { it.toInt().toString() },
                ) { n -> vm.update { it.copy(columns = n.toInt()).reflow() } }
                SliderRow(
                    tr("عدد الصفوف", "Rows"), state.rows.toFloat(), 4f..7f, 2, pal,
                    { it.toInt().toString() },
                ) { n -> vm.update { it.copy(rows = n.toInt()).reflow() } }
                SliderRow(
                    tr("أعمدة الدرج", "Drawer columns"), state.drawerColumns.toFloat(), 3f..6f, 2, pal,
                    { it.toInt().toString() },
                ) { n -> vm.update { it.copy(drawerColumns = n.toInt()) } }
                SliderRow(
                    tr("حجم الأيقونات", "Icon size"), state.iconScale, 0.7f..1.4f, 0, pal,
                    { "${(it * 100).toInt()}%" },
                ) { n -> vm.update { it.copy(iconScale = n) } }
                SwitchRow(tr("إظهار أسماء التطبيقات", "Show app labels"), state.showLabels, pal) { b ->
                    vm.update { it.copy(showLabels = b) }
                }
                ActionRow(
                    tr("حزمة الأيقونات: ", "Icon pack: ") +
                        (iconPacks.firstOrNull { it.packageName == state.iconPack }?.label
                            ?: tr("الافتراضية", "Default")),
                    pal,
                ) { iconPackDialog = true }
            }

            item {
                Section(tr("الإيماءات والتنظيم", "Gestures & organizing"), pal)
                SwitchRow(
                    tr("السحب لأعلى/لأسفل", "Swipe up/down"),
                    state.gestures, pal,
                ) { b -> vm.update { it.copy(gestures = b) } }
                SwitchRow(tr("صفحة Feed (اسحب يمين من أول صفحة)", "Feed page (swipe right on first page)"), state.feedOn, pal) { b -> vm.update { it.copy(feedOn = b) } }
                ActionRow(
                    tr("رابط أخبار RSS: ", "News RSS URL: ") + (state.feedUrl.ifBlank { tr("افتراضي (BBC)", "default (BBC)") }),
                    pal,
                ) {
                    ui.textInput = TextRequest(tr("رابط RSS (فاضي = الافتراضي)", "RSS URL (empty = default)"), state.feedUrl) { u ->
                        vm.update { it.copy(feedUrl = u.trim()) }
                    }
                }
                SwitchRow(tr("اسحب لتحت على الدوك = وضع اليد الواحدة", "Swipe down on dock = one-handed mode"), state.dockSwipe, pal) { b -> vm.update { it.copy(dockSwipe = b) } }
                SwitchRow(tr("اليد الواحدة: الشاشة على اليسار", "One-handed: shrink to the left"), state.oneHandLeft, pal) { b -> vm.update { it.copy(oneHandLeft = b) } }
                Text(
                    tr("السحب لتحت على الشاشة الرئيسية", "Swipe down on home"),
                    color = pal.onPanel, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                )
                ScrollRow {
                    listOf(GestureAction.NOTIFICATIONS, GestureAction.SEARCH, GestureAction.SETTINGS, GestureAction.ONE_HAND, GestureAction.FEED, GestureAction.NONE)
                        .forEach { a -> Chip(gestureLabel(a), state.swipeDown == a, pal) { vm.update { it.copy(swipeDown = a) } } }
                }
                Text(
                    tr("النقر المزدوج على مكان فاضي", "Double-tap on empty space"),
                    color = pal.onPanel, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                ScrollRow {
                    listOf(
                        GestureAction.NONE, GestureAction.LOCK, GestureAction.RECENTS, GestureAction.SEARCH,
                        GestureAction.DRAWER, GestureAction.SETTINGS, GestureAction.NOTIFICATIONS, GestureAction.ONE_HAND, GestureAction.FEED,
                    ).forEach { a -> Chip(gestureLabel(a), state.doubleTap == a, pal) { vm.update { it.copy(doubleTap = a) } } }
                }
                if (state.doubleTap == GestureAction.LOCK) {
                    ActionRow(tr("تفعيل خدمة قفل الشاشة (إمكانية الوصول)", "Enable lock-screen service (Accessibility)"), pal) {
                        ui.actions.openAccessibilitySettings()
                    }
                }
                SwitchRow(tr("شارات الإشعارات على الأيقونات", "Notification badges on icons"), state.badges, pal) { b ->
                    vm.update { it.copy(badges = b) }
                    if (b) ui.actions.openNotificationAccess()
                }
                if (state.badges) {
                    ActionRow(tr("إذن الوصول للإشعارات", "Notification access permission"), pal) {
                        ui.actions.openNotificationAccess()
                    }
                }
                ActionRow(tr("إضافة ودجت", "Add widget"), pal) {
                    ui.settingsOpen = false
                    ui.widgetPicker = true
                }
                ActionRow(tr("مجلد جديد", "New folder"), pal) {
                    ui.settingsOpen = false
                    ui.newFolder()
                }
                ActionRow(tr("مجلدات ذكية (حسب نوع التطبيق)", "Smart folders (by app type)"), pal) {
                    ui.smartFolders(apps)
                    ui.settingsOpen = false
                }
            }

            item {
                Section(tr("الأمان والخصوصية", "Security & privacy"), pal)
                Text(
                    tr(
                        "القفل والإخفاء هنا داخل Aura فقط، ومش بديل لقفل الشاشة بتاع النظام.",
                        "Lock and hide apply inside Aura only; they don't replace the system lock screen.",
                    ),
                    color = pal.onPanel.copy(alpha = 0.6f), fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                ActionRow(
                    if (state.pinHash == null) tr("تعيين PIN", "Set PIN") else tr("تغيير PIN", "Change PIN"),
                    pal,
                ) { ui.requireAuth { pinStage = PinStage.NEW } }
                if (state.pinHash != null) {
                    ActionRow(tr("حذف PIN", "Remove PIN"), pal, danger = true) {
                        ui.requireAuth {
                            vm.update { it.copy(pinHash = null, pinSalt = null, biometric = false) }
                        }
                    }
                    if (ui.actions.biometricAvailable()) {
                        SwitchRow(tr("استخدام البصمة", "Use fingerprint"), state.biometric, pal) { b ->
                            vm.update { it.copy(biometric = b) }
                        }
                    }
                    ActionRow(tr("التطبيقات المقفولة", "Locked apps"), pal) {
                        ui.requireAuth {
                            ui.picker = PickerRequest(
                                tr("التطبيقات المقفولة", "Locked apps"), state.locked, includeHidden = true,
                            ) { sel -> vm.update { it.copy(locked = sel) } }
                        }
                    }
                }
                ActionRow(
                    tr("التطبيقات المخفية (${state.hidden.size})", "Hidden apps (${state.hidden.size})"), pal,
                ) {
                    ui.requireAuth { ui.hiddenOpen = true }
                }
            }

            val top = state.usage.entries
                .filter { byKey.containsKey(it.key) }
                .sortedByDescending { it.value.count }
                .take(8)
            item {
                Section(tr("إحصائيات الاستخدام", "Usage statistics"), pal)
                if (top.isEmpty()) {
                    Text(
                        tr("لسه مفيش استخدام مسجّل.", "No usage recorded yet."),
                        color = pal.onPanel.copy(alpha = 0.6f),
                    )
                }
            }
            items(top, key = { "u:" + it.key }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(byKey[e.key]?.label ?: "", color = pal.onPanel, modifier = Modifier.weight(1f))
                    Text("${e.value.count}", color = pal.onPanel.copy(alpha = 0.7f))
                }
            }

            item {
                Section(tr("النسخ الاحتياطي", "Backup"), pal)
                ActionRow(tr("حفظ نسخة احتياطية (ملف)", "Export backup (file)"), pal) { ui.actions.exportBackup() }
                ActionRow(tr("استعادة من ملف", "Restore from file"), pal) { ui.actions.importBackup() }
                val updScope = androidx.compose.runtime.rememberCoroutineScope()
                ActionRow(tr("الأذونات (شرح وتفعيل)", "Permissions"), pal) { ui.permsOpen = true }
                ActionRow(tr("التشخيص والأداء", "Diagnostics & performance"), pal) { ui.diagOpen = true }
                ActionRow(tr("التحقق من تحديث", "Check for updates"), pal) {
                    updScope.launch {
                        val r = latestRelease()
                        when {
                            r == null -> vm.say(tr("تعذّر التحقق، جرّب لاحقاً", "Couldn't check, try later"))
                            isNewer(r.first, ui.actions.appVersion()) -> {
                                vm.say(tr("فيه تحديث ${r.first}", "Update ${r.first} available"))
                                ui.actions.openUrl(r.second)
                            }
                            else -> vm.say(tr("أنت على آخر إصدار ✓", "You're up to date ✓"))
                        }
                    }
                }
                ActionRow(tr("إعادة ضبط الشاشة الرئيسية", "Reset home screen"), pal, danger = true) {
                    resetDialog = true
                }
            }

            item {
                Text(
                    "Aura Launcher " + ui.actions.appVersion(),
                    color = pal.onPanel.copy(alpha = 0.4f), fontSize = 12.sp,
                    modifier = Modifier.padding(top = 28.dp),
                )
            }
        }
    }

    when (pinStage) {
        PinStage.NONE -> Unit
        PinStage.NEW -> PinDialog(
            title = tr("PIN جديد (4–6 أرقام)", "New PIN (4–6 digits)"),
            errorTick = 0, pal = pal, onBiometric = null,
            onDismiss = { pinStage = PinStage.NONE },
            onSubmit = { firstPin = it; pinStage = PinStage.CONFIRM },
        )
        PinStage.CONFIRM -> PinDialog(
            title = tr("أكّد الـ PIN", "Confirm PIN"),
            errorTick = pinError, pal = pal, onBiometric = null,
            onDismiss = { pinStage = PinStage.NONE },
            onSubmit = { again ->
                if (again == firstPin) {
                    val salt = Security.newSalt()
                    vm.update { it.copy(pinSalt = salt, pinHash = Security.hash(firstPin, salt)) }
                    pinStage = PinStage.NONE
                    firstPin = ""
                } else {
                    pinError++
                }
            },
        )
    }

    if (iconPackDialog) {
        SimpleChoiceDialog(
            title = tr("حزمة الأيقونات", "Icon pack"),
            options = listOf<Pair<String?, String>>(null to tr("الافتراضية", "Default")) +
                iconPacks.map { it.packageName to it.label },
            onPick = { vm.setIconPack(it) },
            onDismiss = { iconPackDialog = false },
        )
    }

    if (schedDialog != 0) {
        val isDay = schedDialog == 1
        SimpleChoiceDialog(
            title = if (isDay) tr("ثيم النهار", "Day theme") else tr("ثيم الليل", "Night theme"),
            options = listOf<Pair<String?, String>>(null to tr("بدون تغيير", "No change")) +
                installedThemes.values.sortedBy { it.id }.map { it.id to it.name },
            onPick = { id -> vm.update { if (isDay) it.copy(dayTheme = id) else it.copy(nightTheme = id) } },
            onDismiss = { schedDialog = 0 },
        )
    }

    if (resetDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text(tr("إعادة الضبط؟", "Reset?")) },
            text = {
                Text(tr(
                    "هيتم مسح ترتيب الشاشة الرئيسية والمجلدات والودجتس (التطبيقات نفسها مش هتتمسح).",
                    "This clears your home layout, folders and widgets (apps are not uninstalled).",
                ))
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.pages.flatten().filterIsInstance<HomeItem.Widget>()
                        .forEach { ui.actions.removeWidget(it.widgetId) }
                    vm.update { it.copy(pages = listOf(emptyList())) }
                    resetDialog = false
                }) { Text(tr("إعادة ضبط", "Reset")) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { resetDialog = false }) {
                    Text(tr("إلغاء", "Cancel"))
                }
            },
        )
    }
}

/** شاشة التطبيقات المخفية (بعد التحقق من PIN لو متفعّل). */
@Composable
fun HiddenAppsScreen(ui: Ui, state: AuraState, apps: List<AppEntry>, pal: AuraPalette) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    val hiddenApps = apps.filter { it.key in state.hidden }
    Column(
        Modifier
            .fillMaxSize()
            .background(pal.drawerBg)
            .padding(top = insets.calculateTopPadding() + 8.dp, start = 20.dp, end = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹", color = pal.onPanel, fontSize = 30.sp,
                modifier = Modifier.clip(CircleShape).clickable { ui.hiddenOpen = false }
                    .padding(horizontal = 14.dp, vertical = 2.dp),
            )
            Text(tr("التطبيقات المخفية", "Hidden apps"), color = pal.onPanel, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (hiddenApps.isEmpty()) {
            Text(
                tr("مفيش تطبيقات مخفية.", "No hidden apps."),
                color = pal.onPanel.copy(alpha = 0.6f), modifier = Modifier.padding(top = 24.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = insets.calculateBottomPadding() + 16.dp)) {
            items(hiddenApps, key = { it.key }) { app ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.Image(app.icon, null, Modifier.size(40.dp))
                    Text(app.label, color = pal.onPanel, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Text(
                        tr("إظهار", "Unhide"),
                        color = pal.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { ui.vm.update { it.unhideApp(app.key) } }
                            .padding(10.dp),
                    )
                }
            }
        }
    }
}
