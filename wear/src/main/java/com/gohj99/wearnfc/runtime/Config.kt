package com.gohj99.wearnfc.runtime

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "Config"

private val Context.configDataStore by preferencesDataStore(name = "wearnfc_config")
private val KEY_NFC_MODULE = stringPreferencesKey("nfc_module")
private val KEY_REFRESH_NFC_WAIT_MILLIS = intPreferencesKey("refresh_nfc_wait_millis")
private val LEGACY_KEY_REFRESH_NFC_WAIT_SECONDS = intPreferencesKey("refresh_nfc_wait_seconds")

private const val DEFAULT_REFRESH_NFC_WAIT_MILLIS = 1_000

/**
 * 目前支持的 NFC 模拟模块。
 *
 * 用 sealed class + 嵌套 object 表示，后续新增模块只需加一个嵌套 object。
 */
sealed class NfcModule(val moduleId: String) {
    /** 模块根目录（Magisk/KernelSU 模块目录） */
    val moduleDir: String get() = "/data/adb/modules/$moduleId"

    /** nfcconf 可执行文件路径 */
    val nfcconfBin: String get() = "$moduleDir/nfcconf"

    /** KernelSU/BakaSU 模块：galaxy_watch_nfc_provide */
    object GalaxyWatchNfcProvide : NfcModule("galaxy_watch_nfc_provide")
}

/** 根据 moduleId 字符串反查 [NfcModule]，找不到返回 null。 */
fun nfcModuleFromId(id: String): NfcModule? = when (id) {
    NfcModule.GalaxyWatchNfcProvide.moduleId -> NfcModule.GalaxyWatchNfcProvide
    else -> null
}

/**
 * 全局配置单例。
 *
 * 在 [WearNFC.onCreate] 中通过 [init] 初始化，之后任意位置直接访问此 object。
 * 配置持久化到 DataStore，冷启动后自动恢复。
 */
object Config {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var appContext: Context

    // ── 当前选中的 NFC 模块 ──────────────────────────────────────────────────

    private val _selectedModule = MutableStateFlow<NfcModule>(NfcModule.GalaxyWatchNfcProvide)

    /** 当前选中的 NFC 模拟模块，供 Compose 订阅。 */
    val selectedModule: StateFlow<NfcModule> = _selectedModule.asStateFlow()

    // ── NFC 刷新等待时间 ────────────────────────────────────────────────────

    const val MIN_REFRESH_NFC_WAIT_MILLIS = 50
    const val MAX_REFRESH_NFC_WAIT_MILLIS = 5_000
    const val REFRESH_NFC_WAIT_STEP_MILLIS = 50

    private val _refreshNfcWaitMillis = MutableStateFlow(DEFAULT_REFRESH_NFC_WAIT_MILLIS)

    /** 刷新 NFC 时 disable 与 enable 之间的等待时间（毫秒）。 */
    val refreshNfcWaitMillis: StateFlow<Int> = _refreshNfcWaitMillis.asStateFlow()

    // ── 初始化 ───────────────────────────────────────────────────────────────

    /**
     * 在 Application.onCreate() 中调用一次。
     *
     * @param context 推荐传入 ApplicationContext
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        restoreFromDataStore()
    }

    private fun restoreFromDataStore() {
        scope.launch {
            val prefs = appContext.configDataStore.data.first()
            val savedId = prefs[KEY_NFC_MODULE]
            if (savedId != null) {
                val module = nfcModuleFromId(savedId)
                if (module != null) {
                    _selectedModule.value = module
                } else {
                    Log.w(TAG, "Unknown NFC module ID '$savedId'; using the default module")
                }
            }
            val savedMillis = prefs[KEY_REFRESH_NFC_WAIT_MILLIS]
            val migratedMillis = prefs[LEGACY_KEY_REFRESH_NFC_WAIT_SECONDS]
                ?.toLong()
                ?.times(1_000L)
            _refreshNfcWaitMillis.value =
                (savedMillis?.toLong() ?: migratedMillis ?: DEFAULT_REFRESH_NFC_WAIT_MILLIS.toLong())
                    .coerceIn(MIN_REFRESH_NFC_WAIT_MILLIS.toLong(), MAX_REFRESH_NFC_WAIT_MILLIS.toLong())
                    .toInt()
        }
    }

    // ── 公开 API ─────────────────────────────────────────────────────────────

    /**
     * 切换 NFC 模拟模块，并持久化到 DataStore。
     */
    fun setNfcModule(module: NfcModule) {
        _selectedModule.value = module
        scope.launch {
            appContext.configDataStore.edit { prefs ->
                prefs[KEY_NFC_MODULE] = module.moduleId
            }
        }
    }

    /** 设置刷新 NFC 时的等待时间（毫秒），并持久化到 DataStore。 */
    fun setRefreshNfcWaitMillis(milliseconds: Int) {
        val normalized = milliseconds.coerceIn(MIN_REFRESH_NFC_WAIT_MILLIS, MAX_REFRESH_NFC_WAIT_MILLIS)
        _refreshNfcWaitMillis.value = normalized
        scope.launch {
            appContext.configDataStore.edit { prefs ->
                prefs[KEY_REFRESH_NFC_WAIT_MILLIS] = normalized
            }
        }
    }
}
