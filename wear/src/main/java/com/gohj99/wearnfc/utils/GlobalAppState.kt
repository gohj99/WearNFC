package com.gohj99.wearnfc.utils

import android.nfc.Tag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.MutableStateFlow

// 定义数据中心：这里可以随意增加、修改你需要的参数
class GlobalAppState {
    // 参数: compose全局导航控制器
    var navController: NavHostController? by mutableStateOf(null)

    // 检测到的 NFC 标签；非 null 表示有新卡靠近，消费后应置回 null
    val detectedNfcTag: MutableStateFlow<Tag?> = MutableStateFlow(null)
}

// 创建一个 CompositionLocal，默认抛出错误（强制必须在上层提供）
val LocalGlobalAppState = staticCompositionLocalOf<GlobalAppState> {
    error("No GlobalAppState provided")
}
