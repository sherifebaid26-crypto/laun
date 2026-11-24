package com.aura.launcher

import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { DARK, LIGHT, GLASS, COLOR }

enum class IconShape { ORIGINAL, CIRCLE, SQUIRCLE, ROUNDED, TEARDROP }

enum class ClockStyle { NONE, BIG, BIG_SERIF, SMALL }

/** شكل قائمة الضغط المطوّل. */
enum class MenuStyle { IOS, SAMSUNG, VIVO }

/** إيماءات الشاشة الرئيسية. */
enum class GestureAction { NONE, NOTIFICATIONS, SEARCH, DRAWER, SETTINGS, LOCK, RECENTS, ONE_HAND, FEED }

/** ستايل الأيقونات اللي بيجي مع الثيم: شكل + صحيفة خلفية + (اختياري) أيقونة أحادية اللون. */
data class IconStyle(
    val shape: IconShape = IconShape.ORIGINAL,
    val plate: Long = 0L,          // ARGB؛ الشفافية صفر = بدون صحيفة
    val mono: Boolean = false,     // أيقونة أحادية اللون موحّدة (طبقة النظام لو موجودة، وإلا بديل)
    val tint: Long = 0xFFFFFFFF,
)

/** عنصر على الشاشة الرئيسية. cell = رقم الخلية في الصفحة (صف*أعمدة+عمود)، و-1 = لسه مالهوش مكان. */
sealed class HomeItem {
    abstract val cell: Int
    abstract val ref: String
    abstract fun at(cell: Int): HomeItem

    data class App(val key: String, override val cell: Int = -1) : HomeItem() {
        override val ref: String get() = "a:$key"
        override fun at(cell: Int): HomeItem = copy(cell = cell)
    }

    data class Folder(val id: String, val name: String, val keys: List<String>, override val cell: Int = -1, val big: Boolean = false) : HomeItem() {
        val isPair: Boolean get() = id.startsWith("pair-")
        override val ref: String get() = "f:$id"
        override fun at(cell: Int): HomeItem = copy(cell = cell)
    }

    data class Widget(val widgetId: Int, override val cell: Int = -1) : HomeItem() {
        override val ref: String get() = "w:$widgetId"
        override fun at(cell: Int): HomeItem = copy(cell = cell)
    }

    /** ودجت داخلية من Aura: clock / battery / search. */
    data class Builtin(
        val kind: String,
        val uid: String,
        override val cell: Int = -1,
        val cols: Int = 0,        // 0 = عرض كامل
        val rows: Int = 0,        // 0 = الافتراضي حسب النوع
        val bg: Int = 0,          // 0 لوحة، 1 شفاف، 2 زجاجي، 3 لون Aura
        val radius: Int = 24,     // نصف قطر الحواف بالـ dp
        val data: String = "",    // نص إضافي (الملاحظات)
    ) : HomeItem() {
        override val ref: String get() = "b:$uid"
        override fun at(cell: Int): HomeItem = copy(cell = cell)
    }
}

data class UsageStat(val count: Int, val last: Long, val slots: List<Int> = emptyList())

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
    val themeId: String? = null,
    val iconStyle: IconStyle = IconStyle(),
    val clock: ClockStyle = ClockStyle.NONE,
    val homeText: Long? = null,
    val menuStyle: MenuStyle = MenuStyle.IOS,
    val drawerColumns: Int = 4,
    val themeIcons: Boolean = true,
    val badgePrompted: Boolean = false,
    val focusOn: Boolean = false,
    val focusApps: Set<String> = emptySet(),
    val focusAuto: Boolean = false,
    val focusFrom: Int = 9,
    val focusTo: Int = 17,
    val limits: Map<String, Int> = emptyMap(),
    val drawerSort: Int = 0,
    val nightIcons: Boolean = false,
    val removed: List<String> = emptyList(),
    val swipeDown: GestureAction = GestureAction.SEARCH,
    val lang: String = "ar",
    val liveIcons: Boolean = true,
    val onboarded: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val appNames: Map<String, String> = emptyMap(),
    val customIcons: Set<String> = emptySet(),
    val webSuggest: Boolean = false,
    val dynamicColor: Boolean = false,
    val drawerAlpha: Int = 92,
    val dockAlpha: Int = 100,
    val blurOn: Boolean = false,
    val edgeOn: Boolean = true,
    val edgeApps: List<String> = emptyList(),
    val rules: List<String> = emptyList(),
    val lockedPages: Set<Int> = emptySet(),
    val iconShape: Int = 0,
    val relaxOn: Boolean = false,
    val relaxHour: Int = 23,
    val doubleTap: GestureAction = GestureAction.NONE,
    val badges: Boolean = false,
    val scheduleOn: Boolean = false,
    val dayTheme: String? = null,
    val nightTheme: String? = null,
    val dayHour: Int = 7,
    val nightHour: Int = 19,
    val feedOn: Boolean = true,
    val feedUrl: String = "",
    val tasks: List<String> = emptyList(),   // "0|نص" أو "1|نص" (1 = منجزة)
    val oneHandLeft: Boolean = false,
    val dockSwipe: Boolean = true,
    val dockFolders: Map<String, HomeItem.Folder> = emptyMap(),
)

