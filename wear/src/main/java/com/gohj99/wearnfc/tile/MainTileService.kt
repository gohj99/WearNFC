package com.gohj99.wearnfc.tile

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.expression.AppDataKey
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.expression.DynamicDataBuilders
import androidx.wear.protolayout.material3.ButtonDefaults
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.PrimaryLayoutMargins.Companion.DEFAULT_PRIMARY_LAYOUT_MARGIN
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.LABEL_MEDIUM
import androidx.wear.protolayout.material3.Typography.TITLE_LARGE
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders
import com.gohj99.wearnfc.R
import com.gohj99.wearnfc.runtime.CardManager
import com.gohj99.wearnfc.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val RESOURCES_VERSION = "1"

/** 状态 key：存放用户点击后要切换到的目标卡片 ID */
private val KEY_TARGET = AppDataKey<DynamicString>("switch_to")

/** 无操作 Clickable，用于切换过程中禁用按钮 */
private val NOOP_CLICKABLE = ModifiersBuilders.Clickable.Builder().build()

/** 卡片渐变末色，参考 CardPage.kt */
private const val CARD_GRADIENT_DARK = 0xFF171B1C.toInt()
/** "原始"卡片底色（深靛蓝） */
private const val ORIGINAL_CARD_COLOR = 0xFF1A237E.toInt()
/** 底部 edge button 蓝色（Material Blue 800） */
private const val BUTTON_BLUE = 0xFF1565C0.toInt()
private const val BUTTON_LABEL_WHITE = 0xFFFFFFFF.toInt()

