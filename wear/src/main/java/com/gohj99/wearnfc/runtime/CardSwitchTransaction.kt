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
    val cause: Exception? = null,
)

/** 一旦开始关闭 NFC，就完成写入、恢复和状态提交，不受页面生命周期影响。 */
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

        val recovered = try {
            if (!disabled) {
                // 关闭命令失败也可能已产生副作用；文件尚未修改，只恢复 NFC。
                operations.enableNfc()
            } else {
                // 开启命令可能部分成功，回滚前必须重新关闭。
                (!enableAttempted || operations.disableNfc()) &&
                    operations.restoreConfiguration() && operations.enableNfc()
            }
        } catch (error: Exception) {
            if (cause == null) cause = error else cause.addSuppressed(error)
            false
        }
        CardSwitchResult(success = false, recovered = recovered, cause = cause)
    }
