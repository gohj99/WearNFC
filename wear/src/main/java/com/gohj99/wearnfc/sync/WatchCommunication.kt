package com.gohj99.wearnfc.sync

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.core.net.toUri
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.gohj99.wearnfc.data.IcCardType
import com.gohj99.wearnfc.runtime.CardManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/** 手表端唯一通信入口；后台 ListenerService 与前台共用同一个处理器。 */
object WatchCommunication {
    private val mutex = Mutex()
    private val responses = object : LinkedHashMap<String, ByteArray>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > 50
    }

    /** 通过系统伴侣连接直接打开手机 Activity，不依赖手机应用已在前台。 */
    fun openPhoneApp(context: Context, onOpened: () -> Unit, onOpenFailed: () -> Unit) {
        val appContext = context.applicationContext
        try {
            // RemoteActivityHelper 只转发 VIEW + URI，将系统 INSERT 动作编码进应用 Intent URI。
            val phoneAppUri = Intent(Intent.ACTION_INSERT)
                .setPackage(appContext.packageName)
                .toUri(Intent.URI_ANDROID_APP_SCHEME)
                .toUri()
            val intent = Intent(Intent.ACTION_VIEW, phoneAppUri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
            Futures.addCallback(
                RemoteActivityHelper(appContext, appContext.mainExecutor).startRemoteActivity(intent),
                object : FutureCallback<Void?> {
                    override fun onSuccess(result: Void?) = onOpened()

                    override fun onFailure(error: Throwable) {
                        Log.w("WatchCommunication", "Failed to open the phone app", error)
                        onOpenFailed()
                    }
                },
                appContext.mainExecutor,
            )
        } catch (error: Exception) {
            Log.w("WatchCommunication", "Failed to open the phone app", error)
            onOpenFailed()
        }
    }

    suspend fun receive(context: Context, event: MessageEvent) {
        if (event.path != WearProtocol.REQUEST_PATH) return
        try {
            val request = WearProtocol.decodeRequest(event.data)
            val bytes = mutex.withLock {
                val key = "${event.sourceNodeId}/${request.id}"
                responses[key] ?: execute(request).also { responses[key] = it }
            }
            withTimeout(10_000.milliseconds) {
                Wearable.getMessageClient(context).sendMessage(
                    event.sourceNodeId, WearProtocol.RESPONSE_PATH, bytes,
                ).awaitResult()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w("WatchCommunication", "Failed to process phone message", error)
        }
    }

    private suspend fun execute(request: WatchRequest): ByteArray {
        var error = ""
        try {
            val p = request.payload
            // 冷启动时必须等待磁盘恢复，不能把尚未加载的空列表回给手机。
            val snapshot = CardManager.snapshot()
            when (request.action) {
                WearProtocol.SYNC -> Unit
                WearProtocol.ADD -> {
                    WearProtocol.validateCardFields(p)
                    val id = p.getString("cardId").also { java.util.UUID.fromString(it) }
                    require(snapshot.cards.size < WearProtocol.MAX_CARDS || snapshot.cards.any { it.id == id }) {
                        "最多添加 ${WearProtocol.MAX_CARDS} 张卡片"
                    }
                    val type = IcCardType.valueOf(p.getString("type"))
                    val dump = Base64.decode(p.getString("dump"), Base64.NO_WRAP)
                    require(dump.size == if (type == IcCardType.MIFARE_CLASSIC_4K) 4096 else 1024) { "卡片数据不完整" }
                    checkNotNull(CardManager.addCard(p.getString("name").trim(), p.getString("detail"),
                        p.getLong("color"), type, dump, id)) { "保存卡片失败" }
                }
                WearProtocol.UPDATE -> {
                    WearProtocol.validateCardFields(p)
                    check(CardManager.updateCard(p.getString("cardId"), p.getString("name").trim(),
                        p.getString("detail"), p.getLong("color"))) { "卡片已不存在，请刷新" }
                }
                WearProtocol.DELETE -> check(CardManager.removeCard(p.getString("cardId"))) {
                    "删除失败；当前卡片需先成功还原，请检查手表 NFC 模块"
                }
                WearProtocol.SELECT -> check(CardManager.switchToCard(p.getString("cardId"))) {
                    "切换失败，请检查手表 Root 权限和 NFC 模块"
                }
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "手表操作失败"
        }
        return WearProtocol.encodeResponse(WatchResponse(request.id, error.isEmpty(), error, CardManager.snapshot()))
    }
}