class MainTileService : Material3TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override suspend fun MaterialScope.tileResponse(
        requestParams: TileRequest,
    ): TileBuilders.Tile {
        val targetId: String? = requestParams.currentState
            .keyToValueMapping[KEY_TARGET]
            ?.takeIf { it.hasStringValue() }
            ?.stringValue
            ?.takeIf { it.isNotEmpty() }

        if (targetId != null) {
            serviceScope.launch {
                try {
                    CardManager.switchToCard(targetId)
                } finally {
                    getUpdater(this@MainTileService)
                        .requestUpdate(MainTileService::class.java)
                }
            }
        }

        val displayId = targetId ?: CardManager.activeCardId.value
        val cards = CardManager.cards.value
        val isSwitching = targetId != null

        val allIds: List<String> = cards.map { it.id } + listOf(CardManager.ORIGINAL_ID)
        val currentIdx = allIds.indexOf(displayId).let { if (it < 0) 0 else it }
        val nextId = allIds[(currentIdx + 1) % allIds.size]

        val displayName = when {
            cards.isEmpty() -> getString(R.string.tile_no_cards)
            displayId == CardManager.ORIGINAL_ID -> getString(R.string.restore_page_label)
            else -> cards.find { it.id == displayId }?.name
                ?: getString(R.string.restore_page_label)
        }

        val totalCount = allIds.size
        val currentPos = when {
            cards.isEmpty() -> 0
            displayId == CardManager.ORIGINAL_ID -> totalCount
            else -> (cards.indexOfFirst { it.id == displayId }.takeIf { it >= 0 } ?: 0) + 1
        }

        val nextClickable = if (isSwitching) NOOP_CLICKABLE else makeLoadClickable(nextId)
        val openAppClickable = makeLaunchClickable(this@MainTileService)
        // ── 屏幕宽度 → 卡片高度（横向比，宽 > 高，参考信用卡 ~1.65:1）────────
        val screenWidthDp = requestParams.deviceConfiguration.screenWidthDp.toFloat()
        val cardHeightDp = ((screenWidthDp - 28f) / 1.65f).coerceIn(80f, 110f)

        // ── 卡片底色：从卡片数据取，同 CardPage.kt ────────────────────────────
        val cardColorArgb: Int = when {
            cards.isEmpty() || displayId == CardManager.ORIGINAL_ID -> ORIGINAL_CARD_COLOR
            else -> (cards.find { it.id == displayId }?.color ?: 0xFF146C65L).toInt()
        }

        // ── 渐变背景（卡片色 → 暗色，左上至右下） ───────────────────────────
        val cardWidthDp = screenWidthDp - 28f
        val gradientBrush = ColorBuilders.LinearGradient.Builder(
            ColorBuilders.argb(cardColorArgb),
            ColorBuilders.argb(CARD_GRADIENT_DARK),
        )
            .setStartX(dp(0f))
            .setStartY(dp(0f))
            .setEndX(dp(cardWidthDp))
            .setEndY(dp(cardHeightDp))
            .build()

        // ── 蓝色 edge button 配色 ─────────────────────────────────────────────
        val blueEdgeColors = with(ButtonDefaults) { filledButtonColors() }.copy(
            containerColor = BUTTON_BLUE.argb,
            labelColor = BUTTON_LABEL_WHITE.argb,
        )

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(
                    primaryLayout(
                        margins = DEFAULT_PRIMARY_LAYOUT_MARGIN,
                        mainSlot = {
                            if (cards.isEmpty()) {
                                text(
                                    getString(R.string.tile_no_cards).layoutString,
                                    typography = BODY_MEDIUM,
                                )
                            } else {
                                val nameText = text(
                                    displayName.layoutString,
                                    typography = TITLE_LARGE,
                                    maxLines = 2,
                                )
                                val counterText = text(
                                    "$currentPos / $totalCount".layoutString,
                                    typography = LABEL_MEDIUM,
                                )
                                // ── 横向卡片 Box（渐变背景、圆角、边框）──────────
                                LayoutElementBuilders.Box.Builder()
                                    .setWidth(expand())
                                    .setHeight(dp(cardHeightDp))
                                    .setModifiers(
                                        ModifiersBuilders.Modifiers.Builder()
                                            .setBackground(
                                                ModifiersBuilders.Background.Builder()
                                                    .setBrush(gradientBrush)
                                                    .setCorner(
                                                        ModifiersBuilders.Corner.Builder()
                                                            .setRadius(dp(24f))
                                                            .build()
                                                    )
                                                    .build()
                                            )
                                            .setBorder(
                                                ModifiersBuilders.Border.Builder()
                                                    .setColor(ColorBuilders.argb(0x2EFFFFFF))
                                                    .setWidth(dp(1f))
                                                    .build()
                                            )
                                            .setPadding(
                                                ModifiersBuilders.Padding.Builder()
                                                    .setAll(dp(14f))
                                                    .build()
                                            )
                                            .setClickable(openAppClickable)
                                            .build()
                                    )
                                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
                                    .addContent(
                                        LayoutElementBuilders.Column.Builder()
                                            .setWidth(expand())
                                            .setHeight(wrap())
                                            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                                            .addContent(nameText)
                                            .addContent(counterText)
                                            .build()
                                    )
                                    .build()
                            }
                        },
                        bottomSlot = if (cards.isNotEmpty()) {
                            {
                                textEdgeButton(
                                    onClick = nextClickable,
                                    colors = blueEdgeColors,
                                    labelContent = {
                                        text(
                                            getString(
                                                if (isSwitching) R.string.card_switch_applying
                                                else R.string.tile_next
                                            ).layoutString
                                        )
                                    },
                                )
                            }
                        } else null,
                    )
                )
            )
            .build()
    }
}

// ── 辅助函数 ──────────────────────────────────────────────────────────────────

/** 触发卡片切换的 LoadAction Clickable。 */
private fun makeLoadClickable(cardId: String): ModifiersBuilders.Clickable =
    ModifiersBuilders.Clickable.Builder()
        .setOnClick(
            ActionBuilders.LoadAction.Builder()
                .setRequestState(
                    StateBuilders.State.Builder()
                        .addKeyToValueMapping(
                            KEY_TARGET,
                            DynamicDataBuilders.DynamicDataValue.fromString(cardId),
                        )
                        .build()
                )
                .build()
        )
        .build()

/** 打开手表主界面的 Clickable。 */
private fun makeLaunchClickable(context: MainTileService): ModifiersBuilders.Clickable =
    ModifiersBuilders.Clickable.Builder()
        .setOnClick(
            ActionBuilders.launchAction(
                ComponentName(context, MainActivity::class.java),
            )
        )
        .build()

