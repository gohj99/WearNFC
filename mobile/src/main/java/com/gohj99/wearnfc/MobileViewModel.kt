package com.gohj99.wearnfc

import android.content.ContentResolver
import android.net.Uri
import android.nfc.Tag
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gohj99.wearnfc.data.newCardId
import com.gohj99.wearnfc.sync.PhoneCommunication
import com.gohj99.wearnfc.utils.NfcReadResult
import com.gohj99.wearnfc.utils.parseCardDump
import com.gohj99.wearnfc.utils.readNfcCard
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AddStage { Closed, Scanning, Reading, Importing, Details }
data class AddCardState(
    val stage: AddStage = AddStage.Closed,
    val cardId: String = "",
    val watchId: String = "",
    val result: NfcReadResult? = null,
    val progress: String = "",
    val error: String? = null,
    val scanAttempt: Int = 0,
    val suggestedName: String = "新卡片",
    val isSaving: Boolean = false,
)

class MobileViewModel : ViewModel() {
    private val _add = MutableStateFlow(AddCardState())
    val add = _add.asStateFlow()
    private var readJob: Job? = null
    private var saveJob: Job? = null
    private var addRequestJob: Job? = null

    fun requestAddFromWatch() {
        if (addRequestJob?.isActive == true) return
        addRequestJob = viewModelScope.launch {
            // 冷启动时先完成手表握手，再进入读卡流程；请求只消费一次。
            PhoneCommunication.state.first { it.connected && !it.busy }
            beginAdd()
        }
    }

    fun beginAdd() {
        val connection = PhoneCommunication.state.value
        val watch = connection.watch
        if (connection.busy) return
        // 重复唤起时保留正在读取的卡片及已填写的资料。
        if (_add.value.stage != AddStage.Closed &&
            (_add.value.watchId.isBlank() || _add.value.watchId == watch?.id)
        ) return
        readJob?.cancel()
        val watchId = if (connection.connected) watch?.id.orEmpty() else ""
        _add.value = AddCardState(AddStage.Scanning, newCardId(), watchId)
    }

    fun retryScan() { _add.update { it.copy(stage = AddStage.Scanning, result = null, error = null, scanAttempt = it.scanAttempt + 1) } }
    fun scanError(message: String) { _add.update { it.copy(error = message) } }

    fun readTag(tag: Tag) {
        if (_add.value.stage != AddStage.Scanning || readJob?.isActive == true) return
        val cardId = _add.value.cardId
        _add.update { it.copy(stage = AddStage.Reading, error = null, progress = "正在读取 UID…") }
        readJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    readNfcCard(tag) { read, total ->
                        _add.update { if (it.cardId == cardId) it.copy(progress = "已读取 $read / $total 个数据块") else it }
                    }
                }
            }
            if (_add.value.cardId != cardId) return@launch
            result.onSuccess { data ->
                _add.update { it.copy(stage = AddStage.Details, result = data, error = null) }
            }.onFailure { error ->
                _add.update { it.copy(stage = AddStage.Scanning, error = error.message ?: "读取失败，请重试") }
            }
        }
    }

    fun importCardFile(contentResolver: ContentResolver, uri: Uri) {
        val connection = PhoneCommunication.state.value
        if (connection.busy) {
            PhoneCommunication.report("正在与手表通信，请稍后重试")
            return
        }
        readJob?.cancel()
        val cardId = newCardId()
        _add.value = AddCardState(
            stage = AddStage.Importing,
            cardId = cardId,
            watchId = "",
        )
        readJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val data = contentResolver.openInputStream(uri)?.use { input ->
                        parseCardDump(input.readCardFile())
                    } ?: error("无法打开所选文件")
                    ImportedCardFile(data, contentResolver.cardFileName(uri))
                }
            }
            if (_add.value.cardId != cardId) return@launch
            result.onSuccess { imported ->
                _add.value = AddCardState(
                    stage = AddStage.Details,
                    cardId = cardId,
                    watchId = "",
                    result = imported.result,
                    suggestedName = imported.suggestedName,
                )
            }.onFailure { error ->
                _add.value = AddCardState()
                PhoneCommunication.report(error.message ?: "文件解析失败，请选择有效的卡片 dump")
            }
        }
    }

    fun closeAdd() {
        if (PhoneCommunication.state.value.busy) return
        addRequestJob?.cancel()
        readJob?.cancel()
        saveJob?.cancel()
        _add.value = AddCardState()
    }

    fun saveCard(name: String, detail: String, color: Long) {
        val draft = _add.value
        val result = draft.result ?: return
        if (draft.isSaving) return
        _add.update { it.copy(isSaving = true) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            try {
                val connection = PhoneCommunication.state.first { it.connected && !it.busy }
                val watchId = connection.watch?.id ?: error("未找到已连接的手表")
                val saved = PhoneCommunication.addCard(
                    draft.cardId,
                    name,
                    detail,
                    color,
                    result.type,
                    result.dump,
                    watchId,
                )
                if (_add.value.cardId != draft.cardId) return@launch
                if (saved) _add.value = AddCardState()
                else _add.update { it.copy(isSaving = false) }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_add.value.cardId == draft.cardId) _add.update { it.copy(isSaving = false) }
                PhoneCommunication.report(error.message ?: "添加卡片失败")
            }
        }
    }

    fun selectCard(id: String) { viewModelScope.launch { PhoneCommunication.selectCard(id) } }
    fun deleteCard(id: String) { viewModelScope.launch { PhoneCommunication.deleteCard(id) } }
    fun updateCard(id: String, name: String, detail: String, color: Long, onSuccess: () -> Unit) {
        viewModelScope.launch { if (PhoneCommunication.updateCard(id, name, detail, color)) onSuccess() }
    }
}

private data class ImportedCardFile(val result: NfcReadResult, val suggestedName: String)

private fun java.io.InputStream.readCardFile(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= 256 * 1024) { "文件过大，无法作为卡片 dump 导入" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun ContentResolver.cardFileName(uri: Uri): String {
    val fileName = runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    val leafName = fileName.substringAfterLast('/')
    val baseName = leafName.substringBeforeLast('.', leafName)
        .replace(Regex("[\\p{Cntrl}]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return baseName.take(40).ifBlank { "导入卡片" }
}
