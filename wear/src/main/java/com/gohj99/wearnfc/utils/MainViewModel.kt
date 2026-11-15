package com.gohj99.wearnfc.utils

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gohj99.wearnfc.runtime.Config
import com.gohj99.wearnfc.ui.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    // 检测结束前保持启动页。
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _startupError = MutableStateFlow<AppError?>(null)
    val startupError = _startupError.asStateFlow()

    val mainPage1FristScrollTop = MutableStateFlow(false)

    init {
        performInitialization()
    }

    private fun performInitialization() {
        viewModelScope.launch {
            try {
                _startupError.value = withContext(Dispatchers.IO) {
                    when {
                        !checkRoot() -> AppError.NoRoot
                        !checkModuleDirectory() -> AppError.NoSupportedModule
                        else -> null
                    }
                }
            } finally {
                // 等后台检测完成，再放行启动页并显示对应错误。
                _isLoading.value = false
            }
        }
    }

    fun dismissStartupError() {
        _startupError.value = null
    }

    private fun checkRoot(): Boolean = try {
        val (exitCode, output) = runSuCommand("id -u")
        exitCode == 0 && output.trim() == "0"
    } catch (error: Exception) {
        Log.w("MainViewModel", "Root access check failed", error)
        false
    }

    private fun checkModuleDirectory(): Boolean = try {
        val module = Config.selectedModule.value
        val (exitCode, _) = runSuCommand("test -d '${module.moduleDir}'")
        exitCode == 0
    } catch (error: Exception) {
        Log.w("MainViewModel", "Module directory check failed", error)
        false
    }

    private fun runSuCommand(command: String): Pair<Int, String> {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        try {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            return process.waitFor() to output
        } finally {
            process.destroy()
        }
    }
}
