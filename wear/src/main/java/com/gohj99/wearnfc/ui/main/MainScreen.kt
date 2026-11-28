package com.gohj99.wearnfc.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.PagerScaffoldDefaults
import com.gohj99.wearnfc.runtime.CardManager
import com.gohj99.wearnfc.data.IcCardUi
import com.gohj99.wearnfc.ui.Destinations
import com.gohj99.wearnfc.ui.LocalNavController
import com.gohj99.wearnfc.ui.main.settings.SettingsPage
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val cardManager = CardManager
    val isInitialized by cardManager.isInitialized.collectAsState()
    val cards by cardManager.cards.collectAsState()
    val activeCardId by cardManager.activeCardId.collectAsState()
    val requestedCardId by cardManager.requestedCardId.collectAsState()
    val detailOriginCardId = rememberSaveable { mutableStateOf<String?>(null) }
    // 元数据恢复前不能创建空列表的还原页，否则它会覆盖用户真正的切卡请求。
    if (!isInitialized) return
    // 卡片增删后按当前卡片重建分页，避免旧页码指向另一张卡并意外切卡。
    key(cards.map { it.id }) {
        CardPager(
            cards = cards,
            activeCardId = activeCardId,
            requestedCardId = requestedCardId,
            detailOriginCardId = detailOriginCardId,
        )
    }
}

@Composable
private fun CardPager(
    cards: List<IcCardUi>,
    activeCardId: String,
    requestedCardId: String?,
    detailOriginCardId: MutableState<String?>,
) {
    val cardManager = CardManager
    val navController = LocalNavController.current
    val coroutineScope = rememberCoroutineScope()
    val settingsListState = rememberTransformingLazyColumnState()

    // 初始页：恢复上次激活的卡片位置。
    // remember(Unit) 保证只在 Composable 首次进入组合时计算一次，
    // 避免 cards 列表更新时重置 pager 位置。
    val initialPage = remember { cardManager.initialPagerPage() }

    // 页数 = 卡片数 + 还原页 + 设置页
    val pagerState = rememberPagerState(initialPage = initialPage) { cards.size + 2 }

    // 当前页是否为设置页（最后一页）
    val isOnSettingsPage = pagerState.currentPage == cards.size + 1

    // 手机端切卡时，即使手表界面已经打开，也同步到手表确认的当前卡片。
    LaunchedEffect(activeCardId, requestedCardId) {
        if (requestedCardId == null && !isOnSettingsPage && !pagerState.isScrollInProgress) {
            val detailPage = detailOriginCardId.value?.let { cardId ->
                cards.indexOfFirst { it.id == cardId }.takeIf { it >= 0 }
            }
            pagerState.scrollToPage(detailPage ?: cardManager.initialPagerPage())
        }
    }

    // 详情返回时优先恢复打开详情的卡片；其他前台恢复仍同步外部切卡结果。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!isOnSettingsPage && requestedCardId == null) {
            val originCardId = detailOriginCardId.value
            val originPage = originCardId?.let { cardId ->
                cards.indexOfFirst { it.id == cardId }.takeIf { it >= 0 }
            }
            val resumePage = originPage ?: cardManager.initialPagerPage()
            if (pagerState.currentPage != resumePage) {
                coroutineScope.launch {
                    pagerState.scrollToPage(resumePage)
                    if (originCardId != null && detailOriginCardId.value == originCardId) {
                        detailOriginCardId.value = null
                    }
                }
            } else if (originCardId != null) {
                if (detailOriginCardId.value == originCardId) detailOriginCardId.value = null
            }
        }
    }

    AppScaffold {
        HorizontalPagerScaffold(pagerState = pagerState) {
            HorizontalPager(
                state = pagerState,
                flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(state = pagerState),
                rotaryScrollableBehavior = if (isOnSettingsPage) {
                    null
                } else {
                    RotaryScrollableDefaults.snapBehavior(
                        pagerState = pagerState,
                        snapSensitivity = RotaryScrollableDefaults.LowSnapSensitivity,
                    )
                },
            ) { page ->
                AnimatedPage(pageIndex = page, pagerState = pagerState) {
                    val card = cards.getOrNull(page)
                    when {
                        card != null -> CardPage(
                            card = card,
                            index = page,
                            count = cards.size,
                            onOpenDetail = {
                                detailOriginCardId.value = card.id
                                navController?.navigate(Destinations.cardDetail(card.id))
                            },
                            // isSelected 只表示"这一页正显示在屏幕中央"，
                            // 与 activeCardId 解耦：滑到就立即触发切换
                            isSelected = pagerState.currentPage == page,
                        )
                        page == cards.size -> RestorePage(
                            isSelected = pagerState.currentPage == page,
                        )
                        else -> SettingsPage(
                            listState = settingsListState,
                            isActive = isOnSettingsPage,
                        )
                    }
                }
            }
        }
    }
}
