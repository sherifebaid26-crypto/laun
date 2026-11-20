package com.aura.launcher

import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { DARK, LIGHT, GLASS, COLOR }

sealed class HomeItem {
    data class App(val key: String) : HomeItem()
    data class Folder(val id: String, val name: String, val keys: List<String>) : HomeItem()
    data class Widget(val widgetId: Int) : HomeItem()
}

data class UsageStat(val count: Int, val last: Long)

data class AuraState(
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Long = 0xFF00E5FF,
    val columns: Int = 4,
    val rows: Int = 5,
    val iconScale: Float = 1f,
    val showLabels: Boolean = true,
    val iconPack: String? = null,
    val gestures: Boolean = true,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val biometric: Boolean = false,
    val pages: List<List<HomeItem>> = listOf(emptyList()),
    val dock: List<String> = emptyList(),
    val dockInitialized: Boolean = false,
    val hidden: Set<String> = emptySet(),
    val locked: Set<String> = emptySet(),
    val usage: Map<String, UsageStat> = emptyMap(),
)

const val MAX_DOCK = 5

// ───────────────────────── عمليات الشاشة الرئيسية ─────────────────────────

private fun AuraState.slotsOf(item: HomeItem): Int =
    if (item is HomeItem.Widget) columns * 2 else 1

private fun AuraState.used(page: List<HomeItem>): Int = page.sumOf { slotsOf(it) }

fun AuraState.trim(): AuraState {
    val nonEmpty = pages.filter { it.isNotEmpty() }
    return copy(pages = if (nonEmpty.isEmpty()) listOf(emptyList()) else nonEmpty)
}

fun AuraState.addItem(item: HomeItem): AuraState {
    val need = slotsOf(item)
    val cap = columns * rows
    val idx = pages.indexOfFirst { used(it) + need <= cap }
    val newPages = if (idx >= 0) {
        pages.mapIndexed { i, p -> if (i == idx) p + item else p }
    } else {
        pages + listOf(listOf(item))
    }
    return copy(pages = newPages)
}

fun AuraState.isOnHome(key: String): Boolean = pages.any { page ->
    page.any { (it is HomeItem.App && it.key == key) || (it is HomeItem.Folder && key in it.keys) }
}

fun AuraState.removeApp(key: String): AuraState {
    val newPages = pages.map { page ->
        page.mapNotNull { item ->
            when (item) {
                is HomeItem.App -> if (item.key == key) null else item
                is HomeItem.Folder -> item.copy(keys = item.keys.filter { it != key })
                is HomeItem.Widget -> item
            }
        }
    }
    return copy(pages = newPages).trim()
}

fun AuraState.removeWidget(id: Int): AuraState = copy(
    pages = pages.map { p -> p.filterNot { it is HomeItem.Widget && it.widgetId == id } }
).trim()

fun AuraState.addFolder(id: String, name: String, keys: List<String>): AuraState {
    var s = this
    for (k in keys) s = s.removeApp(k)
    return s.addItem(HomeItem.Folder(id, name, keys))
}

fun AuraState.renameFolder(id: String, name: String): AuraState = copy(
    pages = pages.map { p -> p.map { if (it is HomeItem.Folder && it.id == id) it.copy(name = name) else it } }
)

fun AuraState.setFolderApps(id: String, keys: List<String>): AuraState {
    val set = keys.toSet()
    val newPages = pages.map { page ->
        page.mapNotNull { item ->
            when (item) {
                is HomeItem.App -> if (item.key in set) null else item
                is HomeItem.Folder ->
                    if (item.id == id) item.copy(keys = keys)
                    else item.copy(keys = item.keys.filter { it !in set })
                is HomeItem.Widget -> item
            }
        }
    }
    return copy(pages = newPages).trim()
}

fun AuraState.folderKeys(id: String): List<String> =
    pages.flatten().filterIsInstance<HomeItem.Folder>().firstOrNull { it.id == id }?.keys ?: emptyList()

fun AuraState.deleteFolder(id: String): AuraState {
    val folder = pages.flatten().filterIsInstance<HomeItem.Folder>().firstOrNull { it.id == id }
        ?: return this
    var s = copy(pages = pages.map { p -> p.filterNot { it is HomeItem.Folder && it.id == id } }).trim()
    for (k in folder.keys) s = s.addItem(HomeItem.App(k))
    return s
}

fun AuraState.hideApp(key: String): AuraState =
    copy(hidden = hidden + key, dock = dock - key).removeApp(key)

fun AuraState.unhideApp(key: String): AuraState = copy(hidden = hidden - key)

