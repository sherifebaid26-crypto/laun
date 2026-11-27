package com.aura.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.zIndex
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class Cat(val ar: String, val en: String) {
    ALL("الكل", "All"),
    GAMES("ألعاب", "Games"),
    SOCIAL("تواصل", "Social"),
    MEDIA("وسائط", "Media"),
    WORK("إنتاجية", "Productivity"),
    MAPS("خرائط", "Maps"),
    OTHER("أخرى", "Other"),
}

private fun catOf(c: Int): Cat = when (c) {
    ApplicationInfo.CATEGORY_GAME -> Cat.GAMES
    ApplicationInfo.CATEGORY_SOCIAL -> Cat.SOCIAL
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> Cat.MEDIA
    ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_NEWS -> Cat.WORK
    ApplicationInfo.CATEGORY_MAPS -> Cat.MAPS
    else -> Cat.OTHER
}

/** حاسبة صغيرة للبحث: + - * / وأقواس. */
private class ExprParser(private val s: String) {
    private var i = 0
    private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

    fun parse(): Double? {
        val v = expr() ?: return null
        return if (i == s.length && v.isFinite()) v else null
    }

    private fun expr(): Double? {
        var v = term() ?: return null
        while (peek() == '+' || peek() == '-') {
            val op = peek(); i++
            val r = term() ?: return null
            v = if (op == '+') v + r else v - r
        }
        return v
    }

    private fun term(): Double? {
        var v = factor() ?: return null
        while (peek() == '*' || peek() == '/') {
            val op = peek(); i++
            val r = factor() ?: return null
            v = if (op == '*') v * r else v / r
        }
        return v
    }

    private fun factor(): Double? {
        if (peek() == '-') { i++; return factor()?.let { -it } }
        if (peek() == '+') { i++; return factor() }
        if (peek() == '(') {
            i++
            val v = expr() ?: return null
            if (peek() != ')') return null
            i++
            return v
        }
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        return s.substring(st, i).toDoubleOrNull()
    }
}

private fun calc(query: String): String? {
    if (query.length > 100) return null   // المحلل تكراري: نمنع الأقواس/الإشارات الطويلة (StackOverflow)
    val q = buildString {
        for (c in query.trim()) {
            append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    '×', 'x', 'X' -> '*'
                    '÷' -> '/'
                    else -> c
                },
            )
        }
    }.replace(" ", "")
    if (!Regex("[0-9+\\-*/().]+").matches(q)) return null
    if (q.none { it.isDigit() } || q.none { it in "+-*/" } || q.first() == '*' || q.first() == '/') return null
    val v = ExprParser(q).parse() ?: return null
    return if (v == Math.rint(v) && abs(v) < 1e15) v.toLong().toString() else String.format(java.util.Locale.US, "%.8g", v).trimEnd('0').trimEnd('.')
}

