package com.gohj99.wearnfc.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.gohj99.wearnfc.R
import com.gohj99.wearnfc.runtime.Config
import com.gohj99.wearnfc.runtime.NfcModule

/** 所有已知 NFC 模块；新增模块时在此追加一行即可。 */
private val allNfcModules: List<NfcModule> = listOf(
    NfcModule.GalaxyWatchNfcProvide,
)

@Composable
internal fun NfcModulePage() {
    val listState = rememberTransformingLazyColumnState()
    val overscrollEffect = rememberOverscrollEffect()
    val transformationSpec = rememberTransformationSpec()

    val selectedModule by Config.selectedModule.collectAsState()

    AppScaffold {
        ScreenScaffold(
            scrollState = listState,
            overscrollEffect = overscrollEffect,
            modifier = Modifier.fillMaxSize(),
        ) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    ListHeader {
                        Text(
                            text = stringResource(R.string.nfc_module_title),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                allNfcModules.forEach { module ->
                    item {
                        RadioButton(
                            selected = selectedModule == module,
                            onSelect = { Config.setNfcModule(module) },
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                        ) {
                            Text(stringResource(module.displayNameRes))
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

private val NfcModule.displayNameRes: Int
    get() = when (this) {
        NfcModule.GalaxyWatchNfcProvide -> R.string.module_galaxy_watch_nfc_provide
    }
