package com.aura.launcher

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Drawer(ui: Ui, state: AuraState, apps: List<AppEntry>, pal: AuraPalette) {
    val context = LocalContext.current
    val insets = WindowInsets.systemBars.asPaddingValues()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val threshold = with(density) { 110.dp.toPx() }

    // سحب لتحت وإحنا في أول القائمة = نرجع للشاشة الرئيسية
    val pullToClose = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y > 0f) {
                        pulled += available.y
                        if (pulled > threshold) {
                            pulled = 0f
                            focusManager.clearFocus()
                            ui.drawerOpen = false
                        }
                    } else if (consumed.y != 0f) {
                        pulled = 0f
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(Unit) {
        if (ui.focusSearch) {
            delay(250)
            runCatching { focus.requestFocus() }
        }
    }

    val visible = remember(apps, state.hidden) { apps.filter { it.key !in state.hidden } }
    val q = normalizeForSearch(query)
    val results = remember(visible, q) {
        if (q.isEmpty()) visible
        else visible.filter { it.normLabel.contains(q) || it.packageName.lowercase().contains(q) }
    }
    val suggestions = remember(visible, state.usage) {
        visible.filter { (state.usage[it.key]?.count ?: 0) > 0 }
            .sortedByDescending { state.usage[it.key]?.count ?: 0 }
            .take(state.columns)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.drawerBg)
            .padding(top = insets.calculateTopPadding())
            .imePadding()
            .nestedScroll(pullToClose),
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text(tr("ابحث في التطبيقات…", "Search apps…")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                results.firstOrNull()?.let { ui.drawerOpen = false; ui.launch(it) }
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .focusRequester(focus),
        )

        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(state.columns),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 8.dp, end = 8.dp, top = 4.dp, bottom = insets.calculateBottomPadding() + 12.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (q.isEmpty() && suggestions.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            tr("الأكثر استخداماً", "Most used"),
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            tr("بحث في الويب عن «$query»", "Search the web for “$query”"),
                            color = pal.onPanel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)),
                                            )
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
