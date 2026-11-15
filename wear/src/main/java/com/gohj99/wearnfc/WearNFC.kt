package com.gohj99.wearnfc

import android.app.Application
import com.gohj99.wearnfc.runtime.CardManager
import com.gohj99.wearnfc.runtime.Config

/**
 * Application 入口。
 *
 * 在 onCreate 中按顺序初始化：
 *  1. [Config]（配置单例，不依赖其他单例）
 *  2. [CardManager]（卡片管理，依赖 Config）
 */
class WearNFC : Application() {

    override fun onCreate() {
        super.onCreate()
        Config.init(this)
        CardManager.init(this)
    }
}
