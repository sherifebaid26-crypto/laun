package com.aura.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures

private val Danger = Color(0xFFFF4D4F)

/** قائمة الضغط المطوّل: بتظهر قريبة من العنصر بتصميم iOS أو Samsung أو Vivo. */
@Composable
fun ContextMenuHost(ui: Ui, pal: AuraPalette, style: MenuStyle) {
    val req = ui.menu ?: return
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val anim = remember(req) { Animatable(0f) }
    LaunchedEffect(req) { anim.animateTo(1f, tween(170)) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .background(Color.Black.copy(alpha = 0.38f * anim.value))
            .pointerInput(req) { detectTapGestures { ui.menu = null } },
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val a = req.anchor.translate(-origin.x, -origin.y)

        val menuW = with(density) { (if (style == MenuStyle.SAMSUNG) 250.dp else 236.dp).toPx() }
        val rowH = with(density) { (if (style == MenuStyle.IOS) 48.dp else 52.dp).toPx() }
        val vPad = with(density) { 8.dp.toPx() }
        val gap = with(density) { 10.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val desiredH = req.entries.size * rowH + vPad * 2

        val below = screenH - a.bottom - margin - gap
        val above = a.top - margin - gap
        val placeBelow = below >= desiredH || below >= above
        val maxH = (if (placeBelow) below else above).coerceAtLeast(rowH * 2)
        val menuH = minOf(desiredH, maxH)

        val x = (a.center.x - menuW / 2f).coerceIn(margin, (screenW - menuW - margin).coerceAtLeast(margin))
        val y = if (placeBelow) a.bottom + gap else a.top - gap - menuH

        Box(
            Modifier
                .absoluteOffset(x = with(density) { x.toDp() }, y = with(density) { y.coerceAtLeast(margin).toDp() })
                .width(with(density) { menuW.toDp() })
                .graphicsLayer {
                    val k = 0.88f + 0.12f * anim.value
                    scaleX = k
                    scaleY = k
                    alpha = anim.value
                    transformOrigin = TransformOrigin(0.5f, if (placeBelow) 0f else 1f)
                }
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            MenuCard(ui, req.entries, pal, style, with(density) { menuH.toDp() }, with(density) { rowH.toDp() })
        }
    }
}

@Composable
private fun MenuCard(
    ui: Ui,
    entries: List<MenuEntry>,
    pal: AuraPalette,
    style: MenuStyle,
    height: androidx.compose.ui.unit.Dp,
    rowH: androidx.compose.ui.unit.Dp,
) {
    val shape = when (style) {
        MenuStyle.IOS -> RoundedCornerShape(22.dp)
        MenuStyle.SAMSUNG -> RoundedCornerShape(26.dp)
        MenuStyle.VIVO -> RoundedCornerShape(16.dp)
    }
    val bg = when (style) {
        MenuStyle.IOS -> pal.dialog.copy(alpha = 0.93f)
        else -> pal.dialog
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = height)
            .shadow(18.dp, shape, clip = false)
            .clip(shape)
            .background(bg)
            .border(0.6.dp, pal.border, shape)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        entries.forEachIndexed { i, e ->
            val color = if (e.danger) Danger else pal.onPanel
            val click = Modifier.clickable {
                ui.menu = null
                e.onClick()
            }
            when (style) {
                MenuStyle.IOS -> {
                    Row(
                        Modifier.fillMaxWidth().height(rowH).then(click).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(e.label, color = color, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(e.glyph, color = color.copy(alpha = 0.85f), fontSize = 18.sp)
                    }
                    if (i < entries.lastIndex) {
                        Box(
                            Modifier.fillMaxWidth().height(0.5.dp).padding(horizontal = 18.dp)
                                .background(pal.onPanel.copy(alpha = 0.14f))
                        )
                    }
                }
                MenuStyle.SAMSUNG -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(rowH)
                            .clip(RoundedCornerShape(18.dp)).then(click).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(if (e.danger) Danger.copy(alpha = 0.16f) else pal.accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(e.glyph, color = if (e.danger) Danger else pal.accent, fontSize = 16.sp) }
                        Text(
                            e.label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                MenuStyle.VIVO -> {
                    Row(
                        Modifier.fillMaxWidth().height(rowH).then(click).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(e.glyph, color = if (e.danger) Danger else pal.accent, fontSize = 18.sp,
                            modifier = Modifier.width(30.dp))
                        Text(e.label, color = color, fontSize = 15.sp)
                    }
                    if (i < entries.lastIndex) {
                        Box(
                            Modifier.fillMaxWidth().height(0.5.dp).padding(start = 46.dp, end = 16.dp)
                                .background(pal.onPanel.copy(alpha = 0.12f))
                        )
                    }
                }
            }
        }
    }
}
