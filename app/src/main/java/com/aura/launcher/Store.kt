package com.aura.launcher

import android.app.Application
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Error(val msg: String) : CatalogState
    data class Ready(val themes: List<ThemeDef>, val offline: Boolean) : CatalogState
}

private val Application.dataStore by preferencesDataStore(name = "aura")

private const val MAX_BACKUP_BYTES = 40L * 1024 * 1024
private val STATE_KEY = stringPreferencesKey("state")

class LauncherViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(AuraState())
    val state: StateFlow<AuraState> = _state.asStateFlow()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _homeTick = MutableStateFlow(0)
    val homeTick: StateFlow<Int> = _homeTick.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = app.dataStore.data.first()[STATE_KEY]
            if (raw != null) {
                val ok = runCatching { _state.value = auraStateFromJson(JSONObject(raw)) }.isSuccess
                if (!ok) {
                    // الحالة المحفوظة تالفة: نحتفظ بنسخة منها قبل ما أي تعديل جاي يكتب فوقها
                    withContext(Dispatchers.IO) {
                        runCatching { java.io.File(app.filesDir, "state-corrupt-backup.json").writeText(raw) }
                    }
                    _message.value = tr(
                        "تعذّرت قراءة إعداداتك المحفوظة، بدأنا بإعدادات جديدة (نسخة من القديم محفوظة).",
                        "Couldn't read saved settings; starting fresh (old copy kept).",
                    )
                }
            }
            if (CrashLog.safeModeNeeded(app)) {
                runCatching { java.io.File(app.filesDir, "pre-safe-mode-backup.json").writeText(_state.value.toJson().toString(2)) }
                val d = AuraState()
                _state.value = _state.value.copy(
                    pages = d.pages, rules = emptyList(), focusOn = false, relaxOn = false,
                )
                CrashLog.clearRecent(app)
                _message.value = tr("حصل كراش متكرر، فرجّعنا الشاشة الرئيسية للوضع الآمن (نسخة من القديم محفوظة).", "Repeated crashes: home reset to safe mode (old copy saved).")
            }
            loadAppsNow()
            if (!_state.value.dockInitialized) {
                val dock = AppRepository.defaultDock(app, _apps.value)
                update { it.copy(dock = dock, dockInitialized = true) }
            }
            _ready.value = true
            _installed.value = ThemeStore.loadInstalled(app).associateBy { it.id }
        }
    }

    private suspend fun loadAppsNow() {
        val s = _state.value
        val icons = if (s.themeIcons) s.themeId?.let { id ->
            withContext(Dispatchers.IO) { ThemeIcons.load(ThemeStore.iconsDir(app, id)) }.ifEmpty { null }
        } else null
        val prev = _apps.value
        val t0 = System.currentTimeMillis()
        val list = AppRepository.loadApps(app, s.iconPack, s.iconStyle, icons)
        Perf.loadMs = System.currentTimeMillis() - t0
        Perf.appCount = list.size
        val customized = if (s.appNames.isEmpty() && s.customIcons.isEmpty()) list else withContext(Dispatchers.Default) {
            list.map { a ->
                var e = a
                s.appNames[a.key]?.takeIf { it.isNotBlank() }?.let { e = e.copy(label = it) }
                if (a.key in s.customIcons) {
                    runCatching {
                        BitmapFactory.decodeFile(customIconFile(a.key).path)?.let { e = e.copy(icon = it.asImageBitmap()) }
                    }
                }
                e
            }.sortedBy { it.normLabel }
        }
        _apps.value = customized
        if (prev.isNotEmpty()) trackRemoved(prev, customized)
    }

    /** بنفتكر التطبيقات اللي اتحذفت علشان ترجّعها من Play بسرعة. */
    private fun trackRemoved(prev: List<AppEntry>, now: List<AppEntry>) {
        val nowPk = now.map { it.packageName }.toSet()
        val gone = prev.filter { it.packageName !in nowPk }.distinctBy { it.packageName }
        val cur = _state.value.removed
        val back = cur.filter { it.substringBefore('|') in nowPk }
        if (gone.isEmpty() && back.isEmpty()) return
        val added = gone.map { it.packageName + "|" + it.label }
        val list2 = (added + cur.filter { it !in back }).distinctBy { it.substringBefore('|') }.take(20)
        if (list2 != cur) update { it.copy(removed = list2) }
    }

    // ───────────── متجر الثيمات ─────────────

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    private val _installed = MutableStateFlow<Map<String, ThemeDef>>(emptyMap())
    val installed: StateFlow<Map<String, ThemeDef>> = _installed.asStateFlow()

    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun refreshInstalled() {
        viewModelScope.launch {
            _installed.value = ThemeStore.loadInstalled(app).associateBy { it.id }
        }
    }

    fun loadCatalog() {
        _catalog.value = CatalogState.Loading
        viewModelScope.launch {
            _catalog.value = try {
                val (list, offline) = ThemeStore.fetchCatalog(app)
                CatalogState.Ready(list, offline)
            } catch (e: Exception) {
                CatalogState.Error(e.message ?: "error")
            }
        }
    }

    fun downloadTheme(def: ThemeDef) {
        if (def.id in _busy.value) return
        _busy.update { it + def.id }
        viewModelScope.launch {
            try {
                ThemeStore.download(app, def)
                _installed.value = ThemeStore.loadInstalled(app).associateBy { it.id }
            } catch (e: Exception) {
                _message.value = tr("تعذّر التحميل، اتأكد من الإنترنت", "Download failed. Check your connection.")
            } finally {
                _busy.update { it - def.id }
            }
        }
    }

    fun applyTheme(def: ThemeDef, quiet: Boolean = false) {
        viewModelScope.launch {
            val r = ThemeStore.applyWallpaper(app, def.id)
            ThemeStore.setLiveTheme(app, def.id)
            update {
                it.copy(
                    themeId = def.id, theme = def.mode, accent = def.accent,
                    homeText = def.homeText, clock = def.clock, iconStyle = def.icon,
                )
            }
            reloadApps()
            if (quiet) return@launch
            val vivo = android.os.Build.MANUFACTURER.lowercase().let { m -> "vivo" in m || "iqoo" in m }
            _message.value = when {
                !r.system -> tr("اتطبّق الستايل بس، والخلفية ماتغيرتش", "Style applied, but the wallpaper wasn't changed")
                !r.lock -> tr("اتطبّق الثيم، لكن الجهاز رفض تغيير شاشة القفل", "Theme applied, but the device refused to change the lock screen")
                vivo -> tr(
                    "اتطبّق الثيم. لو شاشة القفل ماتغيّرتش جرّب «خلفية حيّة» من تفاصيل الثيم",
                    "Theme applied. If the lock screen didn't change, try the live wallpaper from the theme details",
                )
                else -> tr("اتطبّق الثيم", "Theme applied")
            }
        }
    }

    /** خلفية حيّة بصورة الثيم: فيفو بيحترمها على القفل والرئيسية. */
    fun openLiveWallpaper(def: ThemeDef?) {
        val id = def?.id ?: _state.value.themeId
        if (id != null) ThemeStore.setLiveTheme(app, id)
        val comp = ComponentName(app, AuraWallpaperService::class.java)
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, comp)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { app.startActivity(direct) }.isSuccess
        if (!ok) {
            runCatching {
                app.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        _message.value = tr(
            "اختار «تطبيق» وبعدين الشاشة الرئيسية والقفل معاً",
            "Tap Apply, then choose Home and Lock screens",
        )
    }

    fun saveWallpaperToGallery(def: ThemeDef) {
        viewModelScope.launch {
            val ok = ThemeStore.saveToGallery(app, def.id)
            _message.value = if (ok) tr("اتحفظت في المعرض (Pictures/Aura)", "Saved to gallery (Pictures/Aura)")
            else tr("تعذّر الحفظ، حمّل الثيم الأول", "Couldn't save. Download the theme first")
        }
    }

    private fun decodeUri(uri: Uri, maxEdge: Int): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        app.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()

    private fun gradientBitmap(accent: Int): Bitmap {
        val w = 1080
        val h = 2340
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val top = androidx.core.graphics.ColorUtils.blendARGB(accent, 0xFF000000.toInt(), 0.55f)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, 0xFF08080C.toInt(), Shader.TileMode.CLAMP)
        }
        Canvas(bmp).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }

    /** يحفظ الشكل الحالي (ألوان/أيقونات/ساعة) كثيم جديد، بخلفية من المعرض لو اخترت واحدة. */
    fun saveCurrentAsTheme(name: String, image: Uri?) {
        viewModelScope.launch {
            val s = _state.value
            val id = "my-" + newId()
            val ok = runCatching {
                val jpg = withContext(Dispatchers.IO) {
                    val bmp = (image?.let { decodeUri(it, 2400) })
                        ?: s.themeId?.let { tid ->
                            ThemeStore.wallpaperFile(app, tid).takeIf { it.exists() }
                                ?.let { f -> BitmapFactory.decodeFile(f.path) }
                        }
                        ?: gradientBitmap(s.accent.toInt())
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.toByteArray()
                }
                ThemeStore.saveCustom(app, id, customThemeJson(id, name.ifBlank { tr("ثيمي", "My theme") }, s), jpg)
                _installed.value = ThemeStore.loadInstalled(app).associateBy { it.id }
                ThemeStore.setLiveTheme(app, id)
                update { it.copy(themeId = id) }
            }.isSuccess
            _message.value = if (ok) tr("اتحفظ الثيم في «المحمّلة» بالمتجر", "Theme saved under Downloaded in the store")
            else tr("تعذّر حفظ الثيم", "Couldn't save the theme")
        }
    }

    /** صورة ودجت الصورة: بنصغّرها ونحفظها جوه التطبيق. */
    fun setPhoto(uid: String, uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = decodeUri(uri, 1600) ?: return@runCatching false
                    val dir = java.io.File(app.filesDir, "photos").apply { mkdirs() }
                    java.io.File(dir, "$uid.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    true
                }.getOrDefault(false)
            }
            if (ok) update { it.updateBuiltin(uid) { b -> b.copy(data = System.currentTimeMillis().toString()) } }
            else _message.value = tr("تعذّر فتح الصورة", "Couldn't open the image")
        }
    }

    /** جدول الثيم: نهاري/ليلي حسب الساعة. */
    fun applyScheduled() {
        val s = _state.value
        if (!s.scheduleOn) return
        val hour = java.time.LocalTime.now().hour
        val night = if (s.nightHour > s.dayHour) hour >= s.nightHour || hour < s.dayHour
        else hour in s.nightHour until s.dayHour
        val want = (if (night) s.nightTheme else s.dayTheme) ?: return
        if (want == s.themeId) return
        val def = _installed.value[want] ?: return
        applyTheme(def, quiet = true)
    }

    fun postMessage(text: String) { _message.value = text }

    /** يرجّع الستايل الافتراضي (الخلفية بتفضل زي ما هي). */
    fun resetTheme() {
        update {
            it.copy(
                themeId = null, theme = ThemeMode.DARK, accent = 0xFF00E5FF,
                homeText = null, clock = ClockStyle.NONE, iconStyle = IconStyle(),
            )
        }
        reloadApps()
    }

    fun deleteTheme(id: String) {
        viewModelScope.launch {
            ThemeStore.delete(app, id)
            _installed.value = ThemeStore.loadInstalled(app).associateBy { it.id }
        }
    }

    private fun customIconFile(key: String): java.io.File =
        java.io.File(app.filesDir, "appicons").apply { mkdirs() }.resolve(Integer.toHexString(key.hashCode()) + ".png")

    fun setAppIcon(key: String, uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val b = decodeUri(uri, 256) ?: return@runCatching false
                    val side = minOf(b.width, b.height)
                    val sq = android.graphics.Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
                    val out = android.graphics.Bitmap.createScaledBitmap(sq, 192, 192, true)
                    customIconFile(key).outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    true
                }.getOrDefault(false)
            }
            if (ok) { update { it.copy(customIcons = it.customIcons + key) }; loadAppsNow() }
        }
    }

    fun renameApp(key: String, name: String) {
        update { it.copy(appNames = if (name.isBlank()) it.appNames - key else it.appNames + (key to name.trim())) }
        reloadApps()
    }

    fun resetAppCustom(key: String) {
        runCatching { customIconFile(key).delete() }
        update { it.copy(customIcons = it.customIcons - key, appNames = it.appNames - key) }
        reloadApps()
    }

    fun addHistory(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        update { it.copy(searchHistory = (listOf(t) + it.searchHistory.filter { h -> h != t }).take(8)) }
    }

    private var reloadJob: Job? = null

    fun reloadApps() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch { loadAppsNow() }
    }

    /** لأحداث تثبيت/تحديث الحزم: بتيجي دفعات، فنستنى ونعمل تحميل واحد بس. */
    fun reloadAppsDebounced() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch { delay(500); loadAppsNow() }
    }

    private var persistJob: Job? = null

    /** تعديل الحالة: بيتطبّق فوراً، والحفظ بيتأجّل ٢٥٠ms (debounce) وبيتبني الـ JSON خارج الـ UI thread. */
    fun update(block: (AuraState) -> AuraState) {
        _state.update(block)
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(250)
            persistNow()
        }
    }

    private suspend fun persistNow() {
        val json = withContext(Dispatchers.Default) { _state.value.toJson().toString() }
        app.dataStore.edit { it[STATE_KEY] = json }
    }

    /** نحفظ فوراً لما الشاشة تخرج، علشان آخر تعديل ما يضيعش لو العملية اتقفلت. */
    fun flushState() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch { persistNow() }
    }

    fun setIconPack(pkg: String?) {
        update { it.copy(iconPack = pkg) }
        reloadApps()
    }

    fun recordLaunch(key: String) {
        update { s ->
            val old = s.usage[key]
            val slot = java.time.LocalTime.now().hour / 6
            val slots = (old?.slots?.takeIf { it.size == 4 } ?: List(4) { 0 }).toMutableList().also { it[slot] = it[slot] + 1 }
            s.copy(usage = s.usage + (key to UsageStat((old?.count ?: 0) + 1, System.currentTimeMillis(), slots)))
        }
    }

    fun homePressed() {
        _homeTick.update { it + 1 }
    }

    fun say(m: String) { _message.value = m }

    /** نسخة احتياطية كاملة: الترتيب + الإعدادات + صور الودجتس. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val o = _state.value.toJson(forBackup = true)
        val photos = JSONObject()
        java.io.File(app.filesDir, "photos").listFiles()?.forEach { f ->
            runCatching { photos.put(f.nameWithoutExtension, android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)) }
        }
        o.put("photos", photos)
        val themes = JSONObject()
        java.io.File(app.filesDir, "themes").listFiles()?.filter { it.isDirectory && it.name.startsWith("my-") }?.forEach { d ->
            runCatching {
                themes.put(
                    d.name,
                    JSONObject()
                        .put("json", java.io.File(d, "theme.json").readText())
                        .put("wp", android.util.Base64.encodeToString(java.io.File(d, "wallpaper.jpg").readBytes(), android.util.Base64.NO_WRAP)),
                )
            }
        }
        o.put("themes", themes)
        o.toString(2)
    }

    fun exportBackupTo(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                val text = exportJson()
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("no stream")
                }
            }.isSuccess
            say(if (ok) tr("تم حفظ النسخة الاحتياطية", "Backup saved") else tr("فشل حفظ النسخة الاحتياطية", "Backup failed"))
        }
    }

    /**
     * يستعيد من ملف: بيقرأ بحد أقصى للحجم، بيتحقق الأول، وبعدين يطبّق.
     * [onApplied] بيتنادى بس لو الاستعادة نجحت (علشان الودجتس القديمة تتمسح بعد النجاح مش قبله).
     */
    fun restoreBackupFrom(uri: Uri, onApplied: () -> Unit) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(uri)?.use { ins ->
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_BACKUP_BYTES) error("too large")
                            out.write(buf, 0, n)
                        }
                        out.toString(Charsets.UTF_8.name())
                    }
                }.getOrNull()
            }
            when {
                text == null -> say(tr("الملف كبير أو مش مقروء", "File is too large or unreadable"))
                importJson(text) -> { onApplied(); say(tr("تمت الاستعادة", "Backup restored")) }
                else -> say(tr("الملف مش نسخة احتياطية صالحة — ما اتغيّرش حاجة", "Not a valid backup — nothing was changed"))
            }
        }
    }

    suspend fun importJson(text: String): Boolean {
        // التحليل (وممكن يكون كبير) بره الـ UI thread، ولازم الملف يبان نسخة Aura فعلاً
        val pair = withContext(Dispatchers.Default) {
            val r = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext null
            if (!r.has("pages")) return@withContext null
            val p = runCatching { auraStateFromJson(r) }.getOrNull() ?: return@withContext null
            r to p
        } ?: return false
        val (root, parsed) = pair
        withContext(Dispatchers.IO) {
            root.optJSONObject("themes")?.let { th ->
                th.keys().forEach { id ->
                    runCatching {
                        if (id.startsWith("my-") && id.all { it.isLetterOrDigit() || it == '-' }) {
                            val d = java.io.File(app.filesDir, "themes/$id").apply { mkdirs() }
                            val e = th.getJSONObject(id)
                            java.io.File(d, "theme.json").writeText(e.getString("json"))
                            java.io.File(d, "wallpaper.jpg").writeBytes(android.util.Base64.decode(e.getString("wp"), android.util.Base64.NO_WRAP))
                        }
                    }
                }
            }
            root.optJSONObject("photos")?.let { ph ->
                val dir = java.io.File(app.filesDir, "photos").apply { mkdirs() }
                ph.keys().forEach { k ->
                    runCatching {
                        if (k.all { it.isLetterOrDigit() || it == '-' || it == '_' })
                            java.io.File(dir, "$k.jpg").writeBytes(android.util.Base64.decode(ph.getString(k), android.util.Base64.NO_WRAP))
                    }
                }
            }
        }
        if (root.optJSONObject("themes") != null) {
            _installed.value = ThemeStore.loadInstalled(app).associateBy { it.id }
        }
        update { cur ->
            parsed.copy(
                pinHash = cur.pinHash,
                pinSalt = cur.pinSalt,
                biometric = cur.biometric,
                dockInitialized = true,
            )
        }
        reloadApps()
        return true
    }
}
