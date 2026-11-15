package com.gohj99.wearnfc.sync

import com.gohj99.wearnfc.data.IcCardType
import com.gohj99.wearnfc.data.IcCardUi
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WatchSnapshot(
    val cards: List<IcCardUi>,
    val activeCardId: String,
    val moduleAvailable: Boolean,
)

data class WatchRequest(val id: String, val action: String, val payload: JSONObject)
data class WatchResponse(
    val requestId: String,
    val success: Boolean,
    val error: String,
    val snapshot: WatchSnapshot,
)

/** 两端共用的唯一协议定义；所有消息都带版本和请求 ID。 */
object WearProtocol {
    const val REQUEST_PATH = "/wearnfc/v1/request"
    const val RESPONSE_PATH = "/wearnfc/v1/response"
    const val WATCH_CAPABILITY = "wearnfc_watch_v1"
    const val ORIGINAL_ID = "__original__"
    const val SYNC = "sync"
    const val ADD = "add"
    const val UPDATE = "update"
    const val DELETE = "delete"
    const val SELECT = "select"
    const val MAX_CARDS = 100
    // Card metadata no longer carries the optional display number.
    private const val VERSION = 2
    private const val MAX_MESSAGE_BYTES = 90 * 1024
    private val actions = setOf(SYNC, ADD, UPDATE, DELETE, SELECT)

    fun request(action: String, payload: JSONObject = JSONObject()): WatchRequest {
        require(action in actions)
        return WatchRequest(UUID.randomUUID().toString(), action, payload)
    }

    fun encodeRequest(request: WatchRequest): ByteArray = JSONObject()
        .put("version", VERSION).put("requestId", request.id)
        .put("action", request.action).put("payload", request.payload).bytes()

    fun decodeRequest(bytes: ByteArray): WatchRequest {
        val json = parse(bytes)
        val id = json.getString("requestId").also { UUID.fromString(it) }
        val action = json.getString("action").also { require(it in actions) { "未知操作" } }
        return WatchRequest(id, action, json.getJSONObject("payload"))
    }

    fun encodeResponse(response: WatchResponse): ByteArray = JSONObject()
        .put("version", VERSION).put("requestId", response.requestId)
        .put("success", response.success).put("error", response.error)
        .put("activeCardId", response.snapshot.activeCardId)
        .put("moduleAvailable", response.snapshot.moduleAvailable)
        .put("cards", JSONArray().apply {
            response.snapshot.cards.forEach { card ->
                put(JSONObject().put("id", card.id).put("name", card.name)
                    .put("detail", card.detail)
                    .put("color", card.color).put("type", card.type.name).put("uid", card.uid)
                    .put("createdAt", card.createdAt))
            }
        }).bytes()

    fun decodeResponse(bytes: ByteArray): WatchResponse {
        val json = parse(bytes)
        val cards = json.getJSONArray("cards")
        require(cards.length() <= MAX_CARDS)
        val list = List(cards.length()) { index ->
            val card = cards.getJSONObject(index)
            IcCardUi(card.getString("id"), card.getString("name"), card.getString("detail"),
                card.getLong("color"),
                IcCardType.valueOf(card.getString("type")), card.getString("uid"),
                card.optLong("createdAt", 0L))
        }
        require(list.map { it.id }.distinct().size == list.size)
        val active = json.getString("activeCardId")
        require(active == ORIGINAL_ID || list.any { it.id == active }) { "当前卡片不存在" }
        return WatchResponse(json.getString("requestId"), json.getBoolean("success"),
            json.optString("error"), WatchSnapshot(list, active, json.getBoolean("moduleAvailable")))
    }

    fun validateCardFields(payload: JSONObject) {
        require(payload.getString("name").trim().length in 1..40) { "卡片名称需为 1–40 个字符" }
        require(payload.getString("detail").length <= 120) { "备注过长" }
        require(payload.getLong("color") in 0..0xFFFFFFFFL) { "卡片颜色无效" }
    }

    private fun parse(bytes: ByteArray): JSONObject {
        require(bytes.size <= MAX_MESSAGE_BYTES) { "消息过大" }
        return JSONObject(bytes.toString(Charsets.UTF_8)).also {
            require(it.getInt("version") == VERSION) { "请更新手机和手表上的 WearNFC" }
        }
    }

    private fun JSONObject.bytes() = toString().toByteArray(Charsets.UTF_8).also {
        require(it.size <= MAX_MESSAGE_BYTES) { "消息过大" }
    }
}