const val MAX_DOCK = 5

// ───────────────────────── هندسة الشبكة ─────────────────────────

fun AuraState.colsOf(i: HomeItem): Int = when (i) {
    is HomeItem.App -> 1
    is HomeItem.Folder -> if (i.big) 2.coerceAtMost(columns) else 1
    is HomeItem.Widget -> columns
    is HomeItem.Builtin -> if (i.cols <= 0) columns else i.cols.coerceAtMost(columns)
}

fun AuraState.rowsOf(i: HomeItem): Int = when (i) {
    is HomeItem.App -> 1
    is HomeItem.Folder -> if (i.big) 2.coerceAtMost(rows) else 1
    is HomeItem.Widget -> 2
    is HomeItem.Builtin -> (if (i.rows > 0) i.rows else defaultBuiltinRows(i.kind)).coerceAtMost(rows)
}

fun defaultBuiltinRows(kind: String): Int = when (kind) {
    "search" -> 1
    "calendar" -> 3
    "notes" -> 2
    "apps" -> 1
    "photo", "prayer", "stack", "agenda", "usage7" -> 3
    else -> 2
}

fun AuraState.footprint(i: HomeItem, cell: Int): Set<Int> {
    val r = cell / columns
    val c = cell % columns
    val out = HashSet<Int>()
    for (dr in 0 until rowsOf(i)) for (dc in 0 until colsOf(i)) out += (r + dr) * columns + (c + dc)
    return out
}

private fun AuraState.fits(i: HomeItem, cell: Int): Boolean =
    cell >= 0 && cell < columns * rows &&
        (cell / columns) + rowsOf(i) <= rows && (cell % columns) + colsOf(i) <= columns

private fun AuraState.occupied(page: List<HomeItem>, ignoreRef: String? = null): Set<Int> {
    val out = HashSet<Int>()
    for (it in page) if (it.ref != ignoreRef && it.cell >= 0) out += footprint(it, it.cell)
    return out
}

private fun AuraState.firstFree(page: List<HomeItem>, item: HomeItem, nearTo: Int? = null): Int? {
    val occ = occupied(page, item.ref)
    val candidates = (0 until columns * rows).filter { c ->
        fits(item, c) && footprint(item, c).none { it in occ }
    }
    if (nearTo == null) return candidates.firstOrNull()
    val nr = nearTo / columns
    val nc = nearTo % columns
    return candidates.minByOrNull { Math.abs(it / columns - nr) + Math.abs(it % columns - nc) }
}

fun AuraState.trim(): AuraState {
    val nonEmpty = pages.filter { it.isNotEmpty() }
    return copy(pages = if (nonEmpty.isEmpty()) listOf(emptyList()) else nonEmpty)
}

/** يثبّت أي عنصر مالوش خلية صالحة (حفظ قديم أو تغيير الأعمدة) في أول مكان فاضي. */
fun AuraState.normalizeCells(): AuraState {
    val out = mutableListOf<List<HomeItem>>()
    val overflow = mutableListOf<HomeItem>()
    for (page in pages) {
        val placed = mutableListOf<HomeItem>()
        for (item in page) {
            val ok = fits(item, item.cell) && footprint(item, item.cell).none { it in occupied(placed) }
            if (ok) {
                placed += item
            } else {
                val spot = firstFree(placed, item)
                if (spot != null) placed += item.at(spot) else overflow += item
            }
        }
        out += listOf<List<HomeItem>>(placed)
    }
    var res = copy(pages = if (out.isEmpty()) listOf(emptyList()) else out)
    for (o in overflow) res = res.addItem(o)
    return res.trim()
}

