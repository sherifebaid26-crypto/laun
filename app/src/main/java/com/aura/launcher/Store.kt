package com.aura.launcher

import android.app.Application
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
            if (raw != null) runCatching { _state.value = auraStateFromJson(JSONObject(raw)) }
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
        _apps.value = AppRepository.loadApps(app, _state.value.iconPack, _state.value.iconStyle)
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

    fun applyTheme(def: ThemeDef) {
        viewModelScope.launch {
            val r = ThemeStore.applyWallpaper(app, def.id)
            update {
                it.copy(
                    themeId = def.id, theme = def.mode, accent = def.accent,
                    homeText = def.homeText, clock = def.clock, iconStyle = def.icon,
                )
            }
            reloadApps()
            val vivo = android.os.Build.MANUFACTURER.lowercase().let { m -> "vivo" in m || "iqoo" in m }
            _message.value = when {
                !r.system -> tr("اتطبّق الستايل بس، والخلفية ماتغيرتش", "Style applied, but the wallpaper wasn't changed")
                !r.lock -> tr("اتطبّق الثيم، لكن الجهاز رفض تغيير شاشة القفل", "Theme applied, but the device refused to change the lock screen")
                vivo -> tr(
                    "اتطبّق الثيم. لو شاشة القفل ماتغيّرتش، غيّرها من إعدادات فيفو ← الخلفية",
                    "Theme applied. If the lock screen didn't change, set it from Vivo's wallpaper settings",
                )
                else -> tr("اتطبّق الثيم", "Theme applied")
            }
        }
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

    fun reloadApps() {
        viewModelScope.launch { loadAppsNow() }
    }

    fun update(block: (AuraState) -> AuraState) {
        _state.update(block)
        val json = _state.value.toJson().toString()
        viewModelScope.launch { app.dataStore.edit { it[STATE_KEY] = json } }
    }

    fun setIconPack(pkg: String?) {
        update { it.copy(iconPack = pkg) }
        reloadApps()
    }

    fun recordLaunch(key: String) {
        update { s ->
            val old = s.usage[key]
            s.copy(usage = s.usage + (key to UsageStat((old?.count ?: 0) + 1, System.currentTimeMillis())))
        }
    }

    fun homePressed() {
        _homeTick.update { it + 1 }
    }

    fun exportJson(): String = _state.value.toJson(forBackup = true).toString(2)

    fun importJson(text: String): Boolean {
        val parsed = runCatching { auraStateFromJson(JSONObject(text)) }.getOrNull() ?: return false
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
