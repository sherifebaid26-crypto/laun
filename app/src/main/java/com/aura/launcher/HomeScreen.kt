package com.aura.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun itemKey(item: HomeItem): String = when (item) {
    is HomeItem.App -> "a:" + item.key
    is HomeItem.Folder -> "f:" + item.id
    is HomeItem.Widget -> "w:" + item.widgetId
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    ui: Ui,
    app: AppEntry,
    where: Where,
    state: AuraState,
    textColor: Color,
    shadow: Color,
    modifier: Modifier = Modifier,
    showLabel: Boolean = state.showLabels,
    folder: String? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = { ui.launch(app) }, onLongClick = { menu = true })
                .padding(vertical = 6.dp),
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size((56 * state.iconScale).dp),
            )
            if (showLabel) {
                Text(
                    text = if (app.key in state.locked) app.label + " 🔒" else app.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        color = textColor,
                        fontSize = 12.sp,
                        shadow = Shadow(color = shadow, blurRadius = 4f),
                    ),
                    modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            ui.menuFor(app, where, folder).forEach { e ->
                DropdownMenuItem(text = { Text(e.label) }, onClick = { menu = false; e.onClick() })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCell(
    ui: Ui,
    folder: HomeItem.Folder,
    apps: Map<String, AppEntry>,
    state: AuraState,
    pal: AuraPalette,
) {
    var menu by remember { mutableStateOf(false) }
    val size = (56 * state.iconScale).dp
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = { ui.folderId = folder.id }, onLongClick = { menu = true })
                .padding(vertical = 6.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(16.dp))
                    .background(pal.panel)
                    .border(1.dp, pal.border, RoundedCornerShape(16.dp)),
            ) {
                folder.keys.mapNotNull { apps[it] }.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { Image(it.icon, null, Modifier.size(size * 0.38f)) }
                    }
                }
            }
            if (state.showLabels) {
                Text(
                    text = folder.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        color = pal.homeText,
                        fontSize = 12.sp,
                        shadow = Shadow(color = pal.homeShadow, blurRadius = 4f),
                    ),
                    modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(tr("إعادة تسمية", "Rename")) },
                onClick = {
                    menu = false
                    ui.textInput = TextRequest(tr("اسم المجلد", "Folder name"), folder.name) { n ->
                        ui.vm.update { it.renameFolder(folder.id, n.ifBlank { folder.name }) }
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(tr("حذف المجلد", "Delete folder")) },
                onClick = { menu = false; ui.vm.update { it.deleteFolder(folder.id) } },
            )
        }
    }
}

@Composable
private fun WidgetCell(ui: Ui, widgetId: Int, height: androidx.compose.ui.unit.Dp, pal: AuraPalette) {
    Box(Modifier.fillMaxWidth().height(height).padding(4.dp).clip(RoundedCornerShape(20.dp))) {
        WidgetView(ui.actions.widgetHost, widgetId, Modifier.fillMaxSize())
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(pal.panel)
                .clickable {
                    ui.actions.removeWidget(widgetId)
                    ui.vm.update { it.removeWidget(widgetId) }
                },
        ) { Text("✕", color = pal.onPanel, fontSize = 12.sp) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeLayer(ui: Ui, state: AuraState, apps: Map<String, AppEntry>, pal: AuraPalette) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    val pagerState = rememberPagerState(pageCount = { state.pages.size })
    val homeTick by ui.vm.homeTick.collectAsState()

    LaunchedEffect(homeTick) {
        if (homeTick > 0) {
            ui.closeAll()
            pagerState.animateScrollToPage(0)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(state.gestures) {
                    if (state.gestures) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total < -140f) ui.openDrawer(false)
                                else if (total > 140f) ui.openDrawer(true)
                            },
                            onDragCancel = {},
                            onVerticalDrag = { _, dy -> total += dy },
                        )
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onLongPress = { ui.homeMenu = true }) },
        ) {
            val cellH = maxHeight / state.rows

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                val page = state.pages.getOrNull(pageIndex) ?: emptyList()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.columns),
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                ) {
                    items(
                        page,
                        key = { itemKey(it) },
                        span = { item -> GridItemSpan(if (item is HomeItem.Widget) maxLineSpan else 1) },
                    ) { item ->
                        when (item) {
                            is HomeItem.App -> apps[item.key]?.let {
                                AppCell(
                                    ui, it, Where.HOME, state, pal.homeText, pal.homeShadow,
                                    Modifier.height(cellH),
                                )
                            }
                            is HomeItem.Folder -> Box(Modifier.height(cellH)) {
                                FolderCell(ui, item, apps, state, pal)
                            }
                            is HomeItem.Widget -> WidgetCell(ui, item.widgetId, cellH * 2, pal)
                        }
                    }
                }
            }

            if (state.pages.all { it.isEmpty() }) {
                Text(
                    tr(
                        "اسحب لأعلى لفتح التطبيقات\nاضغط مطولاً هنا للإعدادات والودجتس",
                        "Swipe up for all apps\nLong-press here for settings & widgets",
                    ),
                    color = pal.homeText.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    style = TextStyle(shadow = Shadow(color = pal.homeShadow, blurRadius = 4f)),
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }

            Box(Modifier.align(Alignment.Center)) {
                DropdownMenu(expanded = ui.homeMenu, onDismissRequest = { ui.homeMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("إضافة ودجت", "Add widget")) },
                        onClick = { ui.homeMenu = false; ui.widgetPicker = true },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("مجلد جديد", "New folder")) },
                        onClick = { ui.homeMenu = false; ui.newFolder() },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("الخلفية", "Wallpaper")) },
                        onClick = { ui.homeMenu = false; ui.actions.openWallpaperPicker() },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("الإعدادات", "Settings")) },
                        onClick = { ui.homeMenu = false; ui.settingsOpen = true },
                    )
                }
            }
        }

        if (state.pages.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(state.pages.size) { i ->
                    val current = i == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(3.dp)
                            .size(if (current) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (current) pal.homeText else pal.homeText.copy(alpha = 0.4f))
                    )
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().height(26.dp).clickable { ui.openDrawer(false) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(pal.homeText.copy(alpha = 0.6f))
            )
        }

        val shape = RoundedCornerShape(30.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(shape)
                .background(pal.panel)
                .border(1.dp, pal.border, shape)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.dock.mapNotNull { apps[it] }.take(MAX_DOCK).forEach { app ->
                AppCell(
                    ui, app, Where.DOCK, state, pal.onPanel, Color.Transparent,
                    Modifier.weight(1f), showLabel = false,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(68.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { ui.openDrawer(false) },
            ) {
                Text("⋮⋮⋮", color = pal.onPanel, fontSize = 20.sp)
            }
        }
    }
}
