package com.gohj99.wearnfc.runtime

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal interface CardSwitchOperations {
    suspend fun disableNfc(): Boolean
    suspend fun writeConfiguration(): Boolean
    suspend fun restoreConfiguration(): Boolean
    suspend fun enableNfc(): Boolean
    suspend fun commit()
}

internal data class CardSwitchResult(
    val success: Boolean,
    val recovered: Boolean,
    val originalRestored: Boolean = false,
    val cause: Exception? = null,
)

/** 一旦开始关闭 NFC，就完成写入、恢复原始配置和状态提交，不受页面生命周期影响。 */
internal suspend fun performCardSwitch(operations: CardSwitchOperations): CardSwitchResult =
    withContext(NonCancellable) {
        var disabled = false
        var enableAttempted = false
        var cause: Exception? = null
        try {
            disabled = operations.disableNfc()
            if (disabled && operations.writeConfiguration()) {
                enableAttempted = true
                if (operations.enableNfc()) {
                    operations.commit()
                    return@withContext CardSwitchResult(success = true, recovered = true)
                }
            }
        } catch (error: Exception) {
            cause = error
        }

        var originalRestored = false
        val recovered = try {
            // NFC 已重新开启或初次关闭失败时，再尝试关闭后写回原始配置。
            val nfcDisabled = if (disabled && !enableAttempted) true else operations.disableNfc()
            originalRestored = operations.restoreConfiguration()
            val nfcEnabled = originalRestored && operations.enableNfc()
            nfcDisabled && nfcEnabled
        } catch (error: Exception) {
            if (cause == null) cause = error else cause.addSuppressed(error)
            false
        }
        CardSwitchResult(
            success = false,
            recovered = recovered,
            originalRestored = originalRestored,
            cause = cause,
        )
    }
