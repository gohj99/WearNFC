package com.gohj99.wearnfc.sync

import com.gohj99.wearnfc.data.IcCardType
import com.gohj99.wearnfc.data.IcCardUi
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WearProtocolTest {
    private val card = IcCardUi("card-1", "门禁卡", "公司", 0xFF146C65L,
        IcCardType.MIFARE_CLASSIC_1K, "09F85906", 1794729600000L)

    @Test fun requestPreservesCorrelationAndPayload() {
        val request = WearProtocol.request(WearProtocol.SELECT, JSONObject().put("cardId", card.id))
        val decoded = WearProtocol.decodeRequest(WearProtocol.encodeRequest(request))
        assertEquals(request.id, decoded.id)
        assertEquals(WearProtocol.SELECT, decoded.action)
        assertEquals(card.id, decoded.payload.getString("cardId"))
    }

    @Test fun snapshotRoundTripPreservesCurrentCardAndArgb() {
        val response = WatchResponse("request-1", true, "", WatchSnapshot(listOf(card), card.id, true))
        val encoded = String(WearProtocol.encodeResponse(response))
        assertFalse(encoded.contains("number"))
        assertEquals(response, WearProtocol.decodeResponse(encoded.toByteArray()))
    }

    @Test fun emptyWatchCanConfirmOriginalConfiguration() {
        val response = WatchResponse("request-1", true, "", WatchSnapshot(emptyList(), WearProtocol.ORIGINAL_ID, false))
        assertEquals(response, WearProtocol.decodeResponse(WearProtocol.encodeResponse(response)))
    }

    @Test fun failedOperationStillCarriesAuthoritativeCurrentCard() {
        val response = WatchResponse("request-1", false, "切换失败", WatchSnapshot(listOf(card), card.id, false))
        val decoded = WearProtocol.decodeResponse(WearProtocol.encodeResponse(response))
        assertFalse(decoded.success)
        assertEquals(card.id, decoded.snapshot.activeCardId)
        assertEquals("切换失败", decoded.error)
    }

    @Test fun rejectsUnsupportedVersionBeforeDisplayingCards() {
        val request = WearProtocol.request(WearProtocol.SYNC)
        val json = JSONObject(String(WearProtocol.encodeRequest(request))).put("version", 3)
        assertThrows(IllegalArgumentException::class.java) { WearProtocol.decodeRequest(json.toString().toByteArray()) }
    }

    @Test fun rejectsUnknownOperation() {
        val request = WearProtocol.request(WearProtocol.SYNC)
        val json = JSONObject(String(WearProtocol.encodeRequest(request))).put("action", "unknown")
        assertThrows(IllegalArgumentException::class.java) { WearProtocol.decodeRequest(json.toString().toByteArray()) }
    }

    @Test fun rejectsNonexistentActiveCard() {
        val response = WatchResponse("request-1", true, "", WatchSnapshot(listOf(card), "deleted-card", true))
        assertThrows(IllegalArgumentException::class.java) { WearProtocol.decodeResponse(WearProtocol.encodeResponse(response)) }
    }

    @Test fun rejectsDuplicateCardIds() {
        val response = WatchResponse("request-1", true, "", WatchSnapshot(listOf(card, card), card.id, true))
        assertThrows(IllegalArgumentException::class.java) { WearProtocol.decodeResponse(WearProtocol.encodeResponse(response)) }
    }

    @Test fun rejectsOversizedMessages() {
        assertThrows(IllegalArgumentException::class.java) { WearProtocol.decodeRequest(ByteArray(100 * 1024)) }
    }

    @Test fun rejectsBlankNamesAndOutOfRangeColors() {
        fun fields(name: String, color: Long) = JSONObject().put("name", name).put("color", color)
            .put("detail", "")
        assertThrows(IllegalArgumentException::class.java) { WearProtocol.validateCardFields(fields("  ", 0)) }
        assertThrows(IllegalArgumentException::class.java) { WearProtocol.validateCardFields(fields("门禁", -1)) }
        WearProtocol.validateCardFields(fields("门禁", 0xFFFFFFFFL))
    }
}
