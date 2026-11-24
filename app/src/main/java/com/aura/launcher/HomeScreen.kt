package com.aura.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class DragState(
    val ref: String,
    val fromDock: Boolean,
    val pos: Offset,
    val moved: Boolean,
    val icon: ImageBitmap?,
    val wCells: Int,
    val hCells: Int,
)

private data class GridHit(val cell: Int, val center: Boolean)

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
    interactive: Boolean = true,
) {
    var rect by remember { mutableStateOf(Rect.Zero) }
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.9f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "press",
    )
    val night = state.nightIcons && state.isNight(ui.hour)
    val counts by Badges.counts.collectAsState()
    val badge = if (state.badges) counts[app.packageName] ?: 0 else 0

    val click = when {
        where == Where.DRAWER -> Modifier.clickable(interactionSource = src, indication = null) { ui.launch(app, rect) }
        interactive -> Modifier.combinedClickable(
            interactionSource = src,
            indication = null,
            onClick = { ui.launch(app, rect) },
            onLongClick = { ui.showMenu(rect, ui.menuFor(app, where, folder)) },
        )
        else -> Modifier.clickable(interactionSource = src, indication = null) { ui.launch(app, rect) }
    }

    // من الدرج: ضغطة مطوّلة ثم سحب = نقل التطبيق للشاشة الرئيسية، وبدون سحب = القائمة
    val dragOut = if (where == Where.DRAWER) {
        Modifier.pointerInput(app.key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slop = viewConfiguration.touchSlop
                val notLong = withTimeoutOrNull(400L) {
                    var abort = false
                    while (!abort) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch == null || ch.changedToUp() || ch.isConsumed ||
                            (ch.position - down.position).getDistance() > slop
                        ) abort = true
                    }
                    true
                }
                if (notLong != null) return@awaitEachGesture

                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                var moved = false
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    ch.consume()
                    if (ch.changedToUp() || !ch.pressed) break
                    if (!moved && (ch.position - down.position).getDistance() > slop) moved = true
                    if (moved) ui.extDrag = ExtDrag(app, ch.position + rect.topLeft)
                }
                val d = ui.extDrag
                if (moved && d != null) {
                    ui.finishExtDrag(d)
                } else {
                    ui.extDrag = null
                    ui.showMenu(rect, ui.menuFor(app, where, folder))
                }
            }
        }
    } else Modifier

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { rect = it.boundsInWindow() }
            .clip(RoundedCornerShape(16.dp))
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .then(dragOut)
            .then(click)
            .padding(vertical = 6.dp),
    ) {
        Box {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size((56 * state.iconScale).dp),
                colorFilter = if (night) ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.82f, 0.78f, 0.7f, 1f) }) else null,
            )
            if (state.liveIcons) LiveIconOverlay(app, (56 * state.iconScale).dp)
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (badge > 99) "99+" else badge.toString(),
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
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
}

@Composable
private fun FolderCell(
    folder: HomeItem.Folder,
    apps: Map<String, AppEntry>,
    state: AuraState,
    pal: AuraPalette,
    ui: Ui,
    onClick: () -> Unit,
) {
    if (folder.big && !folder.isPair) {
        BigFolderCell(folder, apps, state, pal, ui, onClick)
        return
    }
    val size = (56 * state.iconScale).dp
    val st = state.iconStyle
    // شكل المجلد وخلفيته بيتبعوا شكل أيقونات الثيم
    val shape = when (st.shape) {
        IconShape.CIRCLE -> CircleShape
        IconShape.SQUIRCLE -> RoundedCornerShape(32)
        IconShape.TEARDROP -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 50, bottomStartPercent = 12)
        IconShape.ROUNDED -> RoundedCornerShape(22)
        else -> RoundedCornerShape(16.dp)
    }
    val plate = if (st.plate != 0L) Color(st.plate.toInt()).copy(alpha = 0.55f) else pal.panel
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(plate)
                .border(1.dp, pal.border, shape),
        ) {
            if (folder.isPair) {
                val pr = folder.keys.mapNotNull { apps[it] }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    pr.forEach { Image(it.icon, null, Modifier.size(size * 0.40f)) }
                }
                Text("◫", color = pal.homeText.copy(alpha = 0.8f), fontSize = 11.sp)
            } else {
                folder.keys.mapNotNull { apps[it] }.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { Image(it.icon, null, Modifier.size(size * 0.38f)) }
                    }
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
}

