package com.gohj99.wearnfc.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.gohj99.wearnfc.R
import kotlin.math.sqrt

/**
 * "恢复原始 NFC 配置"页。
 *
 * 在 [MainScreen] 的 pager 中位于卡片之后、设置页之前，模拟未启用时的占位卡片：
 * 中央一张大卡片，中间一道斜杠，标识软件未接管 NFC。
 */
@Composable
internal fun RestorePage() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val r = maxWidth.value / 2f
        val hPad = 14f
        val hw = r - hPad
        val safeD = sqrt(r * r - hw * hw)
        val cardHeightDp = (safeD * 2f).dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = hPad.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 上方留白（与下方对称，卡片垂直居中）
            Box(modifier = Modifier.weight(1f).fillMaxWidth())

            // 卡片（高度 = 2·safeD，四角恰好在圆形边界内）
            Box(
                modifier = Modifier
                    .height(cardHeightDp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2A2F31),
                                Color(0xFF1B1F21),
                                Color(0xFF171B1C),
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(28.dp)),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, 0f),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.restore_card_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.restore_card_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 下方留白
            Box(modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}
