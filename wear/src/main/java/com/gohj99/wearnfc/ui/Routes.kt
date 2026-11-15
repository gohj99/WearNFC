package com.gohj99.wearnfc.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavController

object Destinations {
    const val HOME = "home"
    const val NFC_MODULE = "nfc_module"
    const val ABOUT = "about"
}

/** NavController 注入点，由 [MainActivity] 在 NavHost 外提供。 */
val LocalNavController = compositionLocalOf<NavController?> { null }
