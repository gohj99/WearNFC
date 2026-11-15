package com.gohj99.wearnfc.ui.addcard

import android.nfc.Tag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.Button
import com.gohj99.wearnfc.R
import com.gohj99.wearnfc.data.IcCardType
import com.gohj99.wearnfc.utils.readNfcCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 第 2 页：读取中。在后台读取 dump，完成后将数据回传，不直接保存。 */
@Composable
internal fun AddCardPage2(
    tag: Tag?,
    onComplete: (dumpBytes: ByteArray, type: IcCardType) -> Unit,
    onRetry: () -> Unit,
) {
    var error by remember(tag) { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (error != null) {
                Text(error.orEmpty(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onRetry) { Text(stringResource(R.string.add_card_retry)) }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.add_card_reading_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.add_card_reading_hint),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    LaunchedEffect(tag) {
        if (tag == null) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) { runCatching { readNfcCard(tag) } }
        result.onSuccess { onComplete(it.dump, it.type) }
            .onFailure { error = it.message ?: "NFC read failed" }
    }
}
