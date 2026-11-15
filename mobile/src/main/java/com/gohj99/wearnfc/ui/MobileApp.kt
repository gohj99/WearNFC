package com.gohj99.wearnfc.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gohj99.wearnfc.AddCardState
import com.gohj99.wearnfc.AddStage
import com.gohj99.wearnfc.MobileViewModel
import com.gohj99.wearnfc.data.IcCardType
import com.gohj99.wearnfc.data.IcCardUi
import com.gohj99.wearnfc.sync.PhoneCommunication
import com.gohj99.wearnfc.sync.PhoneConnectionState
import com.gohj99.wearnfc.sync.WearProtocol

private val cardColors = listOf(
    0xFF146C65L, 0xFF65468CL, 0xFF285A8EL,
    0xFF87612CL, 0xFF8B4057L, 0xFF3D6B72L,
)
private val cardColorNames = listOf("松绿", "紫色", "蓝色", "琥珀", "玫瑰", "青灰")

@Composable
fun MobileApp(model: MobileViewModel) {
    val connection by PhoneCommunication.state.collectAsState()
    val draft by model.add.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var chooseWatch by rememberSaveable { mutableStateOf(false) }
    val cards = connection.snapshot?.cards.orEmpty()
    val editing = cards.firstOrNull { it.id == editingId }
    val deleting = cards.firstOrNull { it.id == deletingId }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            model.importCardFile(context.contentResolver, uri)
        }
    }

    LaunchedEffect(Unit) { PhoneCommunication.events.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(draft.cardId) {
        if (draft.stage != AddStage.Closed) {
            editingId = null
            deletingId = null
            chooseWatch = false
        }
    }
    LaunchedEffect(connection.connected, connection.watch?.id) {
        editingId = null
        deletingId = null
        if (connection.connected && draft.stage != AddStage.Closed &&
            draft.watchId.isNotBlank() && draft.watchId != connection.watch?.id
        ) model.closeAdd()
    }
    BackHandler(draft.stage != AddStage.Closed || editingId != null) {
        if (!connection.busy) {
            if (editingId != null) editingId = null else model.closeAdd()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (connection.connected && draft.stage == AddStage.Closed && editing == null) {
                FloatingActionButton(onClick = { if (!connection.busy) model.beginAdd() }) {
                    Text("＋ 添加卡片", modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                when {
                    draft.stage != AddStage.Closed -> AddCardPage(
                        draft = draft,
                        busy = connection.busy,
                        connected = connection.connected,
                        model = model,
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                    )
                    !connection.connected -> ConnectionPage(
                        state = connection,
                        onRefresh = PhoneCommunication::refresh,
                        onAdd = model::beginAdd,
                    )
                    editing != null -> CardEditor(
                        title = "编辑卡片", key = editing.id, card = editing, busy = connection.busy,
                        onBack = { editingId = null }, onSave = { name, detail, color ->
                            model.updateCard(editing.id, name, detail, color) { editingId = null }
                        },
                    )
                    else -> CardList(connection,
                        onRefresh = PhoneCommunication::refresh,
                        onChooseWatch = { chooseWatch = true },
                        onRestore = { model.selectCard(WearProtocol.ORIGINAL_ID) },
                        onSelect = model::selectCard,
                        onEdit = { editingId = it },
                        onDelete = { deletingId = it },
                        onAdd = model::beginAdd,
                    )
                }
            }
        }
    }

    if (connection.connected && deleting != null) AlertDialog(
        onDismissRequest = { if (!connection.busy) deletingId = null },
        title = { Text("删除“${deleting.name}”？") },
        text = { Text(if (deleting.id == connection.snapshot?.activeCardId)
            "这张卡正在使用。手表将先还原原始配置，再删除卡片。" else "这张卡片将从手表中删除。") },
        confirmButton = {
            TextButton(enabled = !connection.busy, onClick = { model.deleteCard(deleting.id); deletingId = null }) { Text("删除") }
        },
        dismissButton = { TextButton(enabled = !connection.busy, onClick = { deletingId = null }) { Text("取消") } },
    )
    if (chooseWatch) AlertDialog(
        onDismissRequest = { chooseWatch = false },
        title = { Text("选择手表") },
        text = { Column { connection.watches.forEach { watch ->
            TextButton(onClick = { PhoneCommunication.chooseWatch(watch.id); chooseWatch = false }) { Text(watch.name) }
        } } },
        confirmButton = { TextButton(onClick = { chooseWatch = false }) { Text("关闭") } },
    )
}

@Composable
private fun ConnectionPage(state: PhoneConnectionState, onRefresh: () -> Unit, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("WearNFC", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.weight(1f))
        WatchIllustration()
        Spacer(Modifier.height(28.dp))
        Text(if (state.connecting) "正在连接手表" else "连接你的手表", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(state.status, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text("与手表通信成功后，这里会显示手表上的卡片。", textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        if (state.connecting) CircularProgressIndicator(Modifier.size(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.connecting) {
                Button(onClick = onRefresh) { Text("重新连接") }
            }
            OutlinedButton(onClick = onAdd) { Text("添加卡片") }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun WatchIllustration() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(112.dp)) {
        val stroke = Stroke(3.dp.toPx())
        drawRoundRect(color.copy(alpha = .2f), Offset(size.width * .32f, 0f), Size(size.width * .36f, size.height), CornerRadius(14.dp.toPx()))
        drawRoundRect(Color(0xFF17131C), Offset(size.width * .15f, size.height * .2f), Size(size.width * .7f, size.height * .6f), CornerRadius(24.dp.toPx()))
        drawRoundRect(color, Offset(size.width * .15f, size.height * .2f), Size(size.width * .7f, size.height * .6f), CornerRadius(24.dp.toPx()), style = stroke)
        drawLine(color, Offset(size.width * .35f, size.height * .44f), Offset(size.width * .65f, size.height * .44f), 3.dp.toPx())
        drawLine(color.copy(alpha = .5f), Offset(size.width * .35f, size.height * .56f), Offset(size.width * .55f, size.height * .56f), 3.dp.toPx())
    }
}

@Composable
private fun CardList(state: PhoneConnectionState, onRefresh: () -> Unit, onChooseWatch: () -> Unit,
                     onRestore: () -> Unit, onSelect: (String) -> Unit, onEdit: (String) -> Unit,
                     onDelete: (String) -> Unit, onAdd: () -> Unit) {
    val snapshot = state.snapshot ?: return
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("卡片", style = MaterialTheme.typography.headlineLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Color(0xFF8AD6BA), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(state.watch?.name.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(enabled = !state.busy, onClick = onRefresh) { Text("刷新") }
                    if (state.watches.size > 1) TextButton(enabled = !state.busy, onClick = onChooseWatch) { Text("切换手表") }
                }
            }
        }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (!snapshot.moduleAvailable) item {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                Text("手表 NFC 模块暂不可用。可以管理卡片；切换使用前，请在手表端检查 Root 权限和模块。",
                    modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("原始配置", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(5.dp))
                        Text(if (snapshot.activeCardId == WearProtocol.ORIGINAL_ID) "当前使用 · 未启用模拟卡" else "恢复手表原本的 NFC 配置",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (snapshot.activeCardId != WearProtocol.ORIGINAL_ID) {
                        TextButton(enabled = !state.busy, onClick = onRestore) { Text("还原") }
                    }
                }
            }
        }
        if (snapshot.cards.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("还没有卡片", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Text("用手机 NFC 读取卡片，添加到手表。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                OutlinedButton(enabled = !state.busy, onClick = onAdd) { Text("添加第一张卡片") }
            }
        }
        items(snapshot.cards, key = { it.id }) { card ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardVisual(card, current = snapshot.activeCardId == card.id)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !state.busy, onClick = { onEdit(card.id) }) { Text("编辑") }
                    TextButton(enabled = !state.busy, onClick = { onDelete(card.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
                    Spacer(Modifier.weight(1f))
                    if (snapshot.activeCardId != card.id) {
                        Button(enabled = !state.busy, onClick = { onSelect(card.id) }) { Text("设为当前卡片") }
                    } else Text("正在使用", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CardVisual(card: IcCardUi, current: Boolean = false) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(Color(card.color), Color(card.color).copy(alpha = .78f), Color(0xFF171B1C))))
        .border(1.dp, Color.White.copy(alpha = if (current) .5f else .18f), RoundedCornerShape(28.dp))
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (current) "当前使用" else card.type.displayName(), color = Color.White.copy(alpha = .8f),
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("NFC", color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.background(Color.White.copy(alpha = .16f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Text(card.name, style = MaterialTheme.typography.headlineSmall, color = Color.White,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(card.detail.ifBlank { "暂无备注" },
            color = Color.White.copy(alpha = .8f),
            style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AddCardPage(
    draft: AddCardState,
    busy: Boolean,
    connected: Boolean,
    model: MobileViewModel,
    onImport: () -> Unit,
) {
    val result = draft.result
    if (draft.stage == AddStage.Details && result != null) {
        CardEditor("添加到手表", draft.cardId,
            IcCardUi(draft.cardId, draft.suggestedName, "", cardColors.first(), result.type, result.uid), busy,
            saving = draft.isSaving,
            waitingForConnection = draft.isSaving && !connected,
            backEnabled = !busy,
            hint = when {
                draft.isSaving && !connected -> "正在等待手表连接，连接恢复后会自动添加卡片。可以取消等待。"
                draft.isSaving -> "正在向手表添加卡片…"
                result.blocksRead == 0 -> "已读取 UID。未读取卡片内存，将按手表现有方式添加 UID 卡片。"
                else -> "已读取 ${result.blocksRead} / ${result.totalBlocks} 个数据块。未读出的加密数据不会被复制。"
            },
            onBack = model::closeAdd, onSave = model::saveCard)
        return
    }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeader("添加卡片", model::closeAdd, canBack = !busy)
        Spacer(Modifier.height(36.dp))
        WatchIllustration()
        Text(
            when (draft.stage) {
                AddStage.Reading -> "正在读取卡片"
                AddStage.Importing -> "正在导入文件"
                else -> "把卡片贴近手机"
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            when (draft.stage) {
                AddStage.Reading -> "请保持卡片贴近手机，读取完成前不要移开。"
                AddStage.Importing -> "正在解析卡片数据，请稍候。"
                else -> "将卡片贴在手机背面的 NFC 感应区域。"
            },
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (draft.stage == AddStage.Reading || draft.stage == AddStage.Importing) {
            CircularProgressIndicator(Modifier.size(32.dp))
            if (draft.progress.isNotBlank()) Text(draft.progress, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("支持当前手表模块使用的 4 字节 UID NFC-A 卡片。", textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(enabled = !busy, onClick = onImport) { Text("从文件导入") }
        }
        draft.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Button(onClick = model::retryScan) { Text("重试读取") }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                    .onFailure { PhoneCommunication.report("无法打开 NFC 设置，请从系统设置中开启") }
            }) { Text("打开 NFC 设置") }
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit, canBack: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        TextButton(enabled = canBack, onClick = onBack) { Text("取消") }
    }
}

@Composable
private fun CardEditor(title: String, key: String, card: IcCardUi, busy: Boolean, hint: String? = null,
                       saving: Boolean = false, waitingForConnection: Boolean = false,
                       backEnabled: Boolean = !busy, onBack: () -> Unit,
                       onSave: (String, String, Long) -> Unit) {
    var name by rememberSaveable(key) { mutableStateOf(card.name) }
    var detail by rememberSaveable(key) { mutableStateOf(card.detail) }
    var color by rememberSaveable(key) { mutableStateOf(card.color) }
    val valid = name.trim().length in 1..40 && detail.length <= 120
    val locked = busy || saving
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeader(title, onBack, canBack = backEnabled)
        CardVisual(card.copy(name = name.ifBlank { "卡片名称" }, detail = detail, color = color))
        hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        OutlinedTextField(value = name, onValueChange = { if (it.length <= 40 || it.length < name.length) name = it }, enabled = !locked,
            label = { Text("卡片名称") }, singleLine = true, isError = name.isBlank(),
            supportingText = { Text("${name.length} / 40") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        OutlinedTextField(value = detail, onValueChange = { if (it.length <= 120 || it.length < detail.length) detail = it }, enabled = !locked,
            label = { Text("备注（选填）") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
        Text("卡片颜色", style = MaterialTheme.typography.titleSmall)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val colorRowFits = maxWidth.value >= 6f * 44f + 5f * 12f
            Row(
                modifier = if (colorRowFits) Modifier.fillMaxWidth()
                else Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = if (colorRowFits) {
                    Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                } else {
                    Arrangement.spacedBy(12.dp)
                },
            ) {
                cardColors.forEachIndexed { index, value ->
                    Box(Modifier.size(44.dp).clip(CircleShape)
                        .background(Color(value))
                        .border(if (color == value) 3.dp else 0.dp,
                            if (color == value) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                        .selectable(selected = color == value, enabled = !locked, role = Role.RadioButton, onClick = { color = value })
                        .semantics { contentDescription = cardColorNames[index] }, contentAlignment = Alignment.Center) {
                        if (color == value) Text("✓", color = Color.White)
                    }
                }
            }
        }
        Button(enabled = valid && !busy && !saving, onClick = { onSave(name.trim(), detail.trim(), color) },
            modifier = Modifier.fillMaxWidth().height(54.dp)) {
            if (busy || saving) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)) }
            Text(when {
                waitingForConnection -> "等待手表连接…"
                busy -> "等待手表确认…"
                saving -> "正在添加…"
                else -> "保存到手表"
            })
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun IcCardType.displayName() = when (this) {
    IcCardType.MIFARE_CLASSIC_1K -> "MIFARE Classic 1K"
    IcCardType.MIFARE_CLASSIC_4K -> "MIFARE Classic 4K"
}
