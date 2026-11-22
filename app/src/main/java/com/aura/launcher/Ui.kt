package com.aura.launcher

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

enum class Where { HOME, DOCK, DRAWER, FOLDER }

data class MenuEntry(
    val label: String,
    val glyph: String = "",
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/** قائمة عائمة قريبة من العنصر (anchor بإحداثيات الشاشة). */
data class MenuRequest(val anchor: Rect, val entries: List<MenuEntry>)

data class TextRequest(
    val title: String,
    val initial: String,
    val multiline: Boolean = false,
    val onOk: (String) -> Unit,
)

data class LimitRequest(val app: AppEntry, val used: Int, val limit: Int, val bounds: Rect?)

/** اختصار تطبيق (من LauncherApps). */
data class AppShortcut(val pkg: String, val id: String, val label: String)

/** تطبيق بيتسحب من الدرج للشاشة الرئيسية (الموضع بإحداثيات النافذة). */
data class ExtDrag(val app: AppEntry, val pos: Offset)
data class PickerRequest(
    val title: String,
    val selected: Set<String>,
    val includeHidden: Boolean = false,
    val onOk: (Set<String>) -> Unit,
)

/** عمليات النظام اللي بتتنفذ من الـ Activity. */
interface LauncherActions {
    val widgetHost: WidgetHost
    fun launchApp(app: AppEntry, bounds: Rect?)
    fun shortcutsFor(app: AppEntry): List<AppShortcut>
    fun startShortcut(s: AppShortcut)
    fun expandNotifications()
    fun lockScreen()
    fun openAccessibilitySettings()
    fun openNotificationAccess()
    fun openUrl(url: String)
    fun pickImage(onResult: (Uri?) -> Unit)
    fun pickPhoto(uid: String)
    fun hasLocationPermission(): Boolean
    fun requestLocation(onDone: () -> Unit)
    fun hasCalendarPermission(): Boolean
    fun requestCalendar(onDone: () -> Unit)
    fun appVersion(): String
    fun shareText(text: String)
    fun deviceStatus(): DeviceStatus
    fun openSystemSetting(action: String)
    fun dailyScreenMinutes(): List<Int>
    fun hasContactsPermission(): Boolean
    fun requestContacts(onDone: () -> Unit)
    fun notificationAccessEnabled(): Boolean
    fun usageMinutes(pkg: String): Int
    fun hasUsageAccess(): Boolean
    fun openUsageAccess()
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
    var textInput by mutableStateOf<TextRequest?>(null)
    var picker by mutableStateOf<PickerRequest?>(null)
    var auth by mutableStateOf<(() -> Unit)?>(null)
    var storeOpen by mutableStateOf(false)
    var storeDetail by mutableStateOf<String?>(null)
    var menu by mutableStateOf<MenuRequest?>(null)
    var editWidget by mutableStateOf<String?>(null)
    var resizeUid by mutableStateOf<String?>(null)
    var hour by androidx.compose.runtime.mutableIntStateOf(java.time.LocalTime.now().hour)
    var limitDialog by mutableStateOf<LimitRequest?>(null)
    var headphones by mutableStateOf(false)
    var page by androidx.compose.runtime.mutableIntStateOf(0)
    val unlockedPages = androidx.compose.runtime.mutableStateListOf<Int>()
    var ruleDialog by mutableStateOf(false)
    var permsOpen by mutableStateOf(false)
    var diagOpen by mutableStateOf(false)
    private val snoozed = HashMap<String, Long>()

    fun launchOverLimit(r: LimitRequest) {
        snoozed[r.app.key] = System.currentTimeMillis() + 5 * 60_000L
        limitDialog = null
        launch(r.app, r.bounds)
    }
    var extDrag by mutableStateOf<ExtDrag?>(null)

    /** بيسجّله HomeLayer: ينفّذ الإفلات على الشاشة الرئيسية ويرجّع true لو اتنفذ. */
    var dropHandler: ((ExtDrag) -> Boolean)? = null

    fun finishExtDrag(d: ExtDrag) {
        val ok = dropHandler?.invoke(d) ?: false
        extDrag = null
        if (ok) drawerOpen = false
    }

    fun perform(a: GestureAction) {
        when (a) {
            GestureAction.NONE -> Unit
            GestureAction.NOTIFICATIONS -> actions.expandNotifications()
            GestureAction.SEARCH -> openDrawer(true)
            GestureAction.DRAWER -> openDrawer(false)
            GestureAction.SETTINGS -> settingsOpen = true
            GestureAction.LOCK -> actions.lockScreen()
        }
    }

    fun editCity(b: HomeItem.Builtin) {
        textInput = TextRequest(
            tr("اسم المدينة (سيبه فاضي = موقعي تلقائياً)", "City name (blank = my location)"),
            if (b.data == "gps") "" else b.data,
        ) { c ->
            val v = c.trim().ifBlank { "gps" }
            vm.update { it.updateBuiltin(b.uid) { old -> old.copy(data = v) } }
            if (v == "gps" && !actions.hasLocationPermission()) actions.requestLocation { }
        }
    }

    fun pickPhoto(uid: String) {
        actions.pickPhoto(uid)
    }

    fun editNote(b: HomeItem.Builtin) {
        textInput = TextRequest(tr("ملاحظة", "Note"), b.data, multiline = true) { t ->
            vm.update { it.updateBuiltin(b.uid) { old -> old.copy(data = t) } }
        }
    }

    /** لو أي طبقة فوق الشاشة الرئيسية مفتوحة، إيماءات الشاشة الرئيسية تتعطل. */
    val overlayOpen: Boolean
        get() = drawerOpen || settingsOpen || hiddenOpen || storeOpen || folderId != null ||
            widgetPicker || menu != null || auth != null || textInput != null || picker != null ||
            editWidget != null || permsOpen || diagOpen || ruleDialog

    fun showMenu(anchor: Rect, entries: List<MenuEntry>) {
        if (entries.isNotEmpty()) menu = MenuRequest(anchor, entries)
    }

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
        storeOpen = false
        storeDetail = null
        menu = null
        editWidget = null
        resizeUid = null
    }

    /** لو في PIN نطلبه، لو مفيش ننفذ على طول. */
    fun requireAuth(onOk: () -> Unit) {
        if (vm.state.value.pinHash == null) onOk() else auth = onOk
    }

    fun launch(app: AppEntry, bounds: Rect? = null) {
        val go = {
            vm.recordLaunch(app.key)
            Badges.clear(app.packageName)
            drawerOpen = false
            folderId = null
            actions.launchApp(app, bounds)
        }
        val s = vm.state.value
        val limit = s.limits[app.key]
        if (limit != null && (snoozed[app.key] ?: 0L) < System.currentTimeMillis()) {
            val used = actions.usageMinutes(app.packageName)
            if (used >= limit) {
                limitDialog = LimitRequest(app, used, limit, bounds)
                return
            }
        }
        if (app.key in s.locked) requireAuth(go) else go()
    }

    fun newFolder() {
        textInput = TextRequest(tr("مجلد جديد", "New folder"), "") { name ->
            val id = newId()
            vm.update { it.addFolder(id, name.ifBlank { tr("مجلد", "Folder") }, emptyList()) }
            folderId = id
        }
    }

    fun homeEntries(): List<MenuEntry> = listOf(
        MenuEntry(
            if (vm.state.value.focusOn) tr("إيقاف وضع التركيز", "Turn off Focus") else tr("تشغيل وضع التركيز", "Turn on Focus"),
            "◎",
        ) {
            val s = vm.state.value
            if (!s.focusOn && s.focusApps.isEmpty()) {
                settingsOpen = true
                vm.postMessage(tr("اختار تطبيقات التركيز من الإعدادات الأول", "Choose Focus apps in Settings first"))
            } else vm.update { it.copy(focusOn = !it.focusOn) }
        },
        MenuEntry(
            if (page in vm.state.value.lockedPages) tr("فتح قفل هذه الصفحة", "Unlock this page") else tr("قفل هذه الصفحة بالبصمة", "Lock this page"),
            "🔒",
        ) {
            val s = vm.state.value
            if (s.pinHash == null) {
                vm.postMessage(tr("فعّل PIN من الإعدادات > الأمان الأول", "Set a PIN in Settings > Security first"))
            } else if (page in s.lockedPages) {
                requireAuth { vm.update { it.copy(lockedPages = it.lockedPages - page) } }
            } else {
                vm.update { it.copy(lockedPages = it.lockedPages + page) }
                unlockedPages.remove(page)
            }
        },
        MenuEntry(tr("إضافة ودجت", "Add widget"), "▦") { widgetPicker = true },
        MenuEntry(tr("مجلد جديد", "New folder"), "🗀") { newFolder() },
        MenuEntry(tr("متجر الثيمات", "Theme store"), "✦") { storeOpen = true },
        MenuEntry(tr("الخلفية", "Wallpaper"), "▣") { actions.openWallpaperPicker() },
        MenuEntry(tr("الإعدادات", "Settings"), "⚙") { settingsOpen = true },
    )

    fun editFolderApps(f: HomeItem.Folder) {
        picker = PickerRequest(
            title = tr("تطبيقات المجلد", "Folder apps"),
            selected = f.keys.toSet(),
        ) { sel ->
            vm.update { st ->
                val old = st.folderKeys(f.id)
                st.setFolderApps(f.id, old.filter { it in sel } + sel.filter { it !in old })
            }
        }
    }

    fun folderEntries(f: HomeItem.Folder): List<MenuEntry> = listOf(
        MenuEntry(tr("إضافة / إزالة تطبيقات", "Add / remove apps"), "⊕") { editFolderApps(f) },
        MenuEntry(tr("إعادة تسمية", "Rename"), "✎") {
            textInput = TextRequest(tr("اسم المجلد", "Folder name"), f.name) { n ->
                vm.update { it.renameFolder(f.id, n.ifBlank { f.name }) }
            }
        },
        MenuEntry(tr("حذف المجلد", "Delete folder"), "⌫", danger = true) { vm.update { it.deleteFolder(f.id) } },
    )

    fun widgetEntries(item: HomeItem): List<MenuEntry> {
        val out = mutableListOf<MenuEntry>()
        if (item is HomeItem.Builtin) {
            out += MenuEntry(tr("تعديل الحجم والشكل", "Edit size & style"), "◧") { editWidget = item.uid }
            if (item.kind == "notes") out += MenuEntry(tr("تعديل النص", "Edit text"), "✎") { editNote(item) }
            if (item.kind == "photo") out += MenuEntry(tr("تغيير الصورة", "Change photo"), "🖼") { pickPhoto(item.uid) }
            if (item.kind == "weather" || item.kind == "prayer") {
                out += MenuEntry(tr("استخدام موقعي تلقائياً", "Use my location"), "◎") {
                    vm.update { it.updateBuiltin(item.uid) { old -> old.copy(data = "gps") } }
                    if (!actions.hasLocationPermission()) actions.requestLocation { }
                }
                out += MenuEntry(tr("تغيير المدينة", "Change city"), "⌖") { editCity(item) }
            }
            out += MenuEntry(tr("تغيير الحجم بالسحب", "Resize by dragging"), "⤡") { resizeUid = item.uid }
        }
        out += MenuEntry(tr("إزالة الودجت", "Remove widget"), "⌫", danger = true) {
            if (item is HomeItem.Widget) actions.removeWidget(item.widgetId)
            vm.update { it.removeByRef(item.ref) }
        }
        return out
    }

    fun menuFor(app: AppEntry, where: Where, folder: String? = null): List<MenuEntry> {
        val s = vm.state.value
        val k = app.key
        val out = mutableListOf<MenuEntry>()

        actions.shortcutsFor(app).forEach { sc ->
            out += MenuEntry(sc.label, "↗") { actions.startShortcut(sc) }
        }

        when (where) {
            Where.HOME -> out += MenuEntry(tr("إزالة من الشاشة", "Remove from home"), "⊖") {
                vm.update { it.removeApp(k) }
            }
            Where.DOCK -> out += MenuEntry(tr("إزالة من الدوك", "Remove from dock"), "⊖") {
                vm.update { it.dockRemove(k) }
            }
            Where.FOLDER -> out += MenuEntry(tr("إخراج من المجلد", "Remove from folder"), "⇱") {
                vm.update { st ->
                    val fid = folder ?: return@update st
                    st.setFolderApps(fid, st.folderKeys(fid) - k).addItem(HomeItem.App(k))
                }
            }
            Where.DRAWER -> if (!s.isOnHome(k)) {
                out += MenuEntry(tr("إضافة للشاشة الرئيسية", "Add to home"), "⊕") {
                    vm.update { it.addItem(HomeItem.App(k)) }
                }
            }
        }

        if (where != Where.DOCK) {
            if (k in s.dock) {
                out += MenuEntry(tr("إزالة من الدوك", "Remove from dock"), "⊖") { vm.update { it.dockRemove(k) } }
            } else if (s.dock.size < MAX_DOCK) {
                out += MenuEntry(tr("إضافة للدوك", "Add to dock"), "⌑") { vm.update { it.dockAdd(k) } }
            }
        }

        if (where == Where.HOME || where == Where.DRAWER) {
            s.pages.flatten().filterIsInstance<HomeItem.Folder>().forEach { f ->
                out += MenuEntry(tr("نقل إلى «${f.name}»", "Move to “${f.name}”"), "🗀") {
                    vm.update { it.setFolderApps(f.id, it.folderKeys(f.id) + k) }
                }
            }
        }

        out += MenuEntry(tr("إخفاء", "Hide"), "◌") { vm.update { it.hideApp(k) } }

        if (k in s.locked) {
            out += MenuEntry(tr("إلغاء القفل", "Unlock"), "🔓") {
                requireAuth { vm.update { it.copy(locked = it.locked - k) } }
            }
        } else if (s.pinHash == null) {
            out += MenuEntry(tr("قفل (اضبط PIN أولاً)", "Lock (set a PIN first)"), "🔒") { settingsOpen = true }
        } else {
            out += MenuEntry(tr("قفل التطبيق", "Lock app"), "🔒") { vm.update { it.copy(locked = it.locked + k) } }
        }

        out += MenuEntry(tr("معلومات التطبيق", "App info"), "ⓘ") { actions.openAppInfo(app) }
        out += MenuEntry(tr("إلغاء التثبيت", "Uninstall"), "🗑", danger = true) { actions.uninstall(app) }
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
