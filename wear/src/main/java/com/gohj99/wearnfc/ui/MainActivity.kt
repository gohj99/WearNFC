package com.gohj99.wearnfc.ui

import android.content.ComponentName
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavHostState
import com.gohj99.wearnfc.nfc.WearNfcHostApduService
import com.gohj99.wearnfc.ui.addcard.AddCardScreen
import com.gohj99.wearnfc.ui.main.MainScreen
import com.gohj99.wearnfc.ui.main.NfcModulePage
import com.gohj99.wearnfc.ui.theme.WearNFCTheme
import com.gohj99.wearnfc.utils.GlobalAppState
import com.gohj99.wearnfc.utils.LocalGlobalAppState
import com.gohj99.wearnfc.utils.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private var cardEmulation: CardEmulation? = null
    private var appState: GlobalAppState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 初始化启动页
        val splashScreen = installSplashScreen()
        // 设置停留条件 (Condition)
        splashScreen.setKeepOnScreenCondition {
            // 只要 isLoading.value 是 true，就会一直显示启动图标
            viewModel.isLoading.value
        }
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

                val startupError by viewModel.startupError.collectAsState()

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
                        composable(
                            route = Destinations.CARD_DETAIL_ROUTE,
                            arguments = listOf(navArgument("cardId") { type = NavType.StringType }),
                        ) { backStackEntry ->
                            CardDetailPage(cardId = backStackEntry.arguments?.getString("cardId").orEmpty())
                        }
                    }

                    // 全局错误对话框，覆盖在所有页面之上
                    ErrorDialog(
                        error = startupError,
                        onDismiss = viewModel::dismissStartupError,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTask 保证单实例；NFC tag 由 AddCardScreen Reader Mode 直接读取，此处不处理。
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
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to set the preferred NFC service", e)
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
