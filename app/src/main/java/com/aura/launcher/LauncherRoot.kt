package com.aura.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    val pal = remember(state.theme, state.accent) { paletteFor(state.theme, state.accent) }

    var authErrors by remember { mutableIntStateOf(0) }

    AuraTheme(pal) {
        Box(Modifier.fillMaxSize()) {
            if (ready) HomeLayer(ui, state, apps, pal)

            AnimatedVisibility(
                visible = ui.drawerOpen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                Drawer(ui, state, appList, pal)
            }

            AnimatedVisibility(visible = ui.settingsOpen, enter = fadeIn(), exit = fadeOut()) {
                SettingsScreen(ui, state, appList, pal)
            }
            AnimatedVisibility(visible = ui.hiddenOpen, enter = fadeIn(), exit = fadeOut()) {
                HiddenAppsScreen(ui, state, appList, pal)
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
                    onDismiss = { ui.widgetPicker = false },
                )
            }

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
        BackHandler(enabled = ui.settingsOpen) { ui.settingsOpen = false }
        BackHandler(enabled = ui.hiddenOpen) { ui.hiddenOpen = false }
    }
}