fun AuraState.addItem(item: HomeItem): AuraState {
    for ((pi, page) in pages.withIndex()) {
        val spot = firstFree(page, item)
        if (spot != null) {
            return copy(pages = pages.mapIndexed { i, p -> if (i == pi) p + item.at(spot) else p })
        }
    }
    return copy(pages = pages + listOf(listOf(item.at(0))))
}

fun AuraState.isOnHome(key: String): Boolean = pages.any { page ->
    page.any { (it is HomeItem.App && it.key == key) || (it is HomeItem.Folder && key in it.keys) }
}

fun AuraState.removeRef(ref: String): AuraState =
    copy(pages = pages.map { p -> p.filterNot { it.ref == ref } })

fun AuraState.removeApp(key: String): AuraState {
    val newPages = pages.map { page ->
        page.mapNotNull { item ->
            when (item) {
                is HomeItem.App -> if (item.key == key) null else item
                is HomeItem.Folder -> item.copy(keys = item.keys.filter { it != key })
                else -> item
            }
        }
    }
    return copy(pages = newPages).trim()
}

fun AuraState.removeByRef(ref: String): AuraState =
    copy(pages = pages.map { p -> p.filterNot { it.ref == ref } }).trim()

fun AuraState.removeWidget(id: Int): AuraState = removeByRef("w:$id")

fun AuraState.addFolder(id: String, name: String, keys: List<String>): AuraState {
    var s = this
    for (k in keys) s = s.removeApp(k)
    return s.addItem(HomeItem.Folder(id, name, keys))
}

fun AuraState.renameFolder(id: String, name: String): AuraState = copy(
    pages = pages.map { p -> p.map { if (it is HomeItem.Folder && it.id == id) it.copy(name = name) else it } },
    dockFolders = dockFolders.mapValues { (k, f) -> if (k == id) f.copy(name = name) else f },
)

fun AuraState.findFolder(id: String): HomeItem.Folder? =
    pages.flatten().filterIsInstance<HomeItem.Folder>().firstOrNull { it.id == id } ?: dockFolders[id]

fun AuraState.setFolderApps(id: String, keys: List<String>): AuraState {
    val set = keys.toSet()
    val newPages = pages.map { page ->
        page.mapNotNull { item ->
            when (item) {
                is HomeItem.App -> if (item.key in set) null else item
                is HomeItem.Folder ->
                    if (item.id == id) item.copy(keys = keys)
                    else item.copy(keys = item.keys.filter { it !in set })
                else -> item
            }
        }
    }
    val df = dockFolders.mapValues { (fid, f) ->
        if (fid == id) f.copy(keys = keys) else f.copy(keys = f.keys.filter { it !in set })
    }
    return copy(pages = newPages, dockFolders = df).trim()
}

fun AuraState.folderKeys(id: String): List<String> = findFolder(id)?.keys ?: emptyList()

fun AuraState.deleteFolder(id: String): AuraState {
    dockFolders[id]?.let { f ->
        var s = copy(dock = dock - "f:$id", dockFolders = dockFolders - id)
        for (k in f.keys) s = s.addItem(HomeItem.App(k))
        return s
    }
    val folder = pages.flatten().filterIsInstance<HomeItem.Folder>().firstOrNull { it.id == id }
        ?: return this
    var s = removeByRef(folder.ref)
    for (k in folder.keys) s = s.addItem(HomeItem.App(k))
    return s
}

fun AuraState.folderToHome(id: String): AuraState {
    val f = dockFolders[id] ?: return this
    return copy(dock = dock - "f:$id", dockFolders = dockFolders - id).addItem(f.copy(cell = -1))
}

fun AuraState.hideApp(key: String): AuraState =
    copy(hidden = hidden + key, dock = dock - key).removeApp(key)

fun AuraState.unhideApp(key: String): AuraState = copy(hidden = hidden - key)

