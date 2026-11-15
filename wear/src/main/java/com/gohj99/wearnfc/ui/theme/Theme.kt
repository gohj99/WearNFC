package com.gohj99.wearnfc.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

@Composable
fun WearNFCTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    MaterialTheme(
        colorScheme = dynamicColorScheme(context) ?: wearColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
