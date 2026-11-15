package com.gohj99.wearnfc.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavHostState
import com.gohj99.wearnfc.runtime.CardManager
import com.gohj99.wearnfc.ui.main.MainScreen
import com.gohj99.wearnfc.ui.main.NfcModulePage
import com.gohj99.wearnfc.ui.theme.WearNFCTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearNFCTheme {
                val navController = rememberSwipeDismissableNavController()
                val navHostState = rememberSwipeDismissableNavHostState()

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

                CompositionLocalProvider(LocalNavController provides navController) {
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
}
