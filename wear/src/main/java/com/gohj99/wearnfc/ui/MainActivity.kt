package com.gohj99.wearnfc.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavHostState
import com.gohj99.wearnfc.ui.main.MainScreen
import com.gohj99.wearnfc.ui.theme.WearNFCTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearNFCTheme {
                val navController = rememberSwipeDismissableNavController()
                val navHostState = rememberSwipeDismissableNavHostState()

                SwipeDismissableNavHost(
                    navController = navController,
                    state = navHostState,
                    startDestination = Destinations.HOME,
                ) {
                    composable(Destinations.HOME) {
                        MainScreen()
                    }
                }
            }
        }
    }
}
