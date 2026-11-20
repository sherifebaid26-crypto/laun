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
        }
    }

    private suspend fun loadAppsNow() {
        _apps.value = AppRepository.loadApps(app, _state.value.iconPack)
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
