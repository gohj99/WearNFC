package com.gohj99.wearnfc.ui.addcard

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import com.gohj99.wearnfc.R
import com.gohj99.wearnfc.data.IcCardType
import com.gohj99.wearnfc.sync.WatchCommunication
import com.gohj99.wearnfc.utils.LocalGlobalAppState
import kotlinx.coroutines.launch

@Composable
fun AddCardScreen() {
    val appState = LocalGlobalAppState.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val activity = LocalActivity.current ?: return
    val nfcAdapter = remember(activity) { NfcAdapter.getDefaultAdapter(activity) }
    var showOpenOnPhone by rememberSaveable { mutableStateOf(false) }

    // 保留检测到的标签，供 Page2 使用
    var processingTag by remember { mutableStateOf<Tag?>(null) }

    // Page2 读取完成后暂存 dump 数据，待 Page3 命名后再保存
    var pendingDump by remember { mutableStateOf<ByteArray?>(null) }
    var pendingType by remember { mutableStateOf(IcCardType.MIFARE_CLASSIC_1K) }

    // 进入屏幕时清除上一次残留的标签
    LaunchedEffect(Unit) {
        appState.detectedNfcTag.value = null
    }

    // 离开添加流程时兜底关闭 Reader Mode 并清理标签。
    // Page1 → Page2 期间保持开启，保证读取扇区时 NFC 连接仍然可用。
    DisposableEffect(Unit) {
        onDispose {
            try { nfcAdapter?.disableReaderMode(activity) } catch (_: Exception) {}
            appState.detectedNfcTag.value = null
        }
    }

    // 读卡页保持 Reader Mode，其他页面不占用手表 NFC。
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
        } else if (pagerState.currentPage != 2) {
            try { nfcAdapter?.disableReaderMode(activity) } catch (_: Exception) {}
            appState.detectedNfcTag.value = null
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
                        },
                        onPhoneAdd = {
                            WatchCommunication.openPhoneApp(
                                context = activity,
                                onOpened = { showOpenOnPhone = true },
                                onOpenFailed = {
                                    Toast.makeText(activity, R.string.add_card_phone_open_failed, Toast.LENGTH_LONG).show()
                                },
                            )
                        },
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
                        },
                        onRetry = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
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

    val openOnPhoneText = OpenOnPhoneDialogDefaults.text
    val openOnPhoneTextStyle = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = showOpenOnPhone,
        onDismissRequest = { showOpenOnPhone = false },
        curvedText = { openOnPhoneDialogCurvedText(openOnPhoneText, openOnPhoneTextStyle) },
    )
}
