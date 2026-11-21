package com.gohj99.wearnfc.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavController

object Destinations {
    const val HOME = "home"
    const val NFC_MODULE = "nfc_module"
    const val REFRESH_NFC = "refresh_nfc"
    const val ABOUT = "about"
    const val ADD_CARD = "add_card"
    const val CARD_DETAIL = "card_detail"
    const val CARD_DETAIL_ROUTE = "$CARD_DETAIL/{cardId}"

    fun cardDetail(cardId: String): String = "$CARD_DETAIL/$cardId"
}

/** NavController 注入点，由 [MainActivity] 在 NavHost 外提供。 */
val LocalNavController = compositionLocalOf<NavController?> { null }
