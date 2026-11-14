package com.gohj99.wearnfc.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.PagerScaffoldDefaults
import com.gohj99.wearnfc.data.NfcCardRepository

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val repository = remember(context) { NfcCardRepository(context.applicationContext) }
    val cards by repository.cards.collectAsState(initial = emptyList())
    val pagerState = rememberPagerState(initialPage = 0) { cards.size + 1 }

    AppScaffold {
        HorizontalPagerScaffold(pagerState = pagerState) {
            HorizontalPager(
                state = pagerState,
                flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(state = pagerState),
                rotaryScrollableBehavior = null,
            ) { page ->
                AnimatedPage(pageIndex = page, pagerState = pagerState) {
                    val card = cards.getOrNull(page)
                    if (card != null) {
                        CardPage(
                            card = card,
                            index = page,
                            count = cards.size,
                            isSelected = pagerState.currentPage == page,
                        )
                    } else {
                        SettingsPage()
                    }
                }
            }
        }
    }
}
