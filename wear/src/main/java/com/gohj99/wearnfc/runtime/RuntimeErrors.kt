package com.gohj99.wearnfc.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class RuntimeError(val priority: Int) {
    CardSwitchFailed(0),
    ConfigurationRecoveryFailed(1),
    RootCommandTimeout(2),
}

/** 错误保留到用户确认，后台切卡失败时也不会丢失提示。 */
internal object RuntimeErrors {
    private val _error = MutableStateFlow<RuntimeError?>(null)
    val error = _error.asStateFlow()

    fun report(error: RuntimeError) {
        _error.update { previous ->
            if (previous == null || error.priority >= previous.priority) error else previous
        }
    }

    fun dismiss() {
        _error.value = null
    }
}
