package com.aura.launcher

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
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

    // ساعة بتتحدّث كل دقيقة (وضع التركيز + أيقونات الليل)
    LaunchedEffect(Unit) {
        while (true) {
            ui.hour = java.time.LocalTime.now().hour
            delay(60_000L)
        }
    }

    // تلميح مرة واحدة: فعّل الوصول للإشعارات علشان الأرقام تظهر على الأيقونات
    var badgeAsk by remember { mutableStateOf(false) }
    LaunchedEffect(ready) {
        if (ready && !vm.state.value.badgePrompted) {
            delay(4_000L)
            if (!actions.notificationAccessEnabled()) badgeAsk = true
            vm.update { it.copy(badgePrompted = true) }
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

    AuraTheme(pal) {
        Box(Modifier.fillMaxSize()) {
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
            ui.folderId?.let { id ->
                val folder = state.pages.flatten().filterIsInstance<HomeItem.Folder>().firstOrNull { it.id == id }
                if (folder == null) ui.folderId = null
                else FolderDialog(ui, state, apps, pal, folder)
            }

            if (ui.widgetPicker) {
                WidgetPickerDialog(
                    host = actions.widgetHost,
                    onPick = { actions.addWidget(it) },
                    onPickBuiltin = { kind ->
                        val b = HomeItem.Builtin(kind, newId())
                        vm.update { it.addItem(b) }
                        when (kind) {
                            "photo" -> ui.pickPhoto(b.uid)
                            "weather", "prayer" -> ui.editCity(b)
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

            if (badgeAsk) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { badgeAsk = false },
                    title = { androidx.compose.material3.Text(tr("أرقام الإشعارات", "Notification badges")) },
                    text = {
                        androidx.compose.material3.Text(
                            tr(
                                "علشان يظهر رقم (1، 2، 3…) فوق التطبيق اللي عليه إشعارات، فعّل «الوصول للإشعارات» للّانشر. بنعدّ بس، مبنقرأش محتوى.",
                                "To show a count (1, 2, 3…) on apps with notifications, enable Notification access. We only count, never read content.",
                            ),
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { badgeAsk = false; actions.openNotificationAccess() }) {
                            androidx.compose.material3.Text(tr("تفعيل", "Enable"))
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { badgeAsk = false }) {
                            androidx.compose.material3.Text(tr("لاحقاً", "Later"))
                        }
                    },
                )
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

        // الرجوع: كل زرار يقفل أقرب طبقة مفتوحة (الأحدث تسجيلاً له الأولوية)
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
