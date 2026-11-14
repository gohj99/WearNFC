package com.gohj99.wearnfc.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.gohj99.wearnfc.data.NfcCard

@Composable
internal fun CardPage(
    card: NfcCard,
    index: Int,
    count: Int,
    isSelected: Boolean,
) {
    LaunchedEffect(card.id, isSelected) {
        if (isSelected) {
            // TODO: 在这里处理当前卡片选中后的卡模拟逻辑。
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "NFC 卡片",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
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
                .clickable(role = Role.Button) {
                    // TODO: 在这里处理当前卡片的点击操作。
                }
                .padding(18.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1} / $count",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.78f),
                    )
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

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = card.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = card.number,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) { dot ->
                Box(
                    modifier = Modifier
                        .size(if (dot == index) 6.dp else 4.dp)
                        .clip(CircleShape)
                        .background(
                            if (dot == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                )
            }
        }
    }
}