fun AuraState.dockAdd(key: String): AuraState =
    if (key in dock || dock.size >= MAX_DOCK) this else copy(dock = dock + key)

fun AuraState.dockRemove(key: String): AuraState = copy(dock = dock - key)

/** يعيد توزيع كل العناصر على الصفحات (بعد تغيير عدد الأعمدة/الصفوف). */
fun AuraState.reflow(): AuraState {
    val items = pages.flatMap { p -> p.sortedBy { it.cell } }
    var s = copy(pages = listOf(emptyList()))
    for (item in items) s = s.addItem(item.at(-1))
    return s
}

// ───────────────────────── السحب والإفلات ─────────────────────────

private fun AuraState.placeAt(item: HomeItem, page: Int, rawCell: Int, center: Boolean): AuraState {
    val pgs = pages.toMutableList()
    while (pgs.size <= page) pgs.add(emptyList())
    val wide = colsOf(item) >= columns
    var c = if (wide) (rawCell / columns) * columns else rawCell
    // لو العنصر مش داخل الصفحة من تحت، نطلّعه لآخر صف مناسب
    val maxRow = rows - rowsOf(item)
    if (c / columns > maxRow) c = maxRow * columns + (c % columns)
    if (!wide && c % columns + colsOf(item) > columns) c = (c / columns) * columns + (columns - colsOf(item))
    val base = copy(pages = pgs)
    val pageItems = pgs[page]
    val occ = base.occupied(pageItems)
    val fp = base.footprint(item, c)

    fun withPage(items: List<HomeItem>): AuraState =
        base.copy(pages = pgs.mapIndexed { i, p -> if (i == page) items else p })

    if (fp.none { it in occ }) return withPage(pageItems + item.at(c))

    val target = pageItems.firstOrNull { it.cell >= 0 && c in base.footprint(it, it.cell) }
    if (center && target != null && item is HomeItem.App) {
        if (target is HomeItem.App && target.key != item.key) {
            val folder = HomeItem.Folder(newId(), tr("مجلد", "Folder"), listOf(target.key, item.key), target.cell)
            return withPage(pageItems.map { if (it.ref == target.ref) folder else it })
        }
        if (target is HomeItem.Folder) {
            return withPage(pageItems.map {
                if (it.ref == target.ref) target.copy(keys = target.keys + item.key) else it
            })
        }
    }
    val spot = base.firstFree(pageItems, item, nearTo = c)
    return if (spot != null) withPage(pageItems + item.at(spot)) else base.addItem(item)
}

/** يسقط عنصر (من الشاشة أو الدوك) على خلية في الصفحة. */
fun AuraState.dropOnHome(ref: String, fromDock: Boolean, page: Int, cell: Int, center: Boolean): AuraState {
    val item: HomeItem
    val s: AuraState
    if (fromDock) {
        val key = ref.removePrefix("a:")
        if (key !in dock) return this
        item = HomeItem.App(key)
        s = copy(dock = dock - key)
    } else {
        item = pages.flatten().firstOrNull { it.ref == ref } ?: return this
        s = copy(pages = pages.map { p -> p.filterNot { it.ref == ref } })
    }
    return s.placeAt(item, page, cell, center).trim()
}

/** يسقط تطبيق على الدوك (أو يعيد ترتيبه جواه). */
fun AuraState.dropOnDock(ref: String, fromDock: Boolean, index: Int): AuraState {
    if (!ref.startsWith("a:")) return this
    val key = ref.removePrefix("a:")
    if (fromDock) {
        val list = dock.toMutableList()
        if (!list.remove(key)) return this
        list.add(index.coerceIn(0, list.size), key)
        return copy(dock = list)
    }
    if (key in dock || dock.size >= MAX_DOCK) return this
    val list = dock.toMutableList()
    list.add(index.coerceIn(0, list.size), key)
    return copy(dock = list).removeApp(key)
}

