package com.gohj99.wearnfc.ui.addcard

import android.nfc.Tag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import com.gohj99.wearnfc.utils.LocalGlobalAppState
import kotlinx.coroutines.launch

@Composable
fun AddCardScreen() {
    val appState = LocalGlobalAppState.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { 4 }

    // 保留检测到的标签，供 Page2 使用
    var processingTag by remember { mutableStateOf<Tag?>(null) }

    // 进入屏幕时清除上一次残留的标签，离开时停止 NFC 并清理
    LaunchedEffect(Unit) {
        appState.detectedNfcTag.value = null
    }
    DisposableEffect(Unit) {
        onDispose {
            appState.nfcReaderEnabled = false
            appState.detectedNfcTag.value = null
        }
    }

    // 根据当前页面决定是否启用 NFC 读卡
    LaunchedEffect(pagerState.currentPage) {
        appState.nfcReaderEnabled = pagerState.currentPage == 1
    }

    // 检测到 NFC 标签时（在 Page1）→ 立即跳到 Page2
    val detectedTag by appState.detectedNfcTag.collectAsState()
    LaunchedEffect(detectedTag) {
        val tag = detectedTag ?: return@LaunchedEffect
        if (pagerState.currentPage == 1) {
            processingTag = tag
            appState.detectedNfcTag.value = null   // 消费标签
            appState.nfcReaderEnabled = false       // 停止扫描
            pagerState.animateScrollToPage(2)
        }
    }

    AppScaffold {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> AnimatedPage(pageIndex = page, pagerState = pagerState) {
                    AddCardPage0(
                        onWatchAdd = {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        }
                    )
                }
                1 -> AnimatedPage(pageIndex = page, pagerState = pagerState) {
                    AddCardPage1()
                }
                2 -> AnimatedPage(pageIndex = page, pagerState = pagerState) {
                    AddCardPage2(
                        tag = processingTag,
                        onComplete = {
                            coroutineScope.launch { pagerState.animateScrollToPage(3) }
                        }
                    )
                }
                3 -> AnimatedPage(pageIndex = page, pagerState = pagerState) {
                    AddCardPage3(
                        onFinish = {
                            appState.navController?.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
