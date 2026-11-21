package com.gohj99.wearnfc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import com.gohj99.wearnfc.R
import com.gohj99.wearnfc.runtime.CardManager
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val cardColors = listOf(
    0xFF146C65L, 0xFF65468CL, 0xFF285A8EL,
    0xFF87612CL, 0xFF8B4057L, 0xFF3D6B72L,
)

/** 卡片详情页：UID 和添加时间只读，其余展示字段可点按编辑。 */
@Composable
internal fun CardDetailPage(cardId: String) {
    val navController = LocalNavController.current
    val cards by CardManager.cards.collectAsState()
    val card = cards.firstOrNull { it.id == cardId }

    LaunchedEffect(card) {
        if (card == null) navController?.popBackStack()
    }

    if (card == null) return

    val listState = rememberTransformingLazyColumnState()
    val overscrollEffect = rememberOverscrollEffect()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var editingName by remember(card.id) { mutableStateOf(false) }
    var draftName by remember(card.id, card.name) { mutableStateOf(card.name) }
    var editingDetail by remember(card.id) { mutableStateOf(false) }
    var draftDetail by remember(card.id, card.detail) { mutableStateOf(card.detail) }
    var editingColor by remember(card.id) { mutableStateOf(false) }
    var draftColor by remember(card.id, card.color) { mutableStateOf(card.color) }
    var saving by remember(card.id) { mutableStateOf(false) }
    var deleting by remember(card.id) { mutableStateOf(false) }
    var showDeleteConfirmation by remember(card.id) { mutableStateOf(false) }
    var showDeleteError by remember(card.id) { mutableStateOf(false) }
    val isEditing = editingName || editingDetail || editingColor

    fun saveChanges() {
        val name = draftName.trim()
        val detail = draftDetail.trim()
        if (saving || detail.length > 120) return
        if (name.isBlank()) {
            draftName = card.name
            editingName = false
            keyboardController?.hide()
            return
        }
        scope.launch {
            saving = true
            try {
                if (CardManager.updateCard(card.id, name = name, detail = detail, color = draftColor)) {
                    editingName = false
                    editingDetail = false
                    editingColor = false
                    keyboardController?.hide()
                }
            } finally {
                saving = false
            }
        }
    }

    fun deleteCard() {
        if (deleting || saving) return
        scope.launch {
            deleting = true
            val removed = runCatching { CardManager.removeCard(card.id) }.getOrDefault(false)
            deleting = false
            showDeleteConfirmation = false
            if (!removed) showDeleteError = true
        }
    }

    LaunchedEffect(editingName, editingDetail) {
        if (editingName || editingDetail) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    AppScaffold {
        ScreenScaffold(
            scrollState = listState,
            overscrollEffect = overscrollEffect,
            edgeButton = {
                EdgeButton(
                    onClick = {
                        if (!saving && !deleting) {
                            if (isEditing) saveChanges() else showDeleteConfirmation = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors().copy(
                        containerColor = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        contentColor = if (isEditing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Text(
                        when {
                            saving || deleting -> "…"
                            isEditing -> stringResource(R.string.card_detail_done)
                            else -> stringResource(R.string.card_detail_delete)
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { contentPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                TransformingLazyColumn(
                    state = listState,
                    overscrollEffect = overscrollEffect,
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        ListHeader {
                            Text(
                                text = stringResource(R.string.card_detail_title),
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    item {
                        DetailRow(
                            label = stringResource(R.string.card_detail_uid),
                            value = card.uid,
                        )
                    }
                    item {
                        DetailRow(
                            label = stringResource(R.string.card_detail_added_at),
                            value = if (card.createdAt > 0L) {
                                formatAddedAt(card.createdAt)
                            } else {
                                stringResource(R.string.card_detail_unknown)
                            },
                        )
                    }
                    item {
                        EditableTextRow(
                            label = stringResource(R.string.card_detail_name),
                            value = draftName,
                            editing = editingName,
                            focusRequester = focusRequester,
                            onClick = { editingName = true },
                            onValueChange = { draftName = it.take(40) },
                            onDone = ::saveChanges,
                        )
                    }
                    item {
                        EditableTextRow(
                            label = stringResource(R.string.card_detail_detail),
                            value = draftDetail,
                            editing = editingDetail,
                            singleLine = true,
                            focusRequester = focusRequester,
                            onClick = { editingDetail = true },
                            onValueChange = { draftDetail = it.take(120) },
                            onDone = ::saveChanges,
                        )
                    }
                    item {
                        ColorPickerRow(
                            label = stringResource(R.string.card_detail_color),
                            selected = draftColor,
                            enabled = !saving,
                            onSelect = {
                                draftColor = it
                                editingColor = true
                            },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }

            AlertDialog(
                visible = showDeleteConfirmation,
                onDismissRequest = { if (!deleting) showDeleteConfirmation = false },
                confirmButton = {
                    AlertDialogDefaults.ConfirmButton(onClick = ::deleteCard) {
                        Text(stringResource(R.string.card_detail_delete_confirm))
                    }
                },
                dismissButton = {
                    AlertDialogDefaults.DismissButton(
                        onClick = { if (!deleting) showDeleteConfirmation = false },
                    ) {
                        Text(stringResource(R.string.card_detail_cancel))
                    }
                },
                title = { Text(stringResource(R.string.card_detail_delete_title)) },
                text = {
                    Text(stringResource(R.string.card_detail_delete_message, card.name))
                },
            )

            AlertDialog(
                visible = showDeleteError,
                onDismissRequest = { showDeleteError = false },
                confirmButton = {
                    AlertDialogDefaults.ConfirmButton(onClick = { showDeleteError = false }) {
                        Text(stringResource(R.string.error_dismiss))
                    }
                },
                dismissButton = {},
                title = { Text(stringResource(R.string.card_detail_delete_failed_title)) },
                text = { Text(stringResource(R.string.card_detail_delete_failed_message)) },
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EditableTextRow(
    label: String,
    value: String,
    editing: Boolean,
    singleLine: Boolean = true,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (editing) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 3,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        innerTextField()
                    }
                },
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ColorPickerRow(
    label: String,
    selected: Long,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            cardColors.chunked(3).forEach { rowColors ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowColors.forEach { value ->
                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .height(34.dp)
                                .background(Color(value), CircleShape)
                                .border(
                                    width = if (selected == value) 3.dp else 1.dp,
                                    color = if (selected == value) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape,
                                )
                                .clickable(enabled = enabled) { onSelect(value) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected == value) Text("✓", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun formatAddedAt(createdAt: Long): String {
    return DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
        Locale.getDefault(),
    ).format(Date(createdAt))
}
