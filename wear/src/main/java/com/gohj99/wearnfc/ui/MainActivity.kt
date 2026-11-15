package com.gohj99.wearnfc.ui

import android.content.ComponentName
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import android.util.Log
import com.gohj99.wearnfc.nfc.WearNfcHostApduService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
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
    private var cardEmulation: CardEmulation? = null
    private var appState: GlobalAppState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        cardEmulation = nfcAdapter?.let { CardEmulation.getInstance(it) }
        setContent {
            WearNFCTheme {
                val navController = rememberSwipeDismissableNavController()
                val navHostState = rememberSwipeDismissableNavHostState()

                // 初始化"数据共享区域"
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTask 保证单实例；NFC tag 由 AddCardScreen Reader Mode 直接读取，此处不处理。
        Log.d("MainActivity", "onNewIntent: action=${intent.action}")
    }

    override fun onResume() {
        super.onResume()
        // 注册前台首选 HCE 服务，确保 DH 路由优先于其他支付/HCE App。
        // 不调用 enableReaderMode：读卡器模式与 HCE 卡模拟互斥，
        // Reader Mode 由 AddCardScreen（添加卡片流程）自行开关。
        try {
            cardEmulation?.setPreferredService(
                this,
                ComponentName(this, WearNfcHostApduService::class.java),
            )
            Log.d("MainActivity", "setPreferredService ok")
        } catch (e: Exception) {
            Log.w("MainActivity", "setPreferredService failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // App 完全退出时才注销前台首选服务
        try {
            cardEmulation?.unsetPreferredService(this)
        } catch (_: Exception) {}
    }
}
