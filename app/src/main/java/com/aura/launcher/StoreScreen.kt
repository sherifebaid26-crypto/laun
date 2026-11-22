package com.aura.launcher

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FreeGreen = Color(0xFF7BD9A8)

@Composable
fun ThemeImage(def: ThemeDef, installed: Boolean, reqWidth: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, def.id, installed) {
        value = ThemeStore.previewBitmap(context, def, installed, reqWidth)
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = def.name,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(Color(0x22808080)))
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, pal: AuraPalette, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else pal.onPanel,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) pal.accent else pal.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun TopBar(title: String, pal: AuraPalette, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(
            "‹",
            color = pal.onPanel,
            fontSize = 30.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 2.dp),
        )
        Text(title, color = pal.onPanel, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun StoreScreen(ui: Ui, state: AuraState, pal: AuraPalette) {
    val vm = ui.vm
    val catalog by vm.catalog.collectAsState()
    val installed by vm.installed.collectAsState()
    val busy by vm.busy.collectAsState()
    val insets = WindowInsets.systemBars.asPaddingValues()
    var tab by remember { mutableIntStateOf(0) }
    var catFilter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.loadCatalog() }

    val cat = catalog
    val ready = if (cat is CatalogState.Ready) cat.themes else emptyList()
    val detailId = ui.storeDetail
    val detailDef = detailId?.let { id -> ready.firstOrNull { it.id == id } ?: installed[id] }

    Box(Modifier.fillMaxSize().background(pal.drawerBg)) {
        Column(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding() + 4.dp)) {
            TopBar(tr("متجر الثيمات", "Theme store"), pal) { ui.storeOpen = false }

            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(tr("كل الثيمات", "All themes"), tab == 0, pal) { tab = 0 }
                Chip(tr("المحمّلة (${installed.size})", "Downloaded (${installed.size})"), tab == 1, pal) { tab = 1 }
            }

            if (tab == 0 && cat is CatalogState.Ready && cat.offline) {
                Text(
                    tr("مفيش إنترنت، ده آخر كتالوج متسجّل.", "You're offline. Showing the last saved catalog."),
                    color = pal.onPanel.copy(alpha = 0.6f), fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }

            if (tab == 0 && ready.isNotEmpty()) {
                val cats = ready.map { it.categoryEn to it.category }.distinctBy { it.first }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp),
                ) {
                    item { Chip(tr("الكل", "All"), catFilter == null, pal) { catFilter = null } }
                    items(cats) { (k, label) -> Chip(label, catFilter == k, pal) { catFilter = k } }
                }
            }
            val list = if (tab == 0) ready.filter { catFilter == null || it.categoryEn == catFilter } else installed.values.toList()
            when {
                tab == 0 && cat is CatalogState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = pal.accent)
                }
                tab == 0 && cat is CatalogState.Error -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        tr("ماقدرتش أحمّل المتجر. اتأكد من الإنترنت وجرّب تاني.",
                            "Couldn't load the store. Check your connection and try again."),
                        color = pal.onPanel, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.loadCatalog() }) { Text(tr("إعادة المحاولة", "Retry")) }
                }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        tr("لسه ماحمّلتش أي ثيم.", "No downloaded themes yet."),
                        color = pal.onPanel.copy(alpha = 0.6f),
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp,
                        bottom = insets.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { def ->
                        ThemeCard(
                            def = def,
                            installed = installed.containsKey(def.id),
                            applied = state.themeId == def.id,
                            pal = pal,
                        ) { ui.storeDetail = def.id }
                    }
                }
            }
        }

        if (detailDef != null) {
            ThemeDetail(
                ui = ui,
                state = state,
                pal = pal,
                def = detailDef,
                installed = installed.containsKey(detailDef.id),
                busy = detailDef.id in busy,
            )
        }
    }
}

