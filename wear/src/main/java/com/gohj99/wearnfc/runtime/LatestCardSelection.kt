package com.gohj99.wearnfc.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 调用者取消只停止等待；实际操作由管理器完成，队列只保留最新目标。 */
internal class LatestCardSelection(
    private val scope: CoroutineScope,
    private val apply: suspend (cardId: String, force: Boolean, isLatest: () -> Boolean) -> Boolean,
) {
    private class Request(val cardId: String, val force: Boolean) {
        val result = CompletableDeferred<Boolean>()
    }

    private val guard = Any()
    private var pending: Request? = null
    private var latest: Request? = null
    private var running = false
    private val _requestedCardId = MutableStateFlow<String?>(null)
    val requestedCardId = _requestedCardId.asStateFlow()

    fun submit(cardId: String, force: Boolean = false): Deferred<Boolean> = synchronized(guard) {
        val request = Request(cardId, force)
        pending?.result?.complete(false)
        pending = request
        latest = request
        _requestedCardId.value = cardId
        if (!running) {
            running = true
            scope.launch { drain() }
        }
        request.result
    }

    private suspend fun drain() {
        while (true) {
            val request = synchronized(guard) {
                pending.also {
                    pending = null
                    if (it == null) {
                        running = false
                        latest = null
                        _requestedCardId.value = null
                    }
                }
            } ?: return
            try {
                val success = apply(request.cardId, request.force) {
                    synchronized(guard) { latest === request }
                }
                request.result.complete(success)
            } catch (error: CancellationException) {
                request.result.cancel(error)
                throw error
            } catch (error: Exception) {
                request.result.completeExceptionally(error)
            }
        }
    }
}
