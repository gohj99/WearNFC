package com.gohj99.wearnfc.ui

import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavHostState
import com.gohj99.wearnfc.runtime.CardManager
import com.gohj99.wearnfc.ui.addcard.AddCardScreen
import com.gohj99.wearnfc.ui.main.MainScreen
import com.gohj99.wearnfc.ui.main.NfcModulePage
import com.gohj99.wearnfc.ui.theme.WearNFCTheme
import com.gohj99.wearnfc.utils.GlobalAppState
import com.gohj99.wearnfc.utils.LocalGlobalAppState

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var appState: GlobalAppState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            WearNFCTheme {
                val navController = rememberSwipeDismissableNavController()
                val navHostState = rememberSwipeDismissableNavHostState()

                // 初始化“数据共享区域”
                val appState = remember { GlobalAppState() }
                appState.navController = navController
                this@MainActivity.appState = appState

                // CardManager.isAvailable 由 WearNFC.onCreate 触发的 checkAvailability() 写入
                val isAvailable by CardManager.isAvailable.collectAsState()

                // 当前要显示的错误；置 null 关闭对话框
                var currentError by rememberSaveable { mutableStateOf<AppError?>(null) }

                // isAvailable 变为 false（且已完成首次检测）时弹出 NoRoot 提示
                LaunchedEffect(isAvailable) {
                    if (!isAvailable) {
                        currentError = AppError.NoRoot
                    } else {
                        // 可用后清除之前的 root 错误
                        if (currentError is AppError.NoRoot) currentError = null
                    }
                }

                // NFC 前台读卡模式：nfcReaderEnabled 为 true 时启用，离开时停用
                val nfcReaderEnabled = appState.nfcReaderEnabled
                DisposableEffect(nfcReaderEnabled) {
                    if (nfcReaderEnabled) enableNfcReader()
                    onDispose {
                        try { nfcAdapter?.disableReaderMode(this@MainActivity) }
                        catch (e: Exception) { Log.e("MainActivity", "NFC disable", e) }
                    }
                }

                CompositionLocalProvider(
                    LocalGlobalAppState provides appState,
                    LocalNavController provides navController,
                ) {
                    SwipeDismissableNavHost(
                        navController = navController,
                        state = navHostState,
                        startDestination = Destinations.HOME,
                    ) {
                        composable(Destinations.HOME) {
                            MainScreen()
                        }
                        composable(Destinations.NFC_MODULE) {
                            NfcModulePage()
                        }
                        composable(Destinations.ABOUT) {
                            AboutPage()
                        }
                        composable(Destinations.ADD_CARD) {
                            AddCardScreen()
                        }
                    }

                    // 全局错误对话框，覆盖在所有页面之上
                    ErrorDialog(
                        error = currentError,
                        onDismiss = { currentError = null },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (appState?.nfcReaderEnabled == true) enableNfcReader()
    }

    override fun onPause() {
        super.onPause()
        try { nfcAdapter?.disableReaderMode(this) } catch (_: Exception) {}
    }

    private fun enableNfcReader() {
        try {
            nfcAdapter?.enableReaderMode(
                this,
                { tag -> appState?.detectedNfcTag?.value = tag },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null,
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "NFC enableReaderMode failed", e)
        }
    }
}
