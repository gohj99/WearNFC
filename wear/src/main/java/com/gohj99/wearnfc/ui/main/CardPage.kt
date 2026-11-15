package com.gohj99.wearnfc.ui.main

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.gohj99.wearnfc.R
import com.gohj99.wearnfc.data.IcCardUi
import com.gohj99.wearnfc.runtime.CardManager
import kotlin.math.sqrt

/** 卡片切换状态，控制顶部状态文字的显示。 */
private enum class SwitchState { Applying, Success, Error }

/**
 * 单张卡片页。
 *
 * 滑动到此页（[isSelected] = true）时立即切换到对应卡片，无需点击。
 * 顶部状态文字：
 *  - 切换中 → 显示"应用中…"
 *  - 成功 → 隐藏（不显示任何文字）
 *  - 失败 → 显示"应用失败"
 */
@Composable
internal fun CardPage(
    card: IcCardUi,
    index: Int,
    count: Int,
    isSelected: Boolean,
) {
    var switchState by remember(card.id) { mutableStateOf(SwitchState.Applying) }

    LaunchedEffect(card.id, isSelected) {
        if (isSelected) {
            switchState = SwitchState.Applying
            CardManager.switchToCard(card.id) { success ->
                switchState = if (success) SwitchState.Success else SwitchState.Error
            }
        }
    }

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
            // ── 顶部状态文字区域（固定高度，避免卡片位移）──────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when (switchState) {
                    SwitchState.Applying -> Text(
                        text = stringResource(R.string.card_switch_applying),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    SwitchState.Error -> Text(
                        text = stringResource(R.string.card_switch_error),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    SwitchState.Success -> { /* 成功：不显示任何文字 */ }
                }
            }

            // ── 卡片（高度 = 2·safeD，四角恰好在圆形边界内）────────────────────
            Box(
                modifier = Modifier
                    .height(cardHeightDp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(card.color),
                                Color(card.color).copy(alpha = 0.78f),
                                Color(0xFF171B1C),
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
                    .padding(18.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // NFC 类型标签（右对齐）
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            text = "NFC",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }

                    // 用户自定义卡片名称
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 下方留白
            Box(modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}
