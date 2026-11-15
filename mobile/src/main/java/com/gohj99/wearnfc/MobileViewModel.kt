package com.gohj99.wearnfc

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gohj99.wearnfc.data.newCardId
import com.gohj99.wearnfc.sync.PhoneCommunication
import com.gohj99.wearnfc.utils.NfcReadResult
import com.gohj99.wearnfc.utils.readNfcCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AddStage { Closed, Scanning, Reading, Details }
data class AddCardState(
    val stage: AddStage = AddStage.Closed,
    val cardId: String = "",
    val watchId: String = "",
    val result: NfcReadResult? = null,
    val progress: String = "",
    val error: String? = null,
    val scanAttempt: Int = 0,
)

class MobileViewModel : ViewModel() {
    private val _add = MutableStateFlow(AddCardState())
    val add = _add.asStateFlow()
    private var readJob: Job? = null

    fun beginAdd() {
        val watch = PhoneCommunication.state.value.watch ?: return
        _add.value = AddCardState(AddStage.Scanning, newCardId(), watch.id)
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

    fun closeAdd() {
        if (PhoneCommunication.state.value.busy) return
        readJob?.cancel()
        _add.value = AddCardState()
    }

    fun saveCard(name: String, detail: String, number: String, color: Long) {
        val draft = _add.value
        val result = draft.result ?: return
        viewModelScope.launch {
            if (PhoneCommunication.addCard(draft.cardId, name, detail, number, color,
                    result.type, result.dump, draft.watchId)) _add.value = AddCardState()
        }
    }

    fun selectCard(id: String) { viewModelScope.launch { PhoneCommunication.selectCard(id) } }
    fun deleteCard(id: String) { viewModelScope.launch { PhoneCommunication.deleteCard(id) } }
    fun updateCard(id: String, name: String, detail: String, number: String, color: Long, onSuccess: () -> Unit) {
        viewModelScope.launch { if (PhoneCommunication.updateCard(id, name, detail, number, color)) onSuccess() }
    }
}
