package com.gohj99.wearnfc.ui.main.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Text
import com.gohj99.wearnfc.R
import com.gohj99.wearnfc.runtime.Config
import com.gohj99.wearnfc.ui.LocalNavController

/** 设置 NFC 刷新时 disable 与 enable 之间的等待时间。 */
@Composable
internal fun RefreshNfcPage() {
    val navController = LocalNavController.current
    val listState = rememberTransformingLazyColumnState()
    val overscrollEffect = rememberOverscrollEffect()
    val waitMillis by Config.refreshNfcWaitMillis.collectAsState()
    var draftMillis by remember(waitMillis) { mutableStateOf(waitMillis) }

    fun save() {
        Config.setRefreshNfcWaitMillis(draftMillis)
        navController?.popBackStack()
    }

    AppScaffold {
        ScreenScaffold(
            scrollState = listState,
            overscrollEffect = overscrollEffect,
            edgeButton = {
                EdgeButton(
                    onClick = ::save,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.refresh_nfc_save))
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ListHeader {
                        Text(
                            text = stringResource(R.string.refresh_nfc_title),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.refresh_nfc_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    )
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.refresh_nfc_wait_value, draftMillis),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Slider(
                            value = draftMillis,
                            onValueChange = { draftMillis = it },
                            valueProgression = Config.MIN_REFRESH_NFC_WAIT_MILLIS..
                                Config.MAX_REFRESH_NFC_WAIT_MILLIS step Config.REFRESH_NFC_WAIT_STEP_MILLIS,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.refresh_nfc_range),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