@Composable
private fun ThemeCard(def: ThemeDef, installed: Boolean, applied: Boolean, pal: AuraPalette, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(20.dp))) {
            ThemeImage(def, installed, reqWidth = 360, modifier = Modifier.fillMaxSize())
            if (applied) {
                Text(
                    tr("مُطبّق ✓", "Applied ✓"),
                    color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(pal.accent)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            def.name, color = pal.onPanel, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
        Text(
            def.tags.joinToString(" · "), color = pal.onPanel.copy(alpha = 0.6f), fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp),
        )
        Row(Modifier.padding(start = 4.dp, top = 2.dp)) {
            Text(tr("مجاني", "Free"), color = FreeGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("  ·  ${def.sizeKb} KB", color = pal.onPanel.copy(alpha = 0.5f), fontSize = 12.sp)
            if (installed) Text("  ·  ✓", color = pal.accent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ThemeDetail(
    ui: Ui,
    state: AuraState,
    pal: AuraPalette,
    def: ThemeDef,
    installed: Boolean,
    busy: Boolean,
) {
    val vm = ui.vm
    val insets = WindowInsets.systemBars.asPaddingValues()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val applied = state.themeId == def.id

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.drawerBg)
            .padding(top = insets.calculateTopPadding() + 4.dp),
    ) {
        TopBar(def.name, pal) { ui.storeDetail = null }

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(start = 20.dp, end = 60.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
        ) { page ->
            ThemePreview(def, installed, page)
        }

        Column(Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) {
            Text(tr("مجاني", "Free"), color = FreeGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "${def.sizeKb} KB  ·  ${def.author}",
                color = pal.onPanel.copy(alpha = 0.6f), fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                def.tags.take(4).forEach { tag ->
                    Text(
                        tag, color = pal.onPanel, fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(pal.field)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            Text(
                tr(
                    "شاشة القفل: لو جهازك ما غيّرهاش، استخدم «خلفية حيّة» (بتشتغل على القفل والرئيسية) أو احفظ الصورة وغيّرها من إعدادات الجهاز.",
                    "Lock screen: if your device didn't change it, use the live wallpaper (works on lock & home) or save the image and set it from device settings.",
                ),
                color = pal.onPanel.copy(alpha = 0.55f), fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (installed) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr("خلفية حيّة للقفل والرئيسية", "Live wallpaper (lock + home)"),
                        color = Color.Black, fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(pal.accent)
                            .clickable { vm.openLiveWallpaper(def) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Text(
                        tr("حفظ الصورة في المعرض", "Save image to gallery"),
                        color = pal.onPanel, fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(pal.field)
                            .clickable { vm.saveWallpaperToGallery(def) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            if (installed && !applied) {
                TextButton(onClick = { vm.deleteTheme(def.id) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(tr("حذف الثيم من الجهاز", "Delete from device"), color = Color(0xFFFF5252))
                }
            }
        }

        val label = when {
            busy -> tr("جاري التحميل…", "Downloading…")
            applied -> tr("مُطبّق ✓", "Applied ✓")
            installed -> tr("تطبيق", "Apply")
            else -> tr("تحميل", "Download")
        }
        Button(
            onClick = { if (installed) vm.applyTheme(def) else vm.downloadTheme(def) },
            enabled = !busy && !applied,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = insets.calculateBottomPadding() + 12.dp)
                .height(54.dp),
        ) { Text(label, fontSize = 18.sp) }
    }
}

/** معاينة شكل الثيم: صفحة 0 شاشة القفل/الساعة، صفحة 1 الشاشة الرئيسية بأيقونات تطبيقاتك. */
@Composable
private fun ThemePreview(def: ThemeDef, installed: Boolean, page: Int) {
    val context = LocalContext.current
    val homeText = Color(def.homeText ?: 0xFFFFFFFF)
    val shadow = if (homeText.luminance() > 0.5f) Color(0x99000000) else Color(0x66FFFFFF)

    val icons by produceState<List<Pair<String, ImageBitmap>>>(emptyList(), def.icon, page) {
        value = if (page == 1) AppRepository.previewIcons(context, def.icon, 12) else emptyList()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = minOf(maxWidth, maxHeight * 0.46f)
        val h = w / 0.46f
        val scale = w.value / 360f

        Box(
            Modifier
                .size(w, h)
                .clip(RoundedCornerShape((26f * scale).dp))
                .background(Color(0xFF202020)),
        ) {
            ThemeImage(def, installed, reqWidth = 540, modifier = Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().padding(top = (40f * scale).dp)) {
                ClockHeader(
                    style = if (def.clock == ClockStyle.NONE) ClockStyle.BIG else def.clock,
                    color = homeText, shadow = shadow, scale = scale,
                )
                if (page == 1) {
                    Spacer(Modifier.weight(1f))
                    icons.chunked(4).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = (5f * scale).dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            row.forEach { (label, bmp) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Image(bmp, null, Modifier.size((46f * scale).dp))
                                    Text(
                                        label, color = homeText, fontSize = (9f * scale).sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = (3f * scale).dp, start = 2.dp, end = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height((26f * scale).dp))
                }
            }
        }
    }
}
