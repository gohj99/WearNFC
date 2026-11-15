package com.gohj99.wearnfc.ui.addcard

import android.app.Activity
import android.nfc.NfcAdapter
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
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import com.gohj99.wearnfc.data.IcCardType
import com.gohj99.wearnfc.utils.LocalGlobalAppState
import kotlinx.coroutines.launch

@Composable
fun AddCardScreen() {
    val appState = LocalGlobalAppState.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val activity = LocalContext.current as Activity
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(activity) }

    // 保留检测到的标签，供 Page2 使用
    var processingTag by remember { mutableStateOf<Tag?>(null) }

    // Page2 读取完成后暂存 dump 数据，待 Page3 命名后再保存
    var pendingDump by remember { mutableStateOf<ByteArray?>(null) }
    var pendingType by remember { mutableStateOf(IcCardType.MIFARE_CLASSIC_1K) }

    // 进入屏幕时清除上一次残留的标签
    LaunchedEffect(Unit) {
        appState.detectedNfcTag.value = null
    }

    // 离开整个 AddCardScreen 时才关闭 Reader Mode 并清理标签。
    // 注意：页内切换（Page1 → Page2）不关闭 Reader Mode，
    // 因为 AddCardPage2 的 readCardDump() 仍需要活跃的 NFC 连接读取扇区数据。
    DisposableEffect(Unit) {
        onDispose {
            try { nfcAdapter?.disableReaderMode(activity) } catch (_: Exception) {}
            appState.detectedNfcTag.value = null
        }
    }

    // Reader Mode 在扫描页（Page 1）开启；导航到其他页面时不主动关闭，
    // 确保 readCardDump() 在 Page 2 仍可连接 Tag。
    // 离开 AddCardScreen 时由上面的 DisposableEffect(Unit) 统一关闭。
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) {
            try {
                nfcAdapter?.enableReaderMode(
                    activity,
                    { tag -> appState.detectedNfcTag.value = tag },
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null,
                )
            } catch (_: Exception) {}
        }
    }

    // 检测到 NFC 标签时（在 Page1）→ 立即跳到 Page2
    val detectedTag by appState.detectedNfcTag.collectAsState()
    LaunchedEffect(detectedTag) {
        val tag = detectedTag ?: return@LaunchedEffect
        if (pagerState.currentPage == 1) {
            processingTag = tag
            appState.detectedNfcTag.value = null   // 消费标签
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
                        onComplete = { dump, type ->
                            pendingDump = dump
                            pendingType = type
                            coroutineScope.launch { pagerState.animateScrollToPage(3) }
                        }
                    )
                }
                3 -> AnimatedPage(pageIndex = page, pagerState = pagerState) {
                    val dump = pendingDump
                    if (dump != null) {
                        AddCardPage3(
                            pendingDump = dump,
                            pendingType = pendingType,
                            onFinish = {
                                appState.navController?.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
