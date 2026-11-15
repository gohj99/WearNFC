package com.gohj99.wearnfc.sync

import android.content.Context
import android.util.Base64
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.gohj99.wearnfc.data.IcCardType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class ConnectedWatch(val id: String, val name: String)
data class PhoneConnectionState(
    val watch: ConnectedWatch? = null,
    val watches: List<ConnectedWatch> = emptyList(),
    val snapshot: WatchSnapshot? = null,
    val connecting: Boolean = true,
    val busy: Boolean = false,
    val status: String = "正在连接手表…",
) {
    val connected: Boolean get() = watch != null && snapshot != null
}

/** 手机端唯一通信入口。卡片仅存在于本次已确认的会话，不保存离线列表。 */
object PhoneCommunication {
    private val _state = MutableStateFlow(PhoneConnectionState())
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operationMutex = Mutex()
    private var sessionScope: CoroutineScope? = null
    private var polling: Job? = null
    private var generation = 0
    private var preferredWatchId: String? = null
    private var pending: Pending? = null
    private lateinit var appContext: Context
    private data class Pending(val node: String, val request: String, val result: CompletableDeferred<WatchResponse>)

    private val messageListener = MessageClient.OnMessageReceivedListener { event ->
        if (event.path == WearProtocol.RESPONSE_PATH) {
            scope.launch {
                val expected = pending ?: return@launch
                if (event.sourceNodeId != expected.node) return@launch
                runCatching { WearProtocol.decodeResponse(event.data) }.onSuccess { response ->
                    if (response.requestId == expected.request) expected.result.complete(response)
                }
            }
        }
    }
    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { capability ->
        scope.launch {
            val current = _state.value.watch ?: return@launch
            if (capability.nodes.none { it.id == current.id }) {
                generation++
                disconnect("手表已断开，正在重新连接…")
                pending?.result?.cancel()
            }
        }
    }

