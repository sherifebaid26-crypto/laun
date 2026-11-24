package com.aura.launcher

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun LauncherRoot(vm: LauncherViewModel, actions: LauncherActions) {
    val state by vm.state.collectAsState()
    val appList by vm.apps.collectAsState()
    val ready by vm.ready.collectAsState()

    Lang.code = state.lang
    val ui = remember { Ui(vm, actions) }
    val apps = remember(appList) { appList.associateBy { it.key } }
    val pal = remember(state.theme, state.accent, state.homeText) {
        val base = paletteFor(state.theme, state.accent)
        val custom = state.homeText
        if (custom == null) base else {
            val col = Color(custom)
            base.copy(
                homeText = col,
                homeShadow = if (col.luminance() > 0.5f) Color(0x99000000) else Color(0x66FFFFFF),
            )
        }
    }

    val installed by vm.installed.collectAsState()
    LaunchedEffect(state.scheduleOn, state.dayTheme, state.nightTheme, state.dayHour, state.nightHour, installed.size) {
        while (vm.state.value.scheduleOn) {
            vm.applyScheduled()
            delay(60_000L)
        }
    }

    // قواعد تلقائية + حالة الجهاز (سماعة/شحن/واي فاي) كل 20 ثانية
    LaunchedEffect(ready) {
        val prev = HashMap<String, Boolean>()
        while (ready) {
            val st = actions.deviceStatus()
            ui.headphones = st.headphones
            val s = vm.state.value
            s.rules.forEach { r ->
                val p = r.split("|")
                val t = p.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val a = p.getOrNull(1)?.toIntOrNull() ?: return@forEach
                val now = ruleActive(t, st, s, ui.hour)
                val was = prev[r]
                prev[r] = now
                if (was == null && !now) return@forEach
                if (now && was != true) {
                    when (a) {
                        0 -> vm.update { it.copy(focusOn = true) }
                        1 -> vm.update { it.copy(relaxOn = true) }
                        2 -> s.nightTheme?.let { id -> vm.installed.value[id]?.let { d -> vm.applyTheme(d, true) } }
                        3 -> s.dayTheme?.let { id -> vm.installed.value[id]?.let { d -> vm.applyTheme(d, true) } }
                    }
                } else if (!now && was == true) {
                    when (a) {
                        0 -> vm.update { it.copy(focusOn = false) }
                        1 -> vm.update { it.copy(relaxOn = false) }
                    }
                }
            }
            delay(20_000L)
        }
    }

    // نقفل الصفحات المقفولة لما نخرج من الشاشة
    val lifeOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifeOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) ui.unlockedPages.clear()
        }
        lifeOwner.lifecycle.addObserver(obs)
        onDispose { lifeOwner.lifecycle.removeObserver(obs) }
    }

    // لون ديناميكي من الخلفية (Material You)
    val dynCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.dynamicColor) {
        if (state.dynamicColor) {
            runCatching {
                val c = androidx.compose.material3.dynamicDarkColorScheme(dynCtx).primary.toArgb().toLong() and 0xFFFFFFFFL
                if (c != vm.state.value.accent) vm.update { it.copy(accent = c) }
            }
        }
    }

    // تمويه خلف الدرج
    LaunchedEffect(ui.drawerOpen, state.blurOn) { actions.setBlur(if (ui.drawerOpen && state.blurOn) 90 else 0) }

    // ساعة بتتحدّث كل دقيقة (وضع التركيز + أيقونات الليل)
    LaunchedEffect(Unit) {
        while (true) {
            ui.hour = java.time.LocalTime.now().hour
            delay(60_000L)
        }
    }

    // تلميح مرة واحدة: فعّل الوصول للإشعارات علشان الأرقام تظهر على الأيقونات
    LaunchedEffect(ready) {
        if (ready && !vm.state.value.onboarded) {
            delay(1_500L)
            ui.tourOpen = true
        }
    }

    LaunchedEffect(ready) {
        if (ready) {
            delay(6_000L)
            latestRelease()?.let { (tag, _) ->
                if (isNewer(tag, actions.appVersion())) vm.say(tr("فيه تحديث جديد $tag — من الإعدادات > التحقق من تحديث", "Update $tag available — Settings > Check for updates"))
            }
        }
    }

    val message by vm.message.collectAsState()
    val toastContext = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(toastContext, it, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    var authErrors by remember { mutableIntStateOf(0) }

    androidx.compose.runtime.key(state.lang) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides
            (if (Lang.isAr) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr),
    ) {
    AuraTheme(pal) {
        val ohScale by androidx.compose.animation.core.animateFloatAsState(if (ui.oneHand) 0.62f else 1f, tween(260), label = "oh")
        Box(Modifier.fillMaxSize()) {
        if (ui.oneHand) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { ui.oneHand = false },
            )
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = ohScale; scaleY = ohScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (state.oneHandLeft) 0f else 1f, 1f)
                clip = ohScale < 0.99f
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
            },
        ) {
            if (ready) HomeLayer(ui, state, apps, pal)

            AnimatedVisibility(
                visible = ui.drawerOpen,
                enter = slideInVertically(tween(300)) { it / 3 } + fadeIn(tween(240)) + scaleIn(tween(300), initialScale = 0.94f),
                exit = slideOutVertically(tween(240)) { it / 3 } + fadeOut(tween(200)) + scaleOut(tween(240), targetScale = 0.94f),
            ) {
                // أثناء سحب تطبيق من الدرج للرئيسية بنخفي الدرج (من غير ما نشيله علشان الإيماءة تكمّل)
                Box(Modifier.fillMaxSize().alpha(if (ui.extDrag != null) 0f else 1f)) {
                    Drawer(ui, state, appList, pal)
                }
            }

            AnimatedVisibility(visible = ui.settingsOpen, enter = fadeIn(), exit = fadeOut()) {
                SettingsScreen(ui, state, appList, pal)
            }
            AnimatedVisibility(visible = ui.hiddenOpen, enter = fadeIn(), exit = fadeOut()) {
                HiddenAppsScreen(ui, state, appList, pal)
            }
            AnimatedVisibility(visible = ui.storeOpen, enter = fadeIn(), exit = fadeOut()) {
                StoreScreen(ui, state, pal)
            }

            // مجلد مفتوح
            AnimatedVisibility(
                visible = ui.feedOpen,
                enter = slideInHorizontally(tween(280)) { -it } + fadeIn(tween(200)),
                exit = slideOutHorizontally(tween(240)) { -it } + fadeOut(tween(160)),
            ) { FeedScreen(ui, state, pal) }

            ui.folderId?.let { id ->
                val folder = state.findFolder(id)
                if (folder == null) ui.folderId = null
                else FolderDialog(ui, state, apps, pal, folder)
            }

            if (ui.widgetPicker) {
                WidgetPickerDialog(
                    host = actions.widgetHost,
                    onPick = { actions.addWidget(it) },
                    onPickBuiltin = { kind ->
                        var b = HomeItem.Builtin(kind, newId())
                        when (kind) {
                            "weather", "prayer" -> b = b.copy(data = "gps")
                            "stack" -> b = b.copy(data = "weather,prayer,ayat,agenda")
                        }
                        vm.update { it.addItem(b) }
                        when (kind) {
                            "photo" -> ui.pickPhoto(b.uid)
                            "weather", "prayer" -> if (!actions.hasLocationPermission()) actions.requestLocation { }
                        }
                    },
                    onDismiss = { ui.widgetPicker = false },
                )
            }

            ui.editWidget?.let { uid ->
                val item = state.builtinByUid(uid)
                if (item == null) ui.editWidget = null
                else WidgetEditDialog(ui, state, item) { ui.editWidget = null }
            }

            ui.limitDialog?.let { r ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { ui.limitDialog = null },
                    title = { androidx.compose.material3.Text(r.app.label) },
                    text = {
                        androidx.compose.material3.Text(
                            tr(
                                "وصلت للحد اليومي (${r.limit} دقيقة) — استخدمت ${r.used} دقيقة النهاردة.",
                                "Daily limit reached (${r.limit} min) — used ${r.used} min today.",
                            ),
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { ui.launchOverLimit(r) }) {
                            androidx.compose.material3.Text(tr("افتح برضه (5 دقايق)", "Open anyway (5 min)"))
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { ui.limitDialog = null }) {
                            androidx.compose.material3.Text(tr("إلغاء", "Cancel"))
                        }
                    },
                )
            }

            if (state.edgeOn && !ui.drawerOpen && !ui.overlayOpen) {
                EdgeHandle(ui, Modifier.align(Alignment.CenterEnd))
            }
            if (ui.edgeOpen) EdgePanel(ui, state, appList, pal)
            if (ui.editMode) EditPanel(ui, state, pal, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
            if (ui.tourOpen) TourDialog {
                ui.tourOpen = false
                val askPerms = !vm.state.value.badgePrompted
                vm.update { it.copy(onboarded = true, badgePrompted = true) }
                if (askPerms) ui.permsOpen = true
            }
            if (ui.ruleDialog) RuleDialog(ui) { ui.ruleDialog = false }
            if (ui.permsOpen) PermissionsDialog(ui) { ui.permsOpen = false }
            if (ui.diagOpen) DiagnosticsDialog(ui) { ui.diagOpen = false }

            if (state.relaxOn && (ui.hour >= state.relaxHour || ui.hour < state.dayHour)) {
                Box(Modifier.fillMaxSize().background(Color(0x30FF8F2E)))
            }

            ContextMenuHost(ui, pal, state.menuStyle)

            ui.textInput?.let { req -> TextInputDialog(req) { ui.textInput = null } }
            ui.picker?.let { req -> AppPickerDialog(appList, state.hidden, req) { ui.picker = null } }

            // التحقق من PIN
            ui.auth?.let { callback ->
                val done = {
                    ui.auth = null
                    authErrors = 0
                    callback()
                }
                PinDialog(
                    title = tr("أدخل PIN", "Enter PIN"),
                    errorTick = authErrors,
                    pal = pal,
                    onSubmit = { pin -> if (Security.verify(state, pin)) done() else authErrors++ },
                    onBiometric = if (state.biometric && actions.biometricAvailable()) {
                        { actions.biometricPrompt { done() } }
                    } else null,
                    onDismiss = { ui.auth = null; authErrors = 0 },
                )
            }
        }
        }

        // الرجوع: كل زرار يقفل أقرب طبقة مفتوحة (الأحدث تسجيلاً له الأولوية)
        BackHandler(enabled = ui.oneHand) { ui.oneHand = false }
        BackHandler(enabled = ui.feedOpen) { ui.feedOpen = false }
        BackHandler(enabled = ui.drawerOpen) { ui.drawerOpen = false }
        BackHandler(enabled = ui.folderId != null) { ui.folderId = null }
        BackHandler(enabled = ui.settingsOpen) { ui.settingsOpen = false }
        BackHandler(enabled = ui.hiddenOpen) { ui.hiddenOpen = false }
        BackHandler(enabled = ui.storeOpen) {
            if (ui.storeDetail != null) ui.storeDetail = null else ui.storeOpen = false
        }
        BackHandler(enabled = ui.menu != null) { ui.menu = null }
    }
    }
    }
}