/** يعدّل ودجت Aura (حجم/شكل/نص) ويعيد تثبيتها قريب من مكانها لو الحجم الجديد مش داخل. */
fun AuraState.updateBuiltin(uid: String, f: (HomeItem.Builtin) -> HomeItem.Builtin): AuraState {
    var found: HomeItem.Builtin? = null
    var pageIdx = -1
    pages.forEachIndexed { pi, p ->
        p.forEach { if (it is HomeItem.Builtin && it.uid == uid) { found = it; pageIdx = pi } }
    }
    val old = found ?: return this
    val updated = f(old)
    val without = copy(pages = pages.map { p -> p.filterNot { it.ref == old.ref } })
    val page = without.pages[pageIdx]
    val ok = without.fits(updated, old.cell) &&
        without.footprint(updated, old.cell).none { it in without.occupied(page) }
    if (ok) {
        return without.copy(pages = without.pages.mapIndexed { i, p -> if (i == pageIdx) p + updated.at(old.cell) else p })
    }
    val spot = without.firstFree(page, updated, nearTo = old.cell)
    val res = if (spot != null) {
        without.copy(pages = without.pages.mapIndexed { i, p -> if (i == pageIdx) p + updated.at(spot) else p })
    } else {
        without.addItem(updated.at(-1))
    }
    return res.trim()
}

/** التطبيقات المخفية بسبب وضع التركيز في الساعة دي. */
fun AuraState.focusHiddenKeys(hour: Int): Set<String> {
    if (focusApps.isEmpty()) return emptySet()
    val inWin = if (focusFrom <= focusTo) hour in focusFrom until focusTo else hour >= focusFrom || hour < focusTo
    return if (focusOn || (focusAuto && inWin)) focusApps else emptySet()
}

fun AuraState.isNight(hour: Int): Boolean =
    if (nightHour > dayHour) hour >= nightHour || hour < dayHour else hour in nightHour until dayHour

fun AuraState.builtinByUid(uid: String): HomeItem.Builtin? =
    pages.flatten().filterIsInstance<HomeItem.Builtin>().firstOrNull { it.uid == uid }

/** يسقط تطبيق جاي من الدرج على خلية في الصفحة. */
fun AuraState.dropNewApp(key: String, page: Int, cell: Int, center: Boolean): AuraState =
    removeApp(key).placeAt(HomeItem.App(key), page, cell, center).trim()

// ───────────────────────── JSON ─────────────────────────

private fun itemToJson(item: HomeItem): JSONObject = when (item) {
    is HomeItem.App -> JSONObject().put("t", "a").put("k", item.key)
    is HomeItem.Folder -> JSONObject().put("t", "f").put("id", item.id)
        .put("n", item.name).put("ks", JSONArray(item.keys)).put("big", item.big)
    is HomeItem.Widget -> JSONObject().put("t", "w").put("id", item.widgetId)
    is HomeItem.Builtin -> JSONObject().put("t", "b").put("kind", item.kind).put("uid", item.uid)
        .put("cols", item.cols).put("rows", item.rows).put("bg", item.bg)
        .put("rad", item.radius).put("data", item.data)
}.put("c", item.cell)

internal fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }

private fun itemFromJson(o: JSONObject): HomeItem? {
    val cell = o.optInt("c", -1)
    return when (o.optString("t")) {
        "a" -> HomeItem.App(o.getString("k"), cell)
        "f" -> HomeItem.Folder(o.getString("id"), o.optString("n"), o.optJSONArray("ks").toStringList(), cell, o.optBoolean("big", false))
        "w" -> HomeItem.Widget(o.getInt("id"), cell)
        "b" -> HomeItem.Builtin(
            o.optString("kind", "clock"), o.getString("uid"), cell,
            cols = o.optInt("cols", 0), rows = o.optInt("rows", 0), bg = o.optInt("bg", 0),
            radius = o.optInt("rad", 24), data = o.optString("data", ""),
        )
        else -> null
    }
}