private fun queryContacts(ctx: Context, q: String): List<Pair<String, String>> = runCatching {
    val out = LinkedHashMap<String, String>()
    ctx.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
        arrayOf("%$q%"),
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
    )?.use { c ->
        while (c.moveToNext() && out.size < 5) {
            val name = c.getString(0) ?: continue
            out.putIfAbsent(name, c.getString(1) ?: "")
        }
    }
    out.map { it.key to it.value }
}.getOrDefault(emptyList())

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Drawer(ui: Ui, state: AuraState, apps: List<AppEntry>, pal: AuraPalette) {
    val context = LocalContext.current
    val insets = WindowInsets.systemBars.asPaddingValues()
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(Cat.ALL) }
    var hasContacts by remember { mutableStateOf(ui.actions.hasContactsPermission()) }
    val focus = remember { FocusRequester() }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val threshold = with(density) { 110.dp.toPx() }
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        if (ui.focusSearch) {
            delay(250)
            runCatching { focus.requestFocus() }
        }
    }

    val focusHidden = state.focusHiddenKeys(ui.hour)
    val visible = remember(apps, state.hidden, focusHidden, state.drawerSort, state.usage) {
        val base = apps.filter { it.key !in state.hidden && it.key !in focusHidden }
        when (state.drawerSort) {
            1 -> base.sortedWith(
                compareByDescending<AppEntry> { state.usage[it.key]?.count ?: 0 }.thenBy { it.normLabel },
            )
            2 -> base.sortedByDescending { it.installed }
            else -> base
        }
    }
    val cats = remember(visible) {
        listOf(Cat.ALL) + Cat.values().filter { c -> c != Cat.ALL && visible.any { catOf(it.category) == c } }
    }
    val q = normalizeForSearch(query)
    val results = remember(visible, q, cat) {
        val base = if (cat == Cat.ALL) visible else visible.filter { catOf(it.category) == cat }
        if (q.isEmpty()) base
        else base.filter { it.normLabel.contains(q) || it.packageName.lowercase().contains(q) }
    }
    val suggestions = remember(visible, state.usage, ui.hour, ui.headphones) {
        smartSuggestions(state, visible, ui.hour, ui.headphones, state.drawerColumns)
    }
    val sysMatches = remember(q) { matchSettings(q) }
    val calcResult = remember(query) { calc(query) }
    androidx.compose.runtime.LaunchedEffect(query) { ui.searchText = query }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ui.searchText = "" } }
    val webSugs by produceState(emptyList<String>(), query, state.webSuggest) {
        val t = query.trim()
        value = if (!state.webSuggest || t.length < 2) emptyList() else {
            delay(350)
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val arr = org.json.JSONArray(
                        httpText("https://suggestqueries.google.com/complete/search?client=firefox&hl=" + (if (Lang.isAr) "ar" else "en") + "&q=" + Uri.encode(t)),
                    ).getJSONArray(1)
                    List(minOf(4, arr.length())) { arr.getString(it) }
                }.getOrDefault(emptyList())
            }
        }
    }
    val contacts by produceState(emptyList<Pair<String, String>>(), query, hasContacts) {
        val t = query.trim()
        value = if (t.length >= 2 && hasContacts) {
            delay(200)
            withContext(Dispatchers.IO) { queryContacts(context, t) }
        } else emptyList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.drawerBg.copy(alpha = pal.drawerBg.alpha * state.drawerAlpha / 100f))
            .padding(top = insets.calculateTopPadding())
            .imePadding()
            // سحب لتحت من أي مكان وإحنا في أول القائمة = نرجع للشاشة الرئيسية
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (gridState.canScrollBackward) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (ch.changedToUp() || !ch.pressed) break
                        if (ui.extDrag != null) break
                        val dx = ch.position.x - down.position.x
                        val dy = ch.position.y - down.position.y
                        if (dy < -slop) break
                        if (dy > threshold && dy > abs(dx) * 1.5f) {
                            focusManager.clearFocus()
                            ui.drawerOpen = false
                            break
                        }
                    }
                }
            },
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(pal.onPanel.copy(alpha = 0.35f)),
            )
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text(tr("ابحث في التطبيقات…", "Search apps…")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                results.firstOrNull()?.let { ui.launch(it) }
            }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = pal.field,
                unfocusedContainerColor = pal.field,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = pal.onPanel,
                unfocusedTextColor = pal.onPanel,
                cursorColor = pal.accent,
                focusedPlaceholderColor = pal.onPanel.copy(alpha = 0.55f),
                unfocusedPlaceholderColor = pal.onPanel.copy(alpha = 0.55f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focus),
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (cats.size > 2) {
                    items(cats) { c ->
                        val on = c == cat
                        Text(
                            tr(c.ar, c.en),
                            color = if (on) Color.Black else pal.onPanel,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (on) pal.accent else pal.field)
                                .clickable { cat = c }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Text(
                "⇅ " + when (state.drawerSort) {
                    1 -> tr("الأكثر استخداماً", "Most used")
                    2 -> tr("الأحدث", "Newest")
                    else -> tr("أ–ي", "A–Z")
                },
                color = pal.accent, fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { ui.vm.update { it.copy(drawerSort = (it.drawerSort + 1) % 3) } }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }

        Box(Modifier.weight(1f)) {
            val land = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val showAlpha = q.isEmpty() && cat == Cat.ALL && state.drawerSort == 0 && results.size > 24
            if (showAlpha) {
                val letters = remember(results) {
                    results.mapNotNull { it.normLabel.firstOrNull()?.uppercaseChar() }.distinct()
                }
                val header = (if (calcResult != null) 1 else 0) +
                    (if (state.searchHistory.isNotEmpty()) 1 else 0) +
                    (if (suggestions.isNotEmpty()) suggestions.size + 2 else 0)
                AlphaScroller(
                    letters, pal.accent,
                    Modifier.align(androidx.compose.ui.Alignment.CenterEnd).zIndex(2f),
                ) { ch ->
                    val i = results.indexOfFirst { it.normLabel.firstOrNull()?.uppercaseChar() == ch }
                    if (i >= 0) scope.launch { gridState.scrollToItem(header + i) }
                }
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(state.drawerColumns * (if (land) 2 else 1)),
                contentPadding = PaddingValues(
                    start = 8.dp, end = 8.dp, top = 4.dp, bottom = insets.calculateBottomPadding() + 12.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (calcResult != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "＝ $calcResult   " + tr("(اضغط للنسخ)", "(tap to copy)"),
                            color = pal.accent, fontSize = 20.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    val cm = context.getSystemService(ClipboardManager::class.java)
                                    cm?.setPrimaryClip(ClipData.newPlainText("result", calcResult))
                                }
                                .padding(14.dp),
                        )
                    }
                }
                if (q.isEmpty() && state.searchHistory.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        androidx.compose.foundation.layout.Column {
                            Text(tr("آخر ما بحثت عنه", "Recent searches"), color = pal.accent, fontSize = 13.sp,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.searchHistory) { h ->
                                    Text(h, color = pal.onPanel, fontSize = 13.sp,
                                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(pal.field)
                                            .clickable { query = h }.padding(horizontal = 14.dp, vertical = 7.dp))
                                }
                                item {
                                    Text("✕ " + tr("مسح", "Clear"), color = pal.accent, fontSize = 13.sp,
                                        modifier = Modifier.clip(RoundedCornerShape(18.dp))
                                            .clickable { ui.vm.update { it.copy(searchHistory = emptyList()) } }
                                            .padding(horizontal = 12.dp, vertical = 7.dp))
                                }
                            }
                        }
                    }
                }
                if (q.isEmpty() && cat == Cat.ALL && suggestions.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (ui.headphones) tr("مقترح (سماعة موصّلة 🎧)", "Suggested (headphones 🎧)") else tr("مقترح لك الآن", "Suggested now"),
                            color = pal.accent,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp),
                        )
                    }
                    items(suggestions, key = { "s:" + it.key }) { app ->
                        DrawerCell(ui, app, state, pal)
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            tr("كل التطبيقات", "All apps"),
                            color = pal.accent,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
                        )
                    }
                }
                items(results, key = { it.key }) { app -> DrawerCell(ui, app, state, pal) }

                if (q.isNotEmpty()) {
                    items(sysMatches, key = { "set:" + it.action }, span = { GridItemSpan(maxLineSpan) }) { st ->
                        Text(
                            "⚙ " + tr("إعدادات: ", "Settings: ") + tr(st.ar, st.en),
                            color = pal.onPanel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { ui.actions.openSystemSetting(st.action) }
                                .padding(16.dp),
                        )
                    }
                    if (query.trim().length >= 2 && !hasContacts) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                tr("👤 اسمح بالبحث في جهات الاتصال", "👤 Allow searching contacts"),
                                color = pal.accent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        ui.actions.requestContacts { hasContacts = ui.actions.hasContactsPermission() }
                                    }
                                    .padding(16.dp),
                            )
                        }
                    }
                    items(contacts, key = { "c:" + it.first }, span = { GridItemSpan(maxLineSpan) }) { (name, number) ->
                        Text(
                            "👤 $name   $number",
                            color = pal.onPanel,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                                .padding(16.dp),
                        )
                    }
                    items(webSugs, key = { "ws:" + it }, span = { GridItemSpan(maxLineSpan) }) { sg ->
                        Text(
                            "🔎 $sg", color = pal.onPanel,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    ui.vm.addHistory(sg)
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(sg)))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }.padding(16.dp),
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            tr("بحث في الويب عن «$query»", "Search the web for “$query”"),
                            color = pal.onPanel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ui.vm.addHistory(query)
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)),
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerCell(ui: Ui, app: AppEntry, state: AuraState, pal: AuraPalette) {
    AppCell(
        ui = ui,
        app = app,
        where = Where.DRAWER,
        state = state,
        textColor = pal.onPanel,
        shadow = Color.Transparent,
    )
}
