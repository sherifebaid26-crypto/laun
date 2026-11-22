package com.aura.launcher

import android.Manifest
import android.app.ActivityOptions
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import androidx.core.app.NotificationManagerCompat
import android.app.role.RoleManager
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.ui.geometry.Rect
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity(), LauncherActions {

    private val vm: LauncherViewModel by viewModels()

    override val widgetHost: WidgetHost by lazy { WidgetHost(this) }

    private var pendingWidgetId = -1
    private var pendingProvider: AppWidgetProviderInfo? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            vm.reloadApps()
        }
    }

    private val bindLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) afterBind() else {
                cancelPendingWidget()
                vm.postMessage(
                    tr(
                        "النظام منع الودجت دي. استخدم ودجتس Aura (قابلة للتعديل) بدالها",
                        "The system blocked this widget. Use the Aura widgets instead (fully customizable)",
                    ),
                )
            }
        }

    private val configLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) finishAddWidget() else cancelPendingWidget()
        }

    private var imageCallback: ((Uri?) -> Unit)? = null
    private val imageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            imageCallback?.invoke(uri)
            imageCallback = null
        }

    private val miscPrefs get() = getSharedPreferences("aura_misc", MODE_PRIVATE)
    private val photoLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val uid = miscPrefs.getString("pending_photo", null)
            miscPrefs.edit().remove("pending_photo").apply()
            if (uri != null && uid != null) vm.setPhoto(uid, uri)
        }

    private var locCallback: (() -> Unit)? = null
    private val locLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            locCallback?.invoke()
            locCallback = null
        }

    private var calCallback: (() -> Unit)? = null
    private val calLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            calCallback?.invoke()
            calCallback = null
        }

    private var contactsCallback: (() -> Unit)? = null
    private val contactsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            contactsCallback?.invoke()
            contactsCallback = null
        }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) }
                }
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val text = runCatching {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (text != null) {
                    // نشيل الودجتس الحالية قبل الاستعادة علشان ما يتسربوش
                    vm.state.value.pages.flatten().filterIsInstance<HomeItem.Widget>()
                        .forEach { widgetHost.delete(it.widgetId) }
                    vm.importJson(text)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CrashLog.install(applicationContext)
        Badges.init(applicationContext)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent { LauncherRoot(vm, this) }
    }

    override fun onStart() {
        super.onStart()
        widgetHost.start()
    }

    override fun onStop() {
        widgetHost.stop()
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.homePressed()
    }

    // ───────────── LauncherActions ─────────────

    override fun launchApp(app: AppEntry, bounds: Rect?) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        // حركة فتح التطبيق: بيكبر من مكان الأيقونة
        val opts = if (bounds != null && bounds.width > 0f) {
            runCatching {
                ActivityOptions.makeScaleUpAnimation(
                    window.decorView, bounds.left.toInt(), bounds.top.toInt(),
                    bounds.width.toInt(), bounds.height.toInt(),
                ).toBundle()
            }.getOrNull()
        } else null
        runCatching { startActivity(intent, opts) }
    }

    override fun shortcutsFor(app: AppEntry): List<AppShortcut> {
        return try {
            val la = getSystemService(LauncherApps::class.java) ?: return emptyList()
            if (!la.hasShortcutHostPermission()) return emptyList()
            val q = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            (la.getShortcuts(q, Process.myUserHandle()) ?: emptyList())
                .filter { it.isEnabled }
                .mapNotNull { sc ->
                    val label = (sc.shortLabel ?: sc.longLabel)?.toString() ?: return@mapNotNull null
                    AppShortcut(sc.`package`, sc.id, label)
                }
                .take(4)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun startShortcut(s: AppShortcut) {
        runCatching {
            getSystemService(LauncherApps::class.java)
                ?.startShortcut(s.pkg, s.id, null, null, Process.myUserHandle())
        }
    }

    override fun expandNotifications() {
        runCatching {
            @Suppress("WrongConstant")
            val sbs = getSystemService("statusbar")
            sbs.javaClass.getMethod("expandNotificationsPanel").invoke(sbs)
        }
    }

    override fun lockScreen() {
        if (!AuraAccessibilityService.lock()) {
            vm.postMessage(
                tr(
                    "فعّل خدمة «Aura» من إعدادات إمكانية الوصول علشان يشتغل قفل الشاشة",
                    "Enable the Aura service in Accessibility settings to lock the screen",
                ),
            )
            openAccessibilitySettings()
        }
    }

    override fun openAccessibilitySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    override fun openNotificationAccess() {
        runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    override fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun pickImage(onResult: (Uri?) -> Unit) {
        imageCallback = onResult
        runCatching { imageLauncher.launch("image/*") }.onFailure {
            imageCallback = null
            onResult(null)
        }
    }

    override fun pickPhoto(uid: String) {
        miscPrefs.edit().putString("pending_photo", uid).apply()
        runCatching {
            photoLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }.onFailure { miscPrefs.edit().remove("pending_photo").apply() }
    }

    override fun deviceStatus(): DeviceStatus {
        val charging = runCatching { getSystemService(android.os.BatteryManager::class.java)?.isCharging == true }.getOrDefault(false)
        val hp = runCatching {
            val types = setOf(
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
            )
            getSystemService(android.media.AudioManager::class.java)
                .getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
        }.getOrDefault(false)
        val wifi = runCatching {
            val cm = getSystemService(android.net.ConnectivityManager::class.java)
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        }.getOrDefault(false)
        return DeviceStatus(charging, hp, wifi)
    }

    override fun openSystemSetting(action: String) {
        runCatching { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun dailyScreenMinutes(): List<Int> {
        if (!hasUsageAccess()) return emptyList()
        return runCatching {
            val usm = getSystemService(UsageStatsManager::class.java)
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                add(java.util.Calendar.DAY_OF_YEAR, -6)
            }
            val start = cal.timeInMillis
            val day = 86_400_000L
            val totals = LongArray(7)
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis()).forEach { st ->
                if (st.packageName == packageName) return@forEach
                val i = ((st.firstTimeStamp - start + day / 2) / day).toInt()
                if (i in 0..6) totals[i] += st.totalTimeInForeground
            }
            totals.map { (it / 60_000L).toInt() }
        }.getOrDefault(emptyList())
    }

    override fun hasLocationPermission(): Boolean = DeviceLoc.granted(this)

    override fun requestLocation(onDone: () -> Unit) {
        locCallback = onDone
        runCatching {
            locLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }.onFailure { locCallback = null }
    }

    override fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override fun requestCalendar(onDone: () -> Unit) {
        calCallback = onDone
        runCatching { calLauncher.launch(Manifest.permission.READ_CALENDAR) }.onFailure { calCallback = null }
    }

    override fun appVersion(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" }.getOrDefault("?")

    override fun shareText(text: String) {
        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                    null,
                ),
            )
        }
    }

    override fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    override fun notificationAccessEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    override fun hasUsageAccess(): Boolean = runCatching {
        val ao = getSystemService(AppOpsManager::class.java)
        ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) ==
            AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    override fun usageMinutes(pkg: String): Int {
        if (!hasUsageAccess()) return -1
        return runCatching {
            val usm = getSystemService(UsageStatsManager::class.java)
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
            val ms = stats.filter { it.packageName == pkg }.maxOfOrNull { it.totalTimeInForeground } ?: 0L
            (ms / 60_000L).toInt()
        }.getOrDefault(-1)
    }

    override fun openUsageAccess() {
        runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    }

    override fun requestContacts(onDone: () -> Unit) {
        contactsCallback = onDone
        contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    override fun openAppInfo(app: AppEntry) {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
            )
        }
    }

    override fun uninstall(app: AppEntry) {
        runCatching {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
        }
    }

    override fun addWidget(provider: AppWidgetProviderInfo) {
        val id = widgetHost.allocate()
        pendingWidgetId = id
        pendingProvider = provider
        val allowed = runCatching { widgetHost.manager.bindAppWidgetIdIfAllowed(id, provider.provider) }
            .getOrDefault(false)
        if (allowed) {
            afterBind()
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            runCatching { bindLauncher.launch(intent) }.onFailure {
                cancelPendingWidget()
                vm.postMessage(
                    tr(
                        "الجهاز رفض الودجت دي. جرّب ودجتس Aura",
                        "The device refused this widget. Try the Aura widgets",
                    ),
                )
            }
        }
    }

    private fun afterBind() {
        val provider = pendingProvider
        val configure = provider?.configure
        if (configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            runCatching { configLauncher.launch(intent) }.onFailure { finishAddWidget() }
        } else {
            finishAddWidget()
        }
    }

    private fun finishAddWidget() {
        val id = pendingWidgetId
        if (id != -1) vm.update { it.addItem(HomeItem.Widget(id)) }
        pendingWidgetId = -1
        pendingProvider = null
    }

    private fun cancelPendingWidget() {
        if (pendingWidgetId != -1) widgetHost.delete(pendingWidgetId)
        pendingWidgetId = -1
        pendingProvider = null
    }

    override fun removeWidget(id: Int) {
        widgetHost.delete(id)
    }

    override fun biometricAvailable(): Boolean =
        BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    override fun biometricPrompt(onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(tr("تأكيد الهوية", "Verify it's you"))
            .setNegativeButtonText(tr("استخدم PIN", "Use PIN"))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }

    override fun exportBackup() {
        exportLauncher.launch("aura-backup.json")
    }

    override fun importBackup() {
        importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
    }

    override fun requestDefaultLauncher() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        }
    }

    override fun openWallpaperPicker() {
        runCatching {
            startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), tr("اختر الخلفية", "Choose wallpaper"))
            )
        }
    }
}
