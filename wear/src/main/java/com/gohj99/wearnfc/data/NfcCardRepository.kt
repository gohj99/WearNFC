package com.gohj99.wearnfc.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class NfcCard(
    val id: String,
    val name: String,
    val detail: String,
    val number: String,
    val color: Long,
)

private val Context.nfcCardDataStore by preferencesDataStore(name = "nfc_cards")
private val cardsKey = stringPreferencesKey("cards")

class NfcCardRepository(private val context: Context) {
    val cards: Flow<List<NfcCard>> = context.nfcCardDataStore.data.map { preferences ->
        preferences[cardsKey]?.let(::decodeCards) ?: sampleCards
    }

    suspend fun saveCards(cards: List<NfcCard>) {
        context.nfcCardDataStore.edit { preferences ->
            preferences[cardsKey] = encodeCards(cards)
        }
    }

    private fun encodeCards(cards: List<NfcCard>): String = JSONArray().apply {
        cards.forEach { card ->
            put(
                JSONObject()
                    .put("id", card.id)
                    .put("name", card.name)
                    .put("detail", card.detail)
                    .put("number", card.number)
                    .put("color", card.color)
            )
        }
    }.toString()

    private fun decodeCards(value: String): List<NfcCard> = runCatching {
        val json = JSONArray(value)
        buildList {
            repeat(json.length()) { index ->
                val card = json.optJSONObject(index) ?: return@repeat
                add(
                    NfcCard(
                        id = card.optString("id"),
                        name = card.optString("name"),
                        detail = card.optString("detail"),
                        number = card.optString("number"),
                        color = card.optLong("color", 0xFF146C65),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        val sampleCards = listOf(
            NfcCard("transit", "交通卡", "城市通行", "**** 4821", 0xFF146C65),
            NfcCard("access", "门禁卡", "办公楼", "**** 1706", 0xFF345C91),
            NfcCard("member", "会员卡", "日常积分", "**** 9038", 0xFF9A4F3F),
        )
    }
}