    fun start(context: Context) {
        if (sessionScope != null) return
        appContext = context.applicationContext
        val currentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        sessionScope = currentScope
        _state.value = PhoneConnectionState(status = "正在连接手表…")
        polling = currentScope.launch {
            try {
                Wearable.getMessageClient(appContext).addListener(messageListener).awaitResult()
                Wearable.getCapabilityClient(appContext)
                    .addListener(capabilityListener, WearProtocol.WATCH_CAPABILITY).awaitResult()
                while (isActive) {
                    if (operationMutex.tryLock()) {
                        try { syncLocked() } finally { operationMutex.unlock() }
                    }
                    delay(3_000)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                disconnect("连接服务不可用，请确认手机已安装并启用 Google Play 服务")
            }
        }
    }

    fun stop() {
        generation++
        sessionScope?.cancel()
        sessionScope = null
        polling = null
        pending?.result?.cancel()
        pending = null
        if (::appContext.isInitialized) {
            Wearable.getMessageClient(appContext).removeListener(messageListener)
            Wearable.getCapabilityClient(appContext).removeListener(capabilityListener)
        }
        _state.value = PhoneConnectionState(connecting = false, status = "等待连接手表")
    }

    fun refresh() {
        if (sessionScope != null && polling?.isActive != true) {
            stop()
            start(appContext)
            return
        }
        val activeScope = sessionScope ?: return
        activeScope.launch { operationMutex.withLock { syncLocked() } }
    }

    fun chooseWatch(id: String) {
        if (_state.value.busy || id == _state.value.watch?.id) return
        preferredWatchId = id
        generation++
        pending?.result?.cancel()
        disconnect("正在连接手表…")
        refresh()
    }

    private suspend fun syncLocked() {
        val session = generation
        try {
            _state.update { it.copy(connecting = !it.connected) }
            val nodes = withTimeout(8_000) {
                Wearable.getCapabilityClient(appContext).getCapability(
                    WearProtocol.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE,
                ).awaitResult().nodes
            }.sortedWith(compareByDescending<com.google.android.gms.wearable.Node> { it.isNearby }.thenBy { it.id })
            if (session != generation) return
            val watches = nodes.map { ConnectedWatch(it.id, it.displayName) }
            _state.update { it.copy(watches = watches) }
            val node = watches.firstOrNull { it.id == preferredWatchId }
                ?: watches.firstOrNull { it.id == _state.value.watch?.id }
                ?: watches.firstOrNull()
            if (node == null) {
                disconnect("尚未连接手表，请在已配对的手表上安装并打开 WearNFC")
                return
            }
            if (_state.value.watch?.id != node.id) disconnect("正在与 ${node.name} 通信…")
            val response = exchange(node.id, WearProtocol.request(WearProtocol.SYNC), 10_000)
            if (session != generation) return
            check(response.success) { response.error }
            _state.update { it.copy(watch = node, snapshot = response.snapshot, connecting = false, status = "已连接 ${node.name}") }
        } catch (error: Exception) {
            if (session != generation) return
            if (error is CancellationException && sessionScope == null) throw error
            disconnect("与手表通信失败，正在重试；请确认手表上的 WearNFC 已更新")
        }
    }

    suspend fun addCard(id: String, name: String, detail: String, color: Long,
                        type: IcCardType, dump: ByteArray, watchId: String): Boolean = command(
        WearProtocol.ADD, fields(name, detail, color).put("cardId", id)
            .put("type", type.name).put("dump", Base64.encodeToString(dump, Base64.NO_WRAP)),
        "卡片已添加到手表", watchId, waitForOperation = true,
    )

    suspend fun updateCard(id: String, name: String, detail: String, color: Long): Boolean = command(
        WearProtocol.UPDATE, fields(name, detail, color).put("cardId", id), "卡片已更新",
    )

    suspend fun deleteCard(id: String): Boolean = command(
        WearProtocol.DELETE, JSONObject().put("cardId", id), "卡片已删除",
    )

    suspend fun selectCard(id: String): Boolean = command(
        WearProtocol.SELECT, JSONObject().put("cardId", id),
        if (id == WearProtocol.ORIGINAL_ID) "手表已还原原始配置" else "已切换手表当前卡片",
    )

    private suspend fun command(
        action: String,
        payload: JSONObject,
        successMessage: String,
        watchId: String? = null,
        waitForOperation: Boolean = false,
    ): Boolean {
        // Deferred adds wait for the reconnect sync to finish; other actions remain fail-fast.
        if (waitForOperation) operationMutex.lock()
        else if (!operationMutex.tryLock()) {
            report("正在与手表通信，请稍后重试")
            return false
        }
        val session = generation
        try {
            val connection = _state.value
            val watch = connection.watch
            if (!connection.connected || watch == null || (watchId != null && watchId != watch.id)) {
                report("请先连接读取卡片时使用的手表")
                return false
            }
            _state.update { it.copy(busy = true) }
            val response = exchange(watch.id, WearProtocol.request(action, payload), 30_000)
            if (session != generation || sessionScope == null || _state.value.watch?.id != watch.id) return false
            _state.update { it.copy(snapshot = response.snapshot) }
            report(if (response.success) successMessage else response.error)
            return response.success
        } catch (error: Exception) {
            if (session != generation || sessionScope == null) return false
            disconnect("手表连接已中断，正在重新连接…")
            report("未收到手表确认，请重连后检查操作结果")
            return false
        } finally {
            if (session == generation) _state.update { it.copy(busy = false) }
            operationMutex.unlock()
        }
    }

    private suspend fun exchange(nodeId: String, request: WatchRequest, timeout: Long): WatchResponse = withTimeout(timeout) {
        val response = CompletableDeferred<WatchResponse>()
        val current = Pending(nodeId, request.id, response)
        pending = current
        try {
            Wearable.getMessageClient(appContext).sendMessage(nodeId, WearProtocol.REQUEST_PATH,
                WearProtocol.encodeRequest(request)).awaitResult()
            response.await()
        } finally {
            if (pending === current) pending = null
            response.cancel()
        }
    }

    private fun disconnect(status: String) {
        _state.update { it.copy(watch = null, snapshot = null, connecting = false, busy = false, status = status) }
    }

    fun report(message: String) { _events.tryEmit(message) }

    private fun fields(name: String, detail: String, color: Long) = JSONObject()
        .put("name", name.trim()).put("detail", detail).put("color", color)
}