fun AuraState.dockAdd(key: String): AuraState =
    if (key in dock || dock.size >= MAX_DOCK) this else copy(dock = dock + key)

fun AuraState.dockRemove(key: String): AuraState = copy(dock = dock - key)

/** يعيد توزيع كل العناصر على الصفحات (بعد تغيير عدد الأعمدة/الصفوف). */
fun AuraState.reflow(): AuraState {
    var s = copy(pages = listOf(emptyList()))
    for (item in pages.flatten()) s = s.addItem(item)
    return s
}

// ───────────────────────── JSON ─────────────────────────

private fun itemToJson(item: HomeItem): JSONObject = when (item) {
    is HomeItem.App -> JSONObject().put("t", "a").put("k", item.key)
    is HomeItem.Folder -> JSONObject().put("t", "f").put("id", item.id)
        .put("n", item.name).put("ks", JSONArray(item.keys))
    is HomeItem.Widget -> JSONObject().put("t", "w").put("id", item.widgetId)
}

private fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }

private fun itemFromJson(o: JSONObject): HomeItem? = when (o.optString("t")) {
    "a" -> HomeItem.App(o.getString("k"))
    "f" -> HomeItem.Folder(o.getString("id"), o.optString("n"), o.optJSONArray("ks").toStringList())
    "w" -> HomeItem.Widget(o.getInt("id"))
    else -> null
}

fun AuraState.toJson(forBackup: Boolean = false): JSONObject {
    val o = JSONObject()
    o.put("v", 1)
    o.put("theme", theme.name)
    o.put("accent", accent)
    o.put("columns", columns)
    o.put("rows", rows)
    o.put("iconScale", iconScale.toDouble())
    o.put("labels", showLabels)
    o.put("gestures", gestures)
    iconPack?.let { o.put("iconPack", it) }
    if (!forBackup) {
        pinHash?.let { o.put("pinHash", it) }
        pinSalt?.let { o.put("pinSalt", it) }
        o.put("biometric", biometric)
    }
    val pagesJson = JSONArray()
    for (page in pages) {
        val arr = JSONArray()
        for (item in page) {
            if (forBackup && item is HomeItem.Widget) continue
            arr.put(itemToJson(item))
        }
        pagesJson.put(arr)
    }
    o.put("pages", pagesJson)
    o.put("dock", JSONArray(dock))
    o.put("dockInit", dockInitialized)
    o.put("hidden", JSONArray(hidden.toList()))
    o.put("locked", JSONArray(locked.toList()))
    val u = JSONObject()
    for ((k, v) in usage) u.put(k, JSONObject().put("c", v.count).put("l", v.last))
    o.put("usage", u)
    return o
}

fun auraStateFromJson(o: JSONObject): AuraState {
    val d = AuraState()
    val pages = o.optJSONArray("pages")?.let { arr ->
        List(arr.length()) { i ->
            val p = arr.getJSONArray(i)
            List(p.length()) { j -> itemFromJson(p.getJSONObject(j)) }.filterNotNull()
        }
    } ?: d.pages
    val usage = o.optJSONObject("usage")?.let { u ->
        u.keys().asSequence().associateWith { k ->
            val e = u.getJSONObject(k)
            UsageStat(e.optInt("c"), e.optLong("l"))
        }
    } ?: emptyMap()
    return AuraState(
        theme = runCatching { ThemeMode.valueOf(o.optString("theme", "DARK")) }.getOrDefault(ThemeMode.DARK),
        accent = o.optLong("accent", d.accent),
        columns = o.optInt("columns", d.columns).coerceIn(3, 6),
        rows = o.optInt("rows", d.rows).coerceIn(4, 7),
        iconScale = o.optDouble("iconScale", 1.0).toFloat().coerceIn(0.7f, 1.4f),
        showLabels = o.optBoolean("labels", true),
        iconPack = if (o.has("iconPack")) o.getString("iconPack") else null,
        gestures = o.optBoolean("gestures", true),
        pinHash = if (o.has("pinHash")) o.getString("pinHash") else null,
        pinSalt = if (o.has("pinSalt")) o.getString("pinSalt") else null,
        biometric = o.optBoolean("biometric", false),
        pages = if (pages.isEmpty()) d.pages else pages,
        dock = o.optJSONArray("dock").toStringList(),
        dockInitialized = o.optBoolean("dockInit", false),
        hidden = o.optJSONArray("hidden").toStringList().toSet(),
        locked = o.optJSONArray("locked").toStringList().toSet(),
        usage = usage,
    )
}
