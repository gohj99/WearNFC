package com.gohj99.wearnfc.ui.addcard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.gohj99.wearnfc.R
import com.gohj99.wearnfc.data.IcCardType
import com.gohj99.wearnfc.runtime.CardManager
import kotlinx.coroutines.launch

/** 第 3 页：为卡片命名。用户输入自定义名称后点击完成按钮保存卡片。 */
@Composable
internal fun AddCardPage3(
    pendingDump: ByteArray,
    pendingType: IcCardType,
    onFinish: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val defaultName = stringResource(R.string.card_default_name)
    var cardName by remember { mutableStateOf(defaultName) }

    val saveAndFinish: () -> Unit = {
        scope.launch {
            CardManager.addCard(
                name = cardName.trim().ifBlank { defaultName },
                detail = "",
                color = 0xFF146C65L,
                type = pendingType,
                dumpBytes = pendingDump,
            )
            onFinish()
        }
        Unit
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容：标题 + 名称输入框，居中偏上（给底部 EdgeButton 留空间）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.add_card_done_title),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )

            // 自定义名称输入框
            BasicTextField(
                value = cardName,
                onValueChange = { cardName = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveAndFinish() }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        innerTextField()
                    }
                },
            )
        }

        // 底部完成按钮
        EdgeButton(
            onClick = saveAndFinish,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_card_done_button))
        }
    }
}
