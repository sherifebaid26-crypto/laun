package com.aura.launcher

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun PinDialog(
    title: String,
    errorTick: Int,
    pal: AuraPalette,
    onSubmit: (String) -> Unit,
    onBiometric: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    LaunchedEffect(errorTick) { if (errorTick > 0) pin = "" }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = pal.dialog) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = pal.onPanel, fontSize = 18.sp)
                Row(Modifier.padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(6) { i ->
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (i < pin.length) pal.accent else pal.field)
                        )
                    }
                }
                if (errorTick > 0) {
                    Text(tr("PIN غير صحيح", "Wrong PIN"), color = Color(0xFFFF5252), fontSize = 13.sp)
                }
                val rows = listOf(
                    listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
                    listOf("bio", "0", "del"),
                )
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 10.dp)) {
                        row.forEach { k ->
                            val label = when (k) {
                                "bio" -> if (onBiometric != null) tr("بصمة", "Bio") else ""
                                "del" -> tr("حذف", "Del")
                                else -> k
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(if (label.isEmpty()) Color.Transparent else pal.field)
                                    .clickable(enabled = label.isNotEmpty()) {
                                        when (k) {
                                            "bio" -> onBiometric?.invoke()
                                            "del" -> pin = pin.dropLast(1)
                                            else -> if (pin.length < 6) pin += k
                                        }
                                    },
                            ) { Text(label, color = pal.onPanel, fontSize = if (k.length == 1) 22.sp else 13.sp) }
                        }
                    }
                }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text(tr("إلغاء", "Cancel")) }
                    Button(onClick = { onSubmit(pin) }, enabled = pin.length >= 4) { Text(tr("تأكيد", "OK")) }
                }
            }
        }
    }
}

@Composable
fun TextInputDialog(req: TextRequest, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(req.initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(req.title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                singleLine = !req.multiline,
                minLines = if (req.multiline) 4 else 1,
                maxLines = if (req.multiline) 8 else 1,
            )
        },
        confirmButton = {
            TextButton(onClick = { req.onOk(text.trim()); onDismiss() }) { Text(tr("حفظ", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء", "Cancel")) } },
    )
}

@Composable
fun AppPickerDialog(apps: List<AppEntry>, hidden: Set<String>, req: PickerRequest, onDismiss: () -> Unit) {
    var sel by remember { mutableStateOf(req.selected) }
    val list = remember(apps) { if (req.includeHidden) apps else apps.filter { it.key !in hidden } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(req.title) },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                items(list, key = { it.key }) { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sel = if (app.key in sel) sel - app.key else sel + app.key }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(checked = app.key in sel, onCheckedChange = null)
                        Image(app.icon, null, Modifier.padding(horizontal = 10.dp).size(32.dp))
                        Text(app.label, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { req.onOk(sel); onDismiss() }) { Text(tr("تم", "Done")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء", "Cancel")) } },
    )
}

@Composable
fun WidgetPickerDialog(
    host: WidgetHost,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onPickBuiltin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val providers = remember { host.providers() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("اختر ودجت", "Choose a widget")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Text(tr("ودجتس Aura (قابلة للتعديل: حجم وشكل وخلفية)", "Aura widgets (resizable & styleable)"),
                        color = Color(0xFF00BFA5), fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                items(BuiltinKinds) { (kind, names) ->
                    Text(
                        tr(names.first, names.second),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickBuiltin(kind); onDismiss() }
                            .padding(vertical = 12.dp),
                    )
                }
                item {
                    Text(tr("ودجتس التطبيقات", "App widgets"), color = Color(0xFF00BFA5), fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                    Text(
                        tr(
                            "بعض الأجهزة (زي فيفو) بتمنع ودجتس النظام من لانشر تاني. لو ظهرت رسالة منع استخدم ودجتس Aura فوق.",
                            "Some devices (e.g. Vivo) block system widgets for third-party launchers. If you see a block message, use the Aura widgets above.",
                        ),
                        color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                if (providers.isEmpty()) {
                    item {
                        Text(
                            tr("مفيش ودجتس تطبيقات ظاهرة", "No app widgets found"),
                            color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(providers) { p ->
                    val pm = context.packageManager
                    val label = runCatching { p.loadLabel(pm)?.toString() }.getOrNull() ?: p.provider.shortClassName
                    val app = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(p.provider.packageName, 0)).toString()
                    }.getOrDefault(p.provider.packageName)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(p); onDismiss() }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(label)
                        Text(app, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء", "Cancel")) } },
    )
}

@Composable
fun SimpleChoiceDialog(
    title: String,
    options: List<Pair<String?, String>>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(options) { (value, label) ->
                    Text(
                        label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value); onDismiss() }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء", "Cancel")) } },
    )
}

@Composable
fun FolderDialog(ui: Ui, state: AuraState, apps: Map<String, AppEntry>, pal: AuraPalette, folder: HomeItem.Folder) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                ui.folderId = null
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = pal.dialog,
            modifier = Modifier
                .padding(28.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    folder.name,
                    color = pal.onPanel,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            ui.textInput = TextRequest(tr("اسم المجلد", "Folder name"), folder.name) { n ->
                                ui.vm.update { it.renameFolder(folder.id, n.ifBlank { folder.name }) }
                            }
                        }
                        .padding(bottom = 12.dp),
                )
                val members = folder.keys.mapNotNull { apps[it] }
                if (members.isEmpty()) {
                    Text(
                        tr("المجلد فاضي", "Empty folder"),
                        color = pal.onPanel.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.heightIn(max = 340.dp),
                    ) {
                        items(members, key = { it.key }) { app ->
                            AppCell(
                                ui = ui, app = app, where = Where.FOLDER, state = state,
                                textColor = pal.onPanel, shadow = Color.Transparent, folder = folder.id,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = {
                        ui.folderId = null
                        ui.vm.update { it.deleteFolder(folder.id) }
                    }) { Text(tr("حذف المجلد", "Delete folder"), color = Color(0xFFFF5252)) }
                    Button(onClick = {
                        ui.picker = PickerRequest(
                            title = tr("تطبيقات المجلد", "Folder apps"),
                            selected = folder.keys.toSet(),
                        ) { sel ->
                            ui.vm.update { st ->
                                val old = st.folderKeys(folder.id)
                                st.setFolderApps(folder.id, old.filter { it in sel } + sel.filter { it !in old })
                            }
                        }
                    }) { Text(tr("تعديل التطبيقات", "Edit apps")) }
                }
            }
        }
    }
}