fun AuraState.toJson(forBackup: Boolean = false): JSONObject {
    val o = JSONObject()
    o.put("v", 2)
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
    for ((k, v) in usage) u.put(k, JSONObject().put("c", v.count).put("l", v.last).put("s", JSONArray(v.slots)))
    o.put("usage", u)
    themeId?.let { o.put("themeId", it) }
    homeText?.let { o.put("homeText", it) }
    o.put("clock", clock.name)
    o.put("menuStyle", menuStyle.name)
    o.put("drawerCols", drawerColumns)
    o.put("themeIcons", themeIcons)
    o.put("badgePrompted", badgePrompted)
    o.put("focusOn", focusOn)
    o.put("focusApps", JSONArray(focusApps.toList()))
    o.put("focusAuto", focusAuto)
    o.put("focusFrom", focusFrom)
    o.put("focusTo", focusTo)
    o.put("limits", JSONObject().also { j -> limits.forEach { (k, v) -> j.put(k, v) } })
    o.put("drawerSort", drawerSort)
    o.put("nightIcons", nightIcons)
    o.put("removed", JSONArray(removed))
    o.put("swipeDown", swipeDown.name)
    o.put("lang", lang)
    o.put("liveIcons", liveIcons)
    o.put("onboarded", onboarded)
    o.put("searchHistory", JSONArray(searchHistory))
    o.put("appNames", JSONObject(appNames))
    o.put("customIcons", JSONArray(customIcons.toList()))
    o.put("webSuggest", webSuggest)
    o.put("dynamicColor", dynamicColor)
    o.put("drawerAlpha", drawerAlpha)
    o.put("dockAlpha", dockAlpha)
    o.put("blurOn", blurOn)
    o.put("edgeOn", edgeOn)
    o.put("edgeApps", JSONArray(edgeApps))
    o.put("rules", JSONArray(rules))
    o.put("lockedPages", JSONArray(lockedPages.toList()))
    o.put("iconShape", iconShape)
    o.put("relaxOn", relaxOn)
    o.put("relaxHour", relaxHour)
    o.put("feedOn", feedOn)
    o.put("feedUrl", feedUrl)
    o.put("tasks", JSONArray(tasks))
    o.put("oneHandLeft", oneHandLeft)
    o.put("dockSwipe", dockSwipe)
    o.put("dockFolders", JSONArray(dockFolders.values.map { itemToJson(it) }))
    o.put("doubleTap", doubleTap.name)
    o.put("badges", badges)
    o.put("schedOn", scheduleOn)
    dayTheme?.let { o.put("dayTheme", it) }
    nightTheme?.let { o.put("nightTheme", it) }
    o.put("dayHour", dayHour)
    o.put("nightHour", nightHour)
    o.put(
        "icon",
        JSONObject().put("shape", iconStyle.shape.name).put("plate", iconStyle.plate)
            .put("mono", iconStyle.mono).put("tint", iconStyle.tint),
    )
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
            UsageStat(e.optInt("c"), e.optLong("l"), e.optJSONArray("s")?.let { a -> List(a.length()) { i -> a.optInt(i) } } ?: emptyList())
        }
    } ?: emptyMap()
    val icon = o.optJSONObject("icon")?.let { j ->
        IconStyle(
            shape = runCatching { IconShape.valueOf(j.optString("shape", "ORIGINAL")) }
                .getOrDefault(IconShape.ORIGINAL),
            plate = j.optLong("plate", 0L),
            mono = j.optBoolean("mono", false),
            tint = j.optLong("tint", 0xFFFFFFFF),
        )
    } ?: IconStyle()
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
        themeId = if (o.has("themeId")) o.getString("themeId") else null,
        iconStyle = icon,
        clock = runCatching { ClockStyle.valueOf(o.optString("clock", "NONE")) }.getOrDefault(ClockStyle.NONE),
        homeText = if (o.has("homeText")) o.getLong("homeText") else null,
        menuStyle = runCatching { MenuStyle.valueOf(o.optString("menuStyle", "IOS")) }.getOrDefault(MenuStyle.IOS),
        drawerColumns = o.optInt("drawerCols", d.drawerColumns).coerceIn(3, 6),
        themeIcons = o.optBoolean("themeIcons", true),
        badgePrompted = o.optBoolean("badgePrompted", false),
        focusOn = o.optBoolean("focusOn", false),
        focusApps = o.optJSONArray("focusApps").toStringList().toSet(),
        focusAuto = o.optBoolean("focusAuto", false),
        focusFrom = o.optInt("focusFrom", 9).coerceIn(0, 23),
        focusTo = o.optInt("focusTo", 17).coerceIn(0, 23),
        limits = o.optJSONObject("limits")?.let { j -> j.keys().asSequence().associateWith { j.optInt(it, 60) } } ?: emptyMap(),
        drawerSort = o.optInt("drawerSort", 0).coerceIn(0, 2),
        nightIcons = o.optBoolean("nightIcons", false),
        removed = o.optJSONArray("removed").toStringList(),
        liveIcons = o.optBoolean("liveIcons", true),
        onboarded = o.optBoolean("onboarded", false),
        searchHistory = o.optJSONArray("searchHistory").toStringList(),
        appNames = o.optJSONObject("appNames")?.let { n -> n.keys().asSequence().associateWith { k -> n.optString(k) } } ?: emptyMap(),
        customIcons = o.optJSONArray("customIcons").toStringList().toSet(),
        webSuggest = o.optBoolean("webSuggest", false),
        dynamicColor = o.optBoolean("dynamicColor", false),
        drawerAlpha = o.optInt("drawerAlpha", 92).coerceIn(40, 100),
        dockAlpha = o.optInt("dockAlpha", 100).coerceIn(0, 100),
        blurOn = o.optBoolean("blurOn", false),
        edgeOn = o.optBoolean("edgeOn", true),
        edgeApps = o.optJSONArray("edgeApps").toStringList(),
        rules = o.optJSONArray("rules").toStringList(),
        lockedPages = o.optJSONArray("lockedPages")?.let { a -> List(a.length()) { i -> a.optInt(i) }.toSet() } ?: emptySet(),
        lang = o.optString("lang", "ar").let { if (it in listOf("ar", "en", "auto")) it else "ar" },
        iconShape = o.optInt("iconShape", 0).coerceIn(0, 3),
        relaxOn = o.optBoolean("relaxOn", false),
        relaxHour = o.optInt("relaxHour", 23).coerceIn(0, 23),
        feedOn = o.optBoolean("feedOn", true),
        feedUrl = o.optString("feedUrl", ""),
        tasks = o.optJSONArray("tasks").toStringList(),
        oneHandLeft = o.optBoolean("oneHandLeft", false),
        dockSwipe = o.optBoolean("dockSwipe", true),
        dockFolders = o.optJSONArray("dockFolders")?.let { a ->
            (0 until a.length()).mapNotNull { i -> itemFromJson(a.getJSONObject(i)) as? HomeItem.Folder }.associateBy { it.id }
        } ?: emptyMap(),
        swipeDown = runCatching { GestureAction.valueOf(o.optString("swipeDown", "SEARCH")) }
            .getOrDefault(GestureAction.SEARCH),
        doubleTap = runCatching { GestureAction.valueOf(o.optString("doubleTap", "NONE")) }
            .getOrDefault(GestureAction.NONE),
        badges = o.optBoolean("badges", false),
        scheduleOn = o.optBoolean("schedOn", false),
        dayTheme = if (o.has("dayTheme")) o.getString("dayTheme") else null,
        nightTheme = if (o.has("nightTheme")) o.getString("nightTheme") else null,
        dayHour = o.optInt("dayHour", d.dayHour).coerceIn(0, 23),
        nightHour = o.optInt("nightHour", d.nightHour).coerceIn(0, 23),
    ).normalizeCells()
}


// ───────────────────────── مجلدات وأزواج ─────────────────────────

fun AuraState.setFolderBig(id: String, big: Boolean): AuraState {
    val f = pages.flatten().filterIsInstance<HomeItem.Folder>().firstOrNull { it.id == id } ?: return this
    val nf = f.copy(big = big, cell = -1)
    // نشيله ونعيد وضعه في أول مكان يسع حجمه الجديد
    return removeByRef(f.ref).addItem(nf).trim()
}

fun AuraState.addPair(a: String, b: String, name: String): AuraState =
    addItem(HomeItem.Folder("pair-" + newId(), name, listOf(a, b)))

/** مجلد في الدوك: بيتخزن كمفتاح "f:<id>" ويتشال من الصفحات. */
fun AuraState.folderToDock(id: String): AuraState {
    val f = pages.flatten().filterIsInstance<HomeItem.Folder>().firstOrNull { it.id == id } ?: return this
    if (dock.size >= MAX_DOCK) return this
    return removeByRef(f.ref).copy(dock = dock + "f:$id", dockFolders = dockFolders + (id to f.copy(big = false, cell = -1)))
}
