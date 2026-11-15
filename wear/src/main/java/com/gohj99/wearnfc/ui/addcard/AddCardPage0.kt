package com.gohj99.wearnfc.ui.addcard

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
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.gohj99.wearnfc.R

/** 第 0 页：选择添加方式。 */
@Composable
internal fun AddCardPage0(onWatchAdd: () -> Unit, onPhoneAdd: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val overscrollEffect = rememberOverscrollEffect()
    val transformationSpec = rememberTransformationSpec()

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
                        text = stringResource(R.string.add_card_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                FilledTonalButton(
                    onClick = { onWatchAdd() },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                ) {
                    Text(stringResource(R.string.add_card_method_watch))
                }
            }
            item {
                FilledTonalButton(
                    onClick = onPhoneAdd,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                ) {
                    Text(stringResource(R.string.add_card_method_phone))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
