package com.gohj99.wearnfc.ui.main

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.PagerScaffoldDefaults
import com.gohj99.wearnfc.runtime.CardManager
import com.gohj99.wearnfc.data.IcCardUi
import com.gohj99.wearnfc.ui.main.settings.SettingsPage
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val cardManager = CardManager
    val cards by cardManager.cards.collectAsState()
    val activeCardId by cardManager.activeCardId.collectAsState()
    // 卡片增删后按当前卡片重建分页，避免旧页码指向另一张卡并意外切卡。
    key(cards.map { it.id }) { CardPager(cards, activeCardId) }
}

@Composable
private fun CardPager(cards: List<IcCardUi>, activeCardId: String) {
    val cardManager = CardManager
    val coroutineScope = rememberCoroutineScope()

    // 初始页：恢复上次激活的卡片位置。
    // remember(Unit) 保证只在 Composable 首次进入组合时计算一次，
    // 避免 cards 列表更新时重置 pager 位置。
    val initialPage = remember { cardManager.initialPagerPage() }

    // 页数 = 卡片数 + 还原页 + 设置页
    val pagerState = rememberPagerState(initialPage = initialPage) { cards.size + 2 }

    // 当前页是否为设置页（最后一页）
    val isOnSettingsPage = pagerState.currentPage == cards.size + 1

    // 手机端切卡时，即使手表界面已经打开，也同步到手表确认的当前卡片。
    LaunchedEffect(activeCardId) {
        if (!isOnSettingsPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(cardManager.initialPagerPage())
        }
    }

    // 返回前台时同步外部（如快捷磁贴）切换后的卡片，设置页保持当前位置。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!isOnSettingsPage) {
            val activePage = cardManager.initialPagerPage()
            if (pagerState.currentPage != activePage) {
                coroutineScope.launch {
                    pagerState.scrollToPage(activePage)
                }
            }
        }
    }

    val focusRequester = remember { FocusRequester() }

    // 切换到非设置页时让 pager 持有旋转焦点；
    // 切换到设置页时不请求焦点，SettingsPage 内的 ScreenScaffold 自行申请。
    LaunchedEffect(isOnSettingsPage) {
        if (!isOnSettingsPage) {
            focusRequester.requestFocus()
        }
    }

    AppScaffold {
        HorizontalPagerScaffold(pagerState = pagerState) {
            HorizontalPager(
                state = pagerState,
                flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(state = pagerState),
                rotaryScrollableBehavior = null,
                modifier = if (!isOnSettingsPage) {
                    Modifier
                        .onRotaryScrollEvent { event ->
                            // 顺时针（正值）→ 下一页；逆时针（负值）→ 上一页
                            if (!pagerState.isScrollInProgress) {
                                coroutineScope.launch {
                                    if (event.verticalScrollPixels > 0) {
                                        pagerState.animateScrollToPage(
                                            (pagerState.currentPage + 1)
                                                .coerceAtMost(pagerState.pageCount - 1)
                                        )
                                    } else {
                                        pagerState.animateScrollToPage(
                                            (pagerState.currentPage - 1).coerceAtLeast(0)
                                        )
                                    }
                                }
                            }
                            true
                        }
                        .focusRequester(focusRequester)
                        .focusable()
                } else {
                    // 设置页：不持有焦点，让 ScreenScaffold 内部处理上下滚动
                    Modifier
                },
            ) { page ->
                AnimatedPage(pageIndex = page, pagerState = pagerState) {
                    val card = cards.getOrNull(page)
                    when {
                        card != null -> CardPage(
                            card = card,
                            index = page,
                            count = cards.size,
                            // isSelected 只表示"这一页正显示在屏幕中央"，
                            // 与 activeCardId 解耦：滑到就立即触发切换
                            isSelected = pagerState.currentPage == page,
                        )
                        page == cards.size -> RestorePage(
                            isSelected = pagerState.currentPage == page,
                        )
                        else -> SettingsPage()
                    }
                }
            }
        }
    }
}
