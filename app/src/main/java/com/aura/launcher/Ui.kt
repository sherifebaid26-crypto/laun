package com.aura.launcher

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Where { HOME, DOCK, DRAWER, FOLDER }

data class MenuEntry(val label: String, val onClick: () -> Unit)
data class TextRequest(val title: String, val initial: String, val onOk: (String) -> Unit)
data class PickerRequest(
    val title: String,
    val selected: Set<String>,
    val includeHidden: Boolean = false,
    val onOk: (Set<String>) -> Unit,
)

/** عمليات النظام اللي بتتنفذ من الـ Activity. */
interface LauncherActions {
    val widgetHost: WidgetHost
    fun launchApp(app: AppEntry)
    fun openAppInfo(app: AppEntry)
    fun uninstall(app: AppEntry)
    fun addWidget(provider: AppWidgetProviderInfo)
    fun removeWidget(id: Int)
    fun biometricPrompt(onSuccess: () -> Unit)
    fun biometricAvailable(): Boolean
    fun exportBackup()
    fun importBackup()
    fun requestDefaultLauncher()
    fun openWallpaperPicker()
}

/** حالة الواجهة المؤقتة + العمليات اللي بتربط الشاشات ببعض. */
class Ui(val vm: LauncherViewModel, val actions: LauncherActions) {
    var drawerOpen by mutableStateOf(false)
    var focusSearch by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var hiddenOpen by mutableStateOf(false)
    var folderId by mutableStateOf<String?>(null)
    var widgetPicker by mutableStateOf(false)
    var homeMenu by mutableStateOf(false)
    var textInput by mutableStateOf<TextRequest?>(null)
    var picker by mutableStateOf<PickerRequest?>(null)
    var auth by mutableStateOf<(() -> Unit)?>(null)
    var storeOpen by mutableStateOf(false)
    var storeDetail by mutableStateOf<String?>(null)

    fun openDrawer(focus: Boolean) {
        focusSearch = focus
        drawerOpen = true
    }

    fun closeAll() {
        drawerOpen = false
        settingsOpen = false
        hiddenOpen = false
        folderId = null
        widgetPicker = false
        homeMenu = false
        storeOpen = false
        storeDetail = null
    }

    /** لو في PIN نطلبه، لو مفيش ننفذ على طول. */
    fun requireAuth(onOk: () -> Unit) {
        if (vm.state.value.pinHash == null) onOk() else auth = onOk
    }

    fun launch(app: AppEntry) {
        val go = {
            vm.recordLaunch(app.key)
            drawerOpen = false
            folderId = null
            actions.launchApp(app)
        }
        if (app.key in vm.state.value.locked) requireAuth(go) else go()
    }

    fun newFolder() {
        textInput = TextRequest(tr("مجلد جديد", "New folder"), "") { name ->
            val id = newId()
            vm.update { it.addFolder(id, name.ifBlank { tr("مجلد", "Folder") }, emptyList()) }
            folderId = id
        }
    }

    fun menuFor(app: AppEntry, where: Where, folder: String? = null): List<MenuEntry> {
        val s = vm.state.value
        val k = app.key
        val out = mutableListOf<MenuEntry>()

        when (where) {
            Where.HOME -> out += MenuEntry(tr("إزالة من الشاشة", "Remove from home")) {
                vm.update { it.removeApp(k) }
            }
            Where.DOCK -> out += MenuEntry(tr("إزالة من الدوك", "Remove from dock")) {
                vm.update { it.dockRemove(k) }
            }
            Where.FOLDER -> out += MenuEntry(tr("إخراج من المجلد", "Remove from folder")) {
                vm.update { st ->
                    val fid = folder ?: return@update st
                    st.setFolderApps(fid, st.folderKeys(fid) - k).addItem(HomeItem.App(k))
                }
            }
            Where.DRAWER -> if (!s.isOnHome(k)) {
                out += MenuEntry(tr("إضافة للشاشة الرئيسية", "Add to home")) {
                    vm.update { it.addItem(HomeItem.App(k)) }
                }
            }
        }

        if (where != Where.DOCK) {
            if (k in s.dock) {
                out += MenuEntry(tr("إزالة من الدوك", "Remove from dock")) { vm.update { it.dockRemove(k) } }
            } else if (s.dock.size < MAX_DOCK) {
                out += MenuEntry(tr("إضافة للدوك", "Add to dock")) { vm.update { it.dockAdd(k) } }
            }
        }

        if (where == Where.HOME || where == Where.DRAWER) {
            s.pages.flatten().filterIsInstance<HomeItem.Folder>().forEach { f ->
                out += MenuEntry(tr("نقل إلى «${f.name}»", "Move to “${f.name}”")) {
                    vm.update { it.setFolderApps(f.id, it.folderKeys(f.id) + k) }
                }
            }
        }

        out += MenuEntry(tr("إخفاء", "Hide")) { vm.update { it.hideApp(k) } }

        if (k in s.locked) {
            out += MenuEntry(tr("إلغاء القفل", "Unlock")) {
                requireAuth { vm.update { it.copy(locked = it.locked - k) } }
            }
        } else if (s.pinHash == null) {
            out += MenuEntry(tr("قفل (اضبط PIN أولاً)", "Lock (set a PIN first)")) { settingsOpen = true }
        } else {
            out += MenuEntry(tr("قفل التطبيق", "Lock app")) { vm.update { it.copy(locked = it.locked + k) } }
        }

        out += MenuEntry(tr("معلومات التطبيق", "App info")) { actions.openAppInfo(app) }
        out += MenuEntry(tr("إلغاء التثبيت", "Uninstall")) { actions.uninstall(app) }
        return out
    }

    /** يرتب التطبيقات في مجلدات حسب تصنيف أندرويد (لو أكتر من تطبيقين في التصنيف). */
    fun smartFolders(apps: List<AppEntry>) {
        val groups = listOf(
            tr("ألعاب", "Games") to listOf(ApplicationInfo.CATEGORY_GAME),
            tr("تواصل", "Social") to listOf(ApplicationInfo.CATEGORY_SOCIAL),
            tr("وسائط", "Media") to listOf(
                ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE,
            ),
            tr("إنتاجية", "Productivity") to listOf(
                ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_NEWS,
            ),
            tr("خرائط وسفر", "Maps & travel") to listOf(ApplicationInfo.CATEGORY_MAPS),
        )
        vm.update { start ->
            var s = start
            for ((name, cats) in groups) {
                val keys = apps.filter { it.category in cats && it.key !in s.hidden }.map { it.key }
                if (keys.size >= 2) s = s.addFolder(newId(), name, keys)
            }
            s
        }
    }
}