/** مجلد كبير 2×2: بيعرض ٤ تطبيقات تفتح بضغطة، والضغط على الاسم أو الخلفية يفتح المجلد. */
@Composable
private fun BigFolderCell(
    folder: HomeItem.Folder,
    apps: Map<String, AppEntry>,
    state: AuraState,
    pal: AuraPalette,
    ui: Ui,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val list = folder.keys.mapNotNull { apps[it] }
    Column(
        Modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(shape)
            .background(pal.panel)
            .border(1.dp, pal.border, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        list.take(4).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                row.forEach { a ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { ui.launch(a) }.padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) { Image(a.icon, null, Modifier.size((46 * state.iconScale).dp)) }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        Text(
            folder.name + if (list.size > 4) "  +${list.size - 4}" else "",
            color = pal.onPanel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WidgetCell(ui: Ui, widgetId: Int) {
    Box(Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(20.dp))) {
        WidgetView(ui.actions.widgetHost, widgetId, Modifier.fillMaxSize())
    }
}

/** مسكة تغيير حجم الودجت بالسحب: كل ما تعدّي خلية بيكبر/يصغر خطوة. اضغط عليها عشان تخلّص. */
@Composable
private fun BoxScope.ResizeHandle(uid: String, ui: Ui, cellW: Dp, cellH: Dp) {
    val density = LocalDensity.current
    val cw = with(density) { cellW.toPx() }
    val ch = with(density) { cellH.toPx() }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .align(AbsoluteAlignment.BottomRight)
            .padding(2.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFF00E5FF))
            .pointerInput(uid) { detectTapGestures(onTap = { ui.resizeUid = null }) }
            .pointerInput(uid, cw, ch) {
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dx += amount.x
                        dy += amount.y
                        var dc = 0
                        var dr = 0
                        while (dx >= cw) { dc++; dx -= cw }
                        while (dx <= -cw) { dc--; dx += cw }
                        while (dy >= ch) { dr++; dy -= ch }
                        while (dy <= -ch) { dr--; dy += ch }
                        if (dc != 0 || dr != 0) {
                            ui.vm.update { s ->
                                val cur = s.builtinByUid(uid) ?: return@update s
                                val nc = (s.colsOf(cur) + dc).coerceIn(1, s.columns)
                                val nr = (s.rowsOf(cur) + dr).coerceIn(1, minOf(4, s.rows))
                                s.updateBuiltin(uid) { it.copy(cols = if (nc >= s.columns) 0 else nc, rows = nr) }
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) { Text("⤡", color = Color.Black, fontSize = 20.sp) }
}

@Composable
private fun PageGrid(
    page: List<HomeItem>,
    ui: Ui,
    state: AuraState,
    apps: Map<String, AppEntry>,
    pal: AuraPalette,
    cellW: Dp,
    cellH: Dp,
    draggingRef: String?,
) {
    val focusHidden = state.focusHiddenKeys(ui.hour)
    Box(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        for (item in page) {
            key(item.ref) {
                if (item.cell >= 0 && !(item is HomeItem.App && item.key in focusHidden)) {
                    val col = item.cell % state.columns
                    val row = item.cell / state.columns
                    Box(
                        Modifier
                            .absoluteOffset(cellW * col, cellH * row)
                            .size(cellW * state.colsOf(item), cellH * state.rowsOf(item))
                            .alpha(if (item.ref == draggingRef) 0f else 1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (item) {
                            is HomeItem.App -> apps[item.key]?.let {
                                AppCell(ui, it, Where.HOME, state, pal.homeText, pal.homeShadow, interactive = false)
                            }
                            is HomeItem.Folder -> FolderCell(item, apps, state, pal, ui) {
                                if (item.isPair) ui.launchPair(item) else ui.folderId = item.id
                            }
                            is HomeItem.Widget -> WidgetCell(ui, item.widgetId)
                            is HomeItem.Builtin -> {
                                BuiltinWidget(item, state, apps, pal, ui, Modifier.fillMaxSize().padding(4.dp))
                                if (ui.resizeUid == item.uid) ResizeHandle(item.uid, ui, cellW, cellH)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeLayer(ui: Ui, state: AuraState, apps: Map<String, AppEntry>, pal: AuraPalette) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val homeTick by ui.vm.homeTick.collectAsState()

    val st = rememberUpdatedState(state)
    val appsRef = rememberUpdatedState(apps)

    var drag by remember { mutableStateOf<DragState?>(null) }
    var dragEdge by remember { mutableIntStateOf(0) }
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var gridWin by remember { mutableStateOf(Rect.Zero) }
    var dockWin by remember { mutableStateOf(Rect.Zero) }

    val ext = ui.extDrag
    val dragging = drag?.moved == true || ext != null
    val pagerState = rememberPagerState(pageCount = { state.pages.size + if (dragging) 1 else 0 })

    val padPx = with(density) { 8.dp.toPx() }
    val dockPadPx = with(density) { 6.dp.toPx() }
    val edgePx = with(density) { 36.dp.toPx() }

    LaunchedEffect(homeTick) {
        if (homeTick > 0) {
            ui.closeAll()
            pagerState.animateScrollToPage(0)
        }
    }

    // لما الإصبع يقعد عند حافة الشاشة أثناء السحب: نقلّب الصفحة
    val extEdge = if (ext == null) 0 else when {
        ext.pos.x < edgePx -> -1
        ext.pos.x > gridWin.right - edgePx -> 1
        else -> 0
    }
    val edgeNow = if (ext != null) extEdge else dragEdge
    LaunchedEffect(edgeNow) {
        if (edgeNow != 0) {
            while (true) {
                delay(650)
                val step = if (edgeNow > 0) (if (rtl) -1 else 1) else (if (rtl) 1 else -1)
                val target = pagerState.currentPage + step
                if (target in 0 until pagerState.pageCount) pagerState.animateScrollToPage(target)
            }
        }
    }

    // ───── هندسة الشبكة (إحداثيات النافذة) ─────
    fun cellW(): Float = (gridWin.width - 2 * padPx) / st.value.columns
    fun cellH(): Float = gridWin.height / st.value.rows

    fun cellRectWin(cell: Int, wC: Int, hC: Int): Rect {
        val s = st.value
        val col = cell % s.columns
        val row = cell / s.columns
        val left = gridWin.left + padPx + col * cellW()
        val top = gridWin.top + row * cellH()
        return Rect(left, top, left + cellW() * wC, top + cellH() * hC)
    }

    fun gridHit(w: Offset): GridHit? {
        if (!gridWin.contains(w)) return null
        val s = st.value
        val fx = (w.x - gridWin.left - padPx) / cellW()
        val fy = (w.y - gridWin.top) / cellH()
        val col = fx.toInt().coerceIn(0, s.columns - 1)
        val row = fy.toInt().coerceIn(0, s.rows - 1)
        val cx = fx - col
        val cy = fy - row
        return GridHit(row * s.columns + col, cx in 0.2f..0.8f && cy in 0.2f..0.8f)
    }

    fun dockCount(): Int = minOf(st.value.dock.size, MAX_DOCK)

    fun dockSlotW(): Float = (dockWin.width - 2 * dockPadPx) / (dockCount() + 1)

    fun dockIndexAt(w: Offset): Int {
        val idx = ((w.x - dockWin.left - dockPadPx) / dockSlotW()).toInt()
        return idx.coerceIn(0, dockCount())
    }

    fun dockSlotRect(i: Int): Rect {
        val left = dockWin.left + dockPadPx + i * dockSlotW()
        return Rect(left, dockWin.top, left + dockSlotW(), dockWin.bottom)
    }

    fun itemAt(page: Int, cell: Int): HomeItem? {
        val s = st.value
        return s.pages.getOrNull(page)?.firstOrNull { it.cell >= 0 && cell in s.footprint(it, it.cell) }
    }

    fun showItemMenu(d: DragState) {
        val s = st.value
        val ui2 = ui
        if (d.fromDock && d.ref.startsWith("f:")) {
            val f = s.dockFolders[d.ref.removePrefix("f:")] ?: return
            val idx = s.dock.indexOf(d.ref)
            ui2.showMenu(dockSlotRect(idx.coerceAtLeast(0)), ui2.folderEntries(f, inDock = true))
            return
        }
        if (d.fromDock) {
            val key = d.ref.removePrefix("a:")
            val app = appsRef.value[key] ?: return
            val idx = s.dock.indexOf(key)
            ui2.showMenu(dockSlotRect(idx.coerceAtLeast(0)), ui2.menuFor(app, Where.DOCK))
            return
        }
        val item = s.pages.flatten().firstOrNull { it.ref == d.ref } ?: return
        val rect = cellRectWin(item.cell, s.colsOf(item), s.rowsOf(item))
        val entries = when (item) {
            is HomeItem.App -> appsRef.value[item.key]?.let { ui2.menuFor(it, Where.HOME) } ?: emptyList()
            is HomeItem.Folder -> ui2.folderEntries(item)
            else -> ui2.widgetEntries(item)
        }
        ui2.showMenu(rect, entries)
    }

    val zoneH = with(density) { 84.dp.toPx() + insets.calculateTopPadding().toPx() }
    fun zoneAt(p: Offset): Int = if (p.y < zoneH) (if (p.x < gridWin.right / 2f) 1 else 2) else 0

    fun performDrop(d: DragState) {
        val z = zoneAt(d.pos)
        if (z != 0) {
            val isApp = d.ref.startsWith("a:")
            val key = d.ref.removePrefix("a:")
            if (z == 2 && isApp) {
                appsRef.value[key]?.let { ui.actions.uninstall(it) }
            } else if (isApp && d.fromDock) {
                ui.vm.update { it.dockRemove(key) }
            } else {
                ui.vm.update { it.removeRef(d.ref) }
            }
            return
        }
        val w = d.pos + rootPos
        if (dockWin.contains(w)) {
            val idx = dockIndexAt(w)
            ui.vm.update { it.dropOnDock(d.ref, d.fromDock, idx) }
            return
        }
        val g = gridHit(w) ?: return
        val page = pagerState.currentPage
        ui.vm.update { it.dropOnHome(d.ref, d.fromDock, page, g.cell, g.center) }
    }

    fun performExtDrop(d: ExtDrag): Boolean {
        val w = d.pos
        val key = d.app.key
        if (dockWin.contains(w)) {
            val s = st.value
            if (key in s.dock || s.dock.size >= MAX_DOCK) {
                ui.vm.postMessage(
                    if (key in s.dock) tr("التطبيق موجود في الدوك", "Already in the dock")
                    else tr("الدوك ممتلئ", "The dock is full"),
                )
                return false
            }
            val idx = dockIndexAt(w)
            ui.vm.update { it.dropOnDock("a:$key", false, idx) }
            return true
        }
        val g = gridHit(w) ?: return false
        val page = pagerState.currentPage
        ui.vm.update { it.dropNewApp(key, page, g.cell, g.center) }
        return true
    }

    DisposableEffect(Unit) {
        ui.dropHandler = { d -> performExtDrop(d) }
        onDispose { ui.dropHandler = null }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootPos = it.positionInWindow() }
            .pointerInput(Unit) {
                // Feed: اسحب لليمين من أقصى اليسار | اليد الواحدة: اسحب لتحت من الدوك
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (ui.overlayOpen) return@awaitEachGesture
                    val inDock = dockWin.contains(down.position + rootPos)
                    var fired = false
                    while (!fired) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed || drag != null) break
                        val dx = ch.position.x - down.position.x
                        val dy = ch.position.y - down.position.y
                        val s0 = st.value
                        if (inDock) {
                            if (s0.dockSwipe && dy > 90f && dy > kotlin.math.abs(dx) * 1.5f) { ui.oneHand = true; fired = true }
                        } else if (s0.feedOn && dx > 130f && dx > kotlin.math.abs(dy) * 2f) {
                            val edge = if (Lang.isAr) pagerState.pageCount - 1 else 0
                            if (pagerState.currentPage == edge) { ui.feedOpen = true; fired = true }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (ui.overlayOpen) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop

                    // ننتظر الضغطة المطوّلة (لو الإصبع اتحرك أو اتشال قبلها نسيب الإيماءات العادية)
                    val notLong = withTimeoutOrNull(400L) {
                        var abort = false
                        while (!abort) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id }
                            if (ch == null || ch.changedToUp() || ch.isConsumed ||
                                (ch.position - down.position).getDistance() > slop
                            ) abort = true
                        }
                        true
                    }
                    if (notLong != null) return@awaitEachGesture

                    val w0 = down.position + rootPos
                    val s = st.value

                    // مين تحت الإصبع؟
                    var ref: String? = null
                    var fromDock = false
                    var icon: ImageBitmap? = null
                    var wC = 1
                    var hC = 1
                    if (dockWin.contains(w0)) {
                        val idx = dockIndexAt(w0)
                        if (idx < dockCount()) {
                            val key = s.dock[idx]
                            ref = if (key.startsWith("f:")) key else "a:$key"
                            fromDock = true
                            icon = appsRef.value[key]?.icon
                        }
                    } else {
                        val g = gridHit(w0)
                        if (g == null) return@awaitEachGesture
                        val item = itemAt(pagerState.currentPage, g.cell)
                        if (item != null) {
                            ref = item.ref
                            wC = s.colsOf(item)
                            hC = s.rowsOf(item)
                            if (item is HomeItem.App) icon = appsRef.value[item.key]?.icon
                        }
                    }

                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    if (ref == null) {
                        // ضغطة مطوّلة على مكان فاضي = قائمة الشاشة الرئيسية
                        ui.showMenu(Rect(w0, w0), ui.homeEntries())
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            ch.consume()
                            if (ch.changedToUp() || !ch.pressed) break
                        }
                        return@awaitEachGesture
                    }

                    drag = DragState(ref, fromDock, down.position, false, icon, wC, hC)
                    var moved = false
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        ch.consume()
                        if (ch.changedToUp() || !ch.pressed) break
                        val p = ch.position
                        if (!moved && (p - down.position).getDistance() > slop) moved = true
                        drag = drag?.copy(pos = p, moved = moved)
                        val wx = p.x + rootPos.x
                        dragEdge = when {
                            !moved -> 0
                            wx < edgePx -> -1
                            wx > gridWin.right - edgePx -> 1
                            else -> 0
                        }
                    }
                    dragEdge = 0
                    val d = drag
                    drag = null
                    if (d != null) {
                        if (d.moved && !(d.fromDock && d.ref.startsWith("f:"))) performDrop(d) else showItemMenu(d)
                    }
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()),
        ) {
            ClockHeader(state.clock, pal.homeText, pal.homeShadow)

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { gridWin = it.boundsInWindow() }
                    .pointerInput(state.gestures) {
                        if (state.gestures) {
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (total < -140f) ui.openDrawer(false)
                                    else if (total > 140f) ui.perform(st.value.swipeDown)
                                },
                                onDragCancel = {},
                                onVerticalDrag = { _, dy -> total += dy },
                            )
                        }
                    }
                    .pointerInput(state.doubleTap) {
                        if (state.doubleTap != GestureAction.NONE) {
                            detectTapGestures(onDoubleTap = { ui.perform(st.value.doubleTap) })
                        }
                    },
            ) {
                val cw = (maxWidth - 16.dp) / state.columns
                val ch = maxHeight / state.rows

                androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) { ui.page = pagerState.currentPage }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                    val locked = pageIndex in state.lockedPages && pageIndex !in ui.unlockedPages
                    if (locked) {
                        Box(
                            Modifier.fillMaxSize().background(Color(0xF0101018))
                                .clickable { ui.requireAuth { ui.unlockedPages.add(pageIndex) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🔒  " + tr("صفحة مقفولة — اضغط للفتح", "Locked page — tap to unlock"), color = Color.White)
                        }
                    } else PageGrid(
                        page = state.pages.getOrNull(pageIndex) ?: emptyList(),
                        ui = ui, state = state, apps = apps, pal = pal,
                        cellW = cw, cellH = ch,
                        draggingRef = if (dragging) drag?.ref else null,
                    )
                }

                if (state.pages.all { it.isEmpty() }) {
                    Text(
                        tr(
                            "اسحب لأعلى لفتح التطبيقات\nاضغط مطولاً هنا للودجتس والإعدادات",
                            "Swipe up for all apps\nLong-press here for widgets & settings",
                        ),
                        color = pal.homeText.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        style = TextStyle(shadow = Shadow(color = pal.homeShadow, blurRadius = 4f)),
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                }
            }

            val dotCount = state.pages.size + if (dragging) 1 else 0
            if (dotCount > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(dotCount) { i ->
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
                    .onGloballyPositioned { dockWin = it.boundsInWindow() }
                    .clip(shape)
                    .background(pal.panel.copy(alpha = pal.panel.alpha * state.dockAlpha / 100f))
                    .border(1.dp, pal.border, shape)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.dock.take(MAX_DOCK).forEach { dk ->
                    if (dk.startsWith("f:")) {
                        state.dockFolders[dk.removePrefix("f:")]?.let { f ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                FolderCell(f, apps, state.copy(showLabels = false), pal, ui) {
                                    if (f.isPair) ui.launchPair(f) else ui.folderId = f.id
                                }
                            }
                        }
                    } else apps[dk]?.let { app ->
                        val hide = dragging && drag?.fromDock == true && drag?.ref == "a:${app.key}"
                        AppCell(
                            ui, app, Where.DOCK, state, pal.onPanel, Color.Transparent,
                            Modifier.weight(1f).alpha(if (hide) 0f else 1f),
                            showLabel = false, interactive = false,
                        )
                    }
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

        // ───── مكان الإفلات + العنصر المسحوب ─────
        val d = drag ?: ext?.let {
            DragState("a:${it.app.key}", false, it.pos - rootPos, true, it.app.icon, 1, 1)
        }
        if (d != null && d.moved) {
            val w = d.pos + rootPos
            val target: Rect? = when {
                dockWin.contains(w) -> dockWin
                else -> gridHit(w)?.let { g ->
                    val s = state
                    val wide = d.wCells >= s.columns
                    var cell = if (wide) (g.cell / s.columns) * s.columns else g.cell
                    val maxRow = s.rows - d.hCells
                    if (cell / s.columns > maxRow) cell = maxRow * s.columns + cell % s.columns
                    if (!wide && cell % s.columns + d.wCells > s.columns) {
                        cell = (cell / s.columns) * s.columns + (s.columns - d.wCells)
                    }
                    cellRectWin(cell, d.wCells, d.hCells)
                }
            }
            if (target != null) {
                val r = target.translate(-rootPos.x, -rootPos.y)
                Box(
                    Modifier
                        .absoluteOffset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                        .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                        .clip(RoundedCornerShape(18.dp))
                        .background(pal.accent.copy(alpha = 0.14f))
                        .border(2.dp, pal.accent.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                )
            }

            run {
                val zn = zoneAt(d.pos)
                val isApp = d.ref.startsWith("a:")
                Row(
                    Modifier.fillMaxWidth().height(with(density) { zoneH.toDp() }).padding(top = insets.calculateTopPadding()),
                ) {
                    Box(
                        Modifier.weight(1f).fillMaxSize().padding(6.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (zn == 1 || (zn == 2 && !isApp)) Color(0xDDFF5252) else Color(0x99000000)),
                        contentAlignment = Alignment.Center,
                    ) { Text("⊖  " + tr("إزالة", "Remove"), color = Color.White) }
                    if (isApp) {
                        Box(
                            Modifier.weight(1f).fillMaxSize().padding(6.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (zn == 2) Color(0xDDB71C1C) else Color(0x99000000)),
                            contentAlignment = Alignment.Center,
                        ) { Text("🗑  " + tr("إلغاء التثبيت", "Uninstall"), color = Color.White) }
                    }
                }
            }
            val gw = if (d.wCells > 1 || d.hCells > 1) cellW() * d.wCells else with(density) { 64.dp.toPx() }
            val gh = if (d.wCells > 1 || d.hCells > 1) cellH() * d.hCells else gw
            Box(
                Modifier
                    .absoluteOffset { IntOffset((d.pos.x - gw / 2).roundToInt(), (d.pos.y - gh / 2).roundToInt()) }
                    .size(with(density) { gw.toDp() }, with(density) { gh.toDp() })
                    .alpha(0.92f),
                contentAlignment = Alignment.Center,
            ) {
                if (d.icon != null) {
                    Image(d.icon, null, Modifier.fillMaxSize())
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                            .background(pal.panel)
                            .border(1.5.dp, pal.accent, RoundedCornerShape(18.dp))
                    )
                }
            }
        }
    }
}
