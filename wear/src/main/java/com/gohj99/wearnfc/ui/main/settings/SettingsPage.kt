package com.gohj99.wearnfc.ui.main.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.gohj99.wearnfc.R
import com.gohj99.wearnfc.ui.Destinations
import com.gohj99.wearnfc.ui.LocalNavController

@Composable
internal fun SettingsPage(
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    isActive: Boolean = true,
) {
    val overscrollEffect = rememberOverscrollEffect()
    val transformationSpec = rememberTransformationSpec()
    val navController = LocalNavController.current

    ScreenScaffold(
        scrollState = listState,
        overscrollEffect = overscrollEffect,
        modifier = Modifier.fillMaxSize(),
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            rotaryScrollableBehavior = if (isActive) {
                RotaryScrollableDefaults.behavior(listState)
            } else {
                null
            },
            overscrollEffect = overscrollEffect,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                ListHeader {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                FilledTonalButton(
                    onClick = { navController?.navigate(Destinations.NFC_MODULE) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                ) {
                    Text(stringResource(R.string.settings_module))
                }
            }
            item {
                FilledTonalButton(
                    onClick = { navController?.navigate(Destinations.ADD_CARD) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                ) {
                    Text(stringResource(R.string.settings_add_card))
                }
            }
            item {
                FilledTonalButton(
                    onClick = { navController?.navigate(Destinations.REFRESH_NFC) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                ) {
                    Text(stringResource(R.string.settings_refresh_nfc))
                }
            }
            item {
                FilledTonalButton(
                    onClick = { navController?.navigate(Destinations.ABOUT) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                ) {
                    Text(stringResource(R.string.settings_about))
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
