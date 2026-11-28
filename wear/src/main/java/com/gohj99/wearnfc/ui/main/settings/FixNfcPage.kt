package com.gohj99.wearnfc.ui.main.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.wear.compose.material3.Text
import com.gohj99.wearnfc.R
import com.gohj99.wearnfc.runtime.KsuNfcProfileFix
import kotlinx.coroutines.launch

@Composable
internal fun FixNfcPage() {
    val context = LocalContext.current
    val listState = rememberTransformingLazyColumnState()
    val overscrollEffect = rememberOverscrollEffect()
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var repaired by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun checkAndFix() {
        if (running) return
        running = true
        error = null
        scope.launch {
            try {
                @Suppress("DEPRECATION")
                val uid = context.packageManager.getApplicationInfo("com.android.nfc", 0).uid
                repaired = KsuNfcProfileFix.repair(context, uid).repaired
            } catch (_: PackageManager.NameNotFoundException) {
                repaired = null
                error = context.getString(R.string.fix_nfc_package_missing)
            } catch (exception: Exception) {
                repaired = null
                error = exception.message ?: exception.javaClass.simpleName
            } finally {
                running = false
            }
        }
    }

    AppScaffold {
        ScreenScaffold(
            scrollState = listState,
            overscrollEffect = overscrollEffect,
            edgeButton = {
                EdgeButton(
                    onClick = ::checkAndFix,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (running) R.string.fix_nfc_working else R.string.fix_nfc_action))
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
                            text = stringResource(R.string.fix_nfc_title),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.fix_nfc_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    )
                }
                item {
                    val currentError = error
                    val statusText = when {
                        running -> stringResource(R.string.fix_nfc_working)
                        currentError != null -> stringResource(R.string.fix_nfc_status_failed, currentError)
                        repaired == true -> stringResource(R.string.fix_nfc_status_fixed)
                        repaired == false -> stringResource(R.string.fix_nfc_status_ok)
                        else -> stringResource(R.string.fix_nfc_status_ready)
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (currentError == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}
