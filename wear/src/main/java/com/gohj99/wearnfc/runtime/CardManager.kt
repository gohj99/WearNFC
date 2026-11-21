package com.gohj99.wearnfc.runtime

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gohj99.wearnfc.data.IcCardData
import com.gohj99.wearnfc.data.IcCardType
import com.gohj99.wearnfc.data.IcCardUi
import com.gohj99.wearnfc.data.newCardId
import com.gohj99.wearnfc.sync.WatchSnapshot
import com.gohj99.wearnfc.sync.WearProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

private const val TAG = "CardManager"

// ── 持久化 keys ──────────────────────────────────────────────────────────────

private val Context.cardDataStore by preferencesDataStore(name = "wearnfc_cards")
private val KEY_CARDS_META = stringPreferencesKey("cards_meta")
private val KEY_ACTIVE_CARD = stringPreferencesKey("active_card_id")

// ── 模块内常量 ──────────────────────────────────────────────────────────────

/**
 * "原始" 卡片对应的文件配置。
 *
 * 这些路径在软件启动时从设备读取（见 [CardManager.refreshOriginalPaths]）。
 */
private object OriginalPaths {
    /** 启动时记录的原始 nfc_rule_configs.xml 路径 */
    var routingXml: String = "/data/adb/galaxy_watch_nfc_provide/routing/original.xml"
    /** 启动时记录的原始 libnfc-nxp.conf 路径 */
    var originalConf: String = "/data/adb/galaxy_watch_nfc_provide/config/original.conf"
}

/** "原始" 卡片的特殊 ID，表示未切换任何模拟卡。 */
private const val ORIGINAL_CARD_ID = WearProtocol.ORIGINAL_ID

/** 持久化的卡片元数据（UI 字段 + dump 文件路径），不含底层字节。 */
private data class CardMeta(
    val id: String,
    val name: String,
    val detail: String,
    val color: Long,
    val type: IcCardType,
    val dumpFilePath: String,
    val uid: String,
    val sak: Byte,
    val atqa: String,
    val createdAt: Long,
)

/**
 * 全局卡片管理单例。
 *
 * 职责：
 * 1. 检测 root 与当前 NFC 模块可用性（[isAvailable]）
 * 2. 卡片 CRUD（addCard / removeCard / updateCard）
 * 3. 持久化卡片元数据 + dump 文件到 app 私有目录
 * 4. 提供 [cards] Flow 供 Compose 订阅
 * 5. 卡片切换：原始 / 卡片1 / 卡片2 …（[activeCardId]）
 * 6. 切换时修改 libnfc-nxp.conf 与 nfc_rule_configs.xml，并刷新 NFC 状态
 *
 * 在 [WearNFC.onCreate] 中通过 [init] 初始化。
 */
object CardManager {

    private const val TAG = "CardManager"

    private lateinit var appContext: Context

    // ── 单例依赖 ─────────────────────────────────────────────────────────────

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Unit>()
    private val cardsMutex = Mutex()

    private suspend fun <T> withCards(block: suspend () -> T): T {
        ready.await()
        return withContext(Dispatchers.IO) { cardsMutex.withLock { block() } }
    }

    suspend fun snapshot(): WatchSnapshot {
        ready.await()
        val available = isAvailable.first { it != null } == true
        return withCards {
            WatchSnapshot(_cards.value, _activeCardId.value, available)
        }
    }

    /** IC dump 文件存放目录（app 私有 filesDir/ic），init 后赋值 */
    private lateinit var icDir: File

    // ── 可用性 ───────────────────────────────────────────────────────────────

    private val _isAvailable = MutableStateFlow<Boolean?>(null)

    /**
     * 当前选中模块的目录是否存在；null 表示尚未完成检测。
     *
     * 通过 `su -c "test -d <模块目录>"` 的退出码判断，不依赖 nfcconf 输出。
     */
    val isAvailable: StateFlow<Boolean?> = _isAvailable.asStateFlow()

    // ── 卡片列表 Flow ────────────────────────────────────────────────────────

    /** 当前选中的卡片 ID（[ORIGINAL_CARD_ID] 表示原始） */
    private val _activeCardId = MutableStateFlow(ORIGINAL_CARD_ID)

    /**
     * 当前激活的卡片 ID。值：
     *  - [ORIGINAL_CARD_ID]：原始（未应用任何模拟卡）
     *  - 其他：对应 [IcCardUi.id]
     */
    val activeCardId: StateFlow<String> = _activeCardId.asStateFlow()

    /** "原始"卡片 ID */
    const val ORIGINAL_ID = ORIGINAL_CARD_ID

    /**
     * 返回激活卡片在卡片列表中的索引，供 UI 计算 pager 初始页。
     *
     * 页面布局：[0..cards.size-1] = 卡片页，[cards.size] = 还原页，[cards.size+1] = 设置页。
     *
     * - 激活为某张卡片 → 返回该卡在列表中的下标（即 pager 页码）
     * - 激活为"原始"或卡片未找到 → 返回 [cards.size]（还原页）
     */
    fun initialPagerPage(): Int {
        val id = _activeCardId.value
        if (id == ORIGINAL_CARD_ID) return _cards.value.size
        val idx = _cards.value.indexOfFirst { it.id == id }
        return if (idx >= 0) idx else _cards.value.size
    }

    /** 持久化的卡片元数据（内存缓存） */
    private val _cardsMeta = MutableStateFlow<List<CardMeta>>(emptyList())

    /** 供 Compose 订阅的卡片 UI 列表（不含底层字节）。 */
    private val _cards = MutableStateFlow<List<IcCardUi>>(emptyList())
    val cards: StateFlow<List<IcCardUi>> = _cards.asStateFlow()

    // ── 初始化 ───────────────────────────────────────────────────────────────

    /**
     * 在 Application.onCreate() 中调用一次。
     *
     * @param context 推荐传入 ApplicationContext
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        icDir = File(appContext.filesDir, "ic").apply { mkdirs() }
        initialize()
    }

    private fun initialize() {
        scope.launch {
            // 1. 从 DataStore 恢复卡片元数据与激活状态
            try {
                restoreCards()
                ready.complete(Unit)
            } catch (error: Exception) {
                ready.completeExceptionally(error)
                Log.e(TAG, "Failed to restore saved cards", error)
            }
            // 2. 卡片恢复失败也继续检测模块，让 Activity 的启动页能结束等待
            checkAvailability()
            // 3. 模块存在时读取原始配置路径；读取失败不影响安装检测结果
            if (_isAvailable.value == true) refreshOriginalPaths()
        }
    }

    /** 从 DataStore 恢复卡片元数据与激活卡片 ID。 */
    private suspend fun restoreCards() {
        val prefs = appContext.cardDataStore.data.first()
        val json = prefs[KEY_CARDS_META]
        val metaList: List<CardMeta> = if (json.isNullOrEmpty()) {
            emptyList()
        } else {
            runCatching { decodeCardMetaList(json) }.getOrElse {
                Log.w(TAG, "Failed to parse card metadata: ${it.message}")
                emptyList()
            }
        }
        _cardsMeta.value = metaList
        refreshCardsUi()

        val activeId = prefs[KEY_ACTIVE_CARD] ?: ORIGINAL_CARD_ID
        _activeCardId.value = activeId.takeIf { id -> metaList.any { it.id == id } } ?: ORIGINAL_CARD_ID

        // Rewrite metadata from older versions so removed fields such as the
        // optional display number do not remain in the local JSON.
        val normalizedJson = encodeCardMetaList(metaList)
        if (json != normalizedJson) {
            appContext.cardDataStore.edit { values ->
                values[KEY_CARDS_META] = normalizedJson
                values[KEY_ACTIVE_CARD] = _activeCardId.value
            }
        }
    }

    private fun refreshCardsUi() {
        val uiList = _cardsMeta.value.map { meta ->
            IcCardUi(
                id = meta.id,
                name = meta.name,
                detail = meta.detail,
                color = meta.color,
                type = meta.type,
                uid = meta.uid,
                createdAt = meta.createdAt,
            )
        }
        _cards.value = uiList
    }

    // ── 模块检测 ─────────────────────────────────────────────────────────────

    /**
     * 用一条 su 命令检查当前模块目录，按退出码更新 [isAvailable]。
     * test -d 成功时不会输出内容，因此不能用 stdout 是否为空判断。
     */
    suspend fun checkAvailability() = withContext(Dispatchers.IO) {
        _isAvailable.value = null
        val module = Config.selectedModule.value
        val result = runSuCommand("test -d '${module.moduleDir}'")
        _isAvailable.value = result.success
        if (!result.success) {
            Log.w(TAG, "Module directory check failed for ${module.moduleDir} (exit=${result.exitCode}): ${result.stderr.take(200)}")
        }
    }

    /**
     * 从 `nfcconf status` 输出解析当前模块文件路径。
     *
     * 典型输出包含：
     *   ROUTING_CONFIG_ORIGINAL=/data/adb/galaxy_watch_nfc_provide/routing/original.xml
     *   ORIGINAL_BACKUP=/data/adb/galaxy_watch_nfc_provide/config/original.conf
     */
    private fun parseStatusPaths(statusOutput: String) {
        for (line in statusOutput.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("ROUTING_CONFIG_ORIGINAL=")) {
                OriginalPaths.routingXml = trimmed.substringAfter('=').trim()
            } else if (trimmed.startsWith("ORIGINAL_BACKUP=")) {
                OriginalPaths.originalConf = trimmed.substringAfter('=').trim()
            }
        }
    }

    // ── 卡片 CRUD ────────────────────────────────────────────────────────────

    /**
     * 加入一张卡片。
     *
     * @param name 卡片名称
     * @param detail 备注
     * @param color 卡片颜色（ARGB Long）
     * @param type IC 卡类型
     * @param dumpBytes 完整 dump 字节数组（1024 或 4096 字节）
     * @return 新卡片 ID；失败返回 null
     */
    suspend fun addCard(
        name: String,
        detail: String,
        color: Long,
        type: IcCardType,
        dumpBytes: ByteArray,
        cardId: String = newCardId(),
    ): String? = withCards {
        java.util.UUID.fromString(cardId)
        if (_cardsMeta.value.any { it.id == cardId }) return@withCards cardId
        require(dumpBytes.size == if (type == IcCardType.MIFARE_CLASSIC_4K) 4096 else 1024)
        val dumpFile = File(icDir, "$cardId.dump")
        try {
            dumpFile.writeBytes(dumpBytes)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write card dump file: ${e.message}", e)
            return@withCards null
        }

        // 从 dump Block 0 解析 UID/SAK/ATQA
        val parsed = parseDumpHeader(dumpBytes, type)
        if (parsed == null) {
            dumpFile.delete()
            return@withCards null
        }
        val (uid, sak, atqa) = parsed

        val meta = CardMeta(
            id = cardId,
            name = name,
            detail = detail,
            color = color,
            type = type,
            dumpFilePath = "ic/$cardId.dump",
            uid = uid,
            sak = sak,
            atqa = atqa.joinToString("") { "%02X".format(it) },
            createdAt = System.currentTimeMillis(),
        )

        val newList = _cardsMeta.value + meta
        persistCards(newList)
        _cardsMeta.value = newList
        refreshCardsUi()
        cardId
    }

    /**
     * 删除一张卡片。若删除的是当前激活卡片，自动回退到"原始"。
     */
    suspend fun removeCard(cardId: String): Boolean = withCards {
        val meta = _cardsMeta.value.firstOrNull { it.id == cardId } ?: run {
            return@withCards false
        }
        // 先确认硬件已还原，再删除当前卡；还原失败时保留卡片和激活状态。
        if (_activeCardId.value == cardId && !applySelectionLocked(ORIGINAL_CARD_ID)) {
            return@withCards false
        }
        val newList = _cardsMeta.value.filterNot { it.id == cardId }
        persistCards(newList)
        _cardsMeta.value = newList
        refreshCardsUi()
        // 元数据落盘后再删除 dump，避免保存失败时只剩下无效卡片。
        val dumpFile = File(appContext.filesDir, meta.dumpFilePath)
        if (dumpFile.exists() && !dumpFile.delete()) {
            Log.w(TAG, "Failed to delete card dump file: ${dumpFile.absolutePath}")
        }

        true
    }

    /**
     * 更新卡片信息（不修改 dump 文件）。
     */
    suspend fun updateCard(
        cardId: String,
        name: String? = null,
        detail: String? = null,
        color: Long? = null,
    ): Boolean = withCards {
        val list = _cardsMeta.value.toMutableList()
        val idx = list.indexOfFirst { it.id == cardId }
        if (idx < 0) {
            return@withCards false
        }
        val old = list[idx]
        list[idx] = old.copy(
            name = name ?: old.name,
            detail = detail ?: old.detail,
            color = color ?: old.color,
        )
        persistCards(list)
        _cardsMeta.value = list
        refreshCardsUi()
        true
    }

    /** 查询某张卡片的底层数据（含完整 dump 字节）。 */
    suspend fun getCardData(cardId: String): IcCardData? = withCards {
        val meta = _cardsMeta.value.firstOrNull { it.id == cardId } ?: return@withCards null
        val dumpFile = File(appContext.filesDir, meta.dumpFilePath)
        if (!dumpFile.exists()) {
            Log.w(TAG, "Card dump file is missing: ${dumpFile.absolutePath}")
            return@withCards null
        }
        IcCardData(
            id = meta.id,
            type = meta.type,
            dumpFilePath = meta.dumpFilePath,
            uid = meta.uid,
            sak = meta.sak,
            atqa = meta.atqa.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
        )
    }

    // ── 持久化 ───────────────────────────────────────────────────────────────

    private suspend fun persistCards(cards: List<CardMeta> = _cardsMeta.value) {
        val json = encodeCardMetaList(cards)
        appContext.cardDataStore.edit { prefs ->
            prefs[KEY_CARDS_META] = json
            prefs[KEY_ACTIVE_CARD] = _activeCardId.value
        }
    }

    private fun encodeCardMetaList(list: List<CardMeta>): String = JSONArray().apply {
        list.forEach { m ->
            put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("detail", m.detail)
                    .put("color", m.color)
                    .put("type", m.type.name)
                    .put("dumpFilePath", m.dumpFilePath)
                    .put("uid", m.uid)
                    .put("sak", m.sak.toInt() and 0xFF)
                    .put("atqa", m.atqa)
                    .put("createdAt", m.createdAt)
            )
        }
    }.toString()

    private fun decodeCardMetaList(json: String): List<CardMeta> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    CardMeta(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        detail = o.optString("detail"),
                        color = o.optLong("color", 0xFF146C65),
                        type = runCatching { IcCardType.valueOf(o.optString("type")) }.getOrDefault(IcCardType.MIFARE_CLASSIC_1K),
                        dumpFilePath = o.optString("dumpFilePath"),
                        uid = o.optString("uid"),
                        sak = (o.optInt("sak", 0x08) and 0xFF).toByte(),
                        atqa = o.optString("atqa"),
                        createdAt = o.optLong("createdAt", 0L),
                    )
                )
            }
        }
    }

    // ── 卡片切换 ─────────────────────────────────────────────────────────────

    /**
     * 切换到指定卡片。
     *
     * 若目标卡片与当前激活卡片相同，直接返回（幂等），避免无谓的 NFC 重启。
     * 手动强制重新应用请用 [forceApplyCard]。
     *
     * @param cardId 目标卡片 ID；[ORIGINAL_CARD_ID] 表示还原原始
     * @param onResult 切换结果回调：true = 成功（或已是目标卡片，无需切换），false = 失败
     */
    suspend fun switchToCard(cardId: String, onResult: ((success: Boolean) -> Unit)? = null): Boolean {
        val success = withCards { applySelectionLocked(cardId) }
        onResult?.invoke(success)
        return success
    }

    /**
     * 强制重新应用指定卡片，无视当前激活状态（即使用户点击的是当前卡，也重新写文件 + 刷新 NFC）。
     *
     * 用于卡片点击的"手动刷新"场景：NFC 状态异常时让用户手动重试。
     *
     * @param onResult 切换结果回调：true = 成功，false = 失败
     */
    suspend fun forceApplyCard(cardId: String, onResult: ((success: Boolean) -> Unit)? = null): Boolean {
        val success = withCards { applySelectionLocked(cardId, force = true) }
        onResult?.invoke(success)
        return success
    }

    /** 调用者持有 cardsMutex，避免手机、磁贴和手表 UI 同时修改 NFC 配置。 */
    private suspend fun applySelectionLocked(cardId: String, force: Boolean = false): Boolean {
        if (!force && cardId == _activeCardId.value) return true
        val ok = if (cardId == ORIGINAL_CARD_ID) {
            restoreOriginal()
        } else {
            val meta = _cardsMeta.value.firstOrNull { it.id == cardId } ?: return false
            applyCard(meta)
        }
        if (ok) {
            _activeCardId.value = cardId
            appContext.cardDataStore.edit { prefs ->
                prefs[KEY_ACTIVE_CARD] = cardId
            }
            vibrateOnCardChanged()
        }
        return ok
    }

    /** 成功修改 NFC 卡片配置后给出短暂触觉反馈。 */
    private fun vibrateOnCardChanged() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Vibrator::class.java)
            } ?: return

            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.w(TAG, "Card configuration changed, but vibration feedback is unavailable: ${e.message}", e)
        }
    }

    /**
     * 还原"原始"配置。
     * 把软件启动时记录的两个原始文件搬到模块挂载目录：
     *   routing/original.xml  → system/etc/nfc_rule_configs.xml
     *   config/original.conf   → system/vendor/etc/libnfc-nxp.conf
     *
     * @return 两个 cp 命令均成功则返回 true
     */
    private suspend fun restoreOriginal(): Boolean = withContext(Dispatchers.IO) {
        val module = Config.selectedModule.value
        val routingTarget = "${module.moduleDir}/system/etc/nfc_rule_configs.xml"
        val confTarget = "${module.moduleDir}/system/vendor/etc/libnfc-nxp.conf"

        // 用 cp 原地覆盖（保留 inode，符合 Magic Mount-rs 要求）
        val r1 = runSuCommand("cp '${OriginalPaths.routingXml}' '$routingTarget'")
        val r2 = runSuCommand("cp '${OriginalPaths.originalConf}' '$confTarget'")
        if (!r1.success) Log.e(TAG, "Failed to restore routing configuration (exit=${r1.exitCode}): ${r1.stderr.take(300)}")
        if (!r2.success) Log.e(TAG, "Failed to restore NFC configuration (exit=${r2.exitCode}): ${r2.stderr.take(300)}")

        val ok = r1.success && r2.success
        if (ok) refreshNfc()
        ok
    }

    /**
     * 应用某张模拟卡：修改 nfc_rule_configs.xml 与 libnfc-nxp.conf，并刷新 NFC。
     *
     * 注意：总是从 [OriginalPaths] 中的原始备份文件读取内容再写入目标路径，
     * 避免卡片互切时读到上一次已修改过的文件导致 pattern 匹配失败。
     *
     * @return 两步文件修改均成功则返回 true
     */
    private suspend fun applyCard(meta: CardMeta): Boolean = withContext(Dispatchers.IO) {
        val module = Config.selectedModule.value
        val routingTarget = "${module.moduleDir}/system/etc/nfc_rule_configs.xml"
        val confTarget = "${module.moduleDir}/system/vendor/etc/libnfc-nxp.conf"


        // 1) 总是从原始备份读取，把 eSE 路由改为 DH，写入模块挂载路径
        val rRouting = rewriteRoutingXml(OriginalPaths.routingXml, routingTarget)
        if (!rRouting) {
            Log.e(TAG, "Failed to update routing configuration; card switch aborted")
            return@withContext false
        }

        // 2) 总是从原始备份读取，写入 UID + 默认路由 + NXP_CORE_CONF / NXP_CORE_RF_FIELD
        val rConf = rewriteNxpConf(OriginalPaths.originalConf, confTarget, meta)
        if (!rConf) {
            Log.e(TAG, "Failed to update NFC configuration; card switch aborted")
            return@withContext false
        }

        // 3) 刷新 NFC 状态
        refreshNfc()
        true
    }

    /**
     * 修改 nfc_rule_configs.xml：route_aid/route_iso_dep/route_tech/route_system_code
     * 的值从 eSE 改为 DH。
     *
     * @param sourcePath 原始备份文件路径（从 [OriginalPaths] 取，保证每次从干净状态开始）
     * @param targetPath 写入目标路径（模块挂载目录下的系统文件）
     * @return 成功与否
     */
    private suspend fun rewriteRoutingXml(sourcePath: String, targetPath: String): Boolean = withContext(Dispatchers.IO) {
        // 1. cat 原始备份到本地临时文件
        val tmp = File(appContext.cacheDir, "nfc_rule_configs.xml.tmp")
        val cat = runSuCommand("cat '$sourcePath'")
        if (!cat.success) {
            Log.e(TAG, "Failed to read routing configuration (exit=${cat.exitCode}): ${cat.stderr.take(300)}")
            return@withContext false
        }
        if (cat.stdout.isBlank()) {
            Log.e(TAG, "Routing configuration is empty: $sourcePath")
            return@withContext false
        }
        tmp.writeBytes(cat.stdout.toByteArray())

        // 2. 本地替换 eSE → DH
        val originalText = tmp.readText()
        val hasRouteAid     = "route_aid=\"eSE\""          in originalText
        val hasRouteIsoDep  = "route_iso_dep=\"eSE\""       in originalText
        val hasRouteTech    = "route_tech=\"eSE\""          in originalText
        val hasRouteSysCode = "route_system_code=\"eSE\""   in originalText

        val newText = originalText
            .replace("route_aid=\"eSE\"",         "route_aid=\"DH\"")
            .replace("route_iso_dep=\"eSE\"",      "route_iso_dep=\"DH\"")
            .replace("route_tech=\"eSE\"",         "route_tech=\"DH\"")
            .replace("route_system_code=\"eSE\"",  "route_system_code=\"DH\"")

        if (newText == originalText) {
            Log.w(TAG, "No routing entries were updated; the source may already use DH or have an unsupported format")
        }
        tmp.writeText(newText)

        // 3. 通过 su cat 原地覆盖目标文件（保留 inode，符合 Magic Mount-rs 要求）
        val pushCmd = "cat '${tmp.absolutePath}' > '$targetPath'"
        val push = runSuCommand(pushCmd)
        tmp.delete()
        if (!push.success) {
            Log.e(TAG, "Failed to write routing configuration (exit=${push.exitCode}): ${push.stderr.take(300)}")
            return@withContext false
        }
        true
    }

    /**
     * 修改 libnfc-nxp.conf：
     *  - UID 写入 NXP_CORE_CONF 与 NXP_CORE_RF_FIELD 的 LA_NFCID1（0x33）
     *  - 默认路由 0x01 → 0x00（DEFAULT_*_ROUTE）
     *  - LA_BIT_FRAME_SDD (0x30) → 0x04，LA_PLATFORM_CONFIG (0x31) → 0x00
     *
     * 按 [摘要文档] 规则：数组长度需同步更新，不能只插入字节。
     *
     * @param sourcePath 原始备份文件路径（从 [OriginalPaths] 取，保证每次从干净状态开始）
     * @param targetPath 写入目标路径（模块挂载目录下的系统文件）
     * @param meta 卡片元数据，用于取 UID
     * @return 成功与否
     */
    private suspend fun rewriteNxpConf(sourcePath: String, targetPath: String, meta: CardMeta): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(appContext.cacheDir, "libnfc-nxp.conf.tmp")
        val cat = runSuCommand("cat '$sourcePath'")
        if (!cat.success) {
            Log.e(TAG, "Failed to read NFC configuration (exit=${cat.exitCode}): ${cat.stderr.take(300)}")
            return@withContext false
        }
        if (cat.stdout.isBlank()) {
            Log.e(TAG, "NFC configuration is empty: $sourcePath")
            return@withContext false
        }
        tmp.writeBytes(cat.stdout.toByteArray())

        val uidBytes = hexToBytes(meta.uid)
        val uidLen = uidBytes.size
        if (uidLen !in setOf(4, 7, 10)) {
            Log.e(TAG, "Invalid card UID length: $uidLen")
            return@withContext false
        }

        val originalText = tmp.readText()
        var changedAny = false

        // ── 1) 默认路由 0x01 → 0x00 ──
        val routeKeys = listOf(
            "DEFAULT_ROUTE",
            "DEFAULT_ISODEP_ROUTE",
            "DEFAULT_MIFARE_CLT_ROUTE",
            "DEFAULT_OFFHOST_ROUTE",
            "DEFAULT_FELICA_CLT_ROUTE",
            "DEFAULT_SYS_CODE_ROUTE",
        )
        var newText = originalText
        routeKeys.forEach { key ->
            val from = "$key=0x01"
            val to   = "$key=0x00"
            if (from in newText) {
                newText = newText.replace(from, to)
                changedAny = true
            }
        }

        // ── 2) NXP_CORE_RF_FIELD：追加 LA_NFCID1 ──
        // 原: { 20, 02, 05, 01, A0, 62, 01, 00 }
        // 改: { 20, 02, <len>, 02, A0, 62, 01, 00, 33, <uidLen>, <uid...> }
        // NXP_CORE_RF_FIELD 的 NCI payload 长度：
        //   02(num_params) + A0,62,01,00(4) + 33(uidLen 标记,1) + uidLen(1) + uid(uidLen)
        //   = 7 + uidLen
        // 原始为 0x05(只有 A0 62 一个参数)，新增 LA_NFCID1 参数会多出 tag(33)+len 共 2 字节，
        // 所以必须用 0x07 + uidLen，而非 0x05 + uidLen（否则 HAL 报 NCI command not well formed）。
        val rfFieldPayload = 0x07 + uidLen
        val rfFieldNewInner = buildString {
            append("20, 02, ")
            append("%02X".format(rfFieldPayload))
            append(", 02, A0, 62, 01, 00, 33, ")
            append("%02X".format(uidLen))
            append(", ")
            append(uidBytes.joinToString(", ") { "%02X".format(it) })
        }
        val rfFieldRegex = Regex("NXP_CORE_RF_FIELD=\\{[^}]*\\}", RegexOption.DOT_MATCHES_ALL)
        val rfFieldMatched = rfFieldRegex.containsMatchIn(newText)
        newText = rfFieldRegex.replace(newText) { _ ->
            "NXP_CORE_RF_FIELD={\n    $rfFieldNewInner\n}"
        }
        if (rfFieldMatched) {
            changedAny = true
        } else {
            Log.w(TAG, "NXP_CORE_RF_FIELD block not found; UID was not written to RF_FIELD")
        }

        // ── 3) NXP_CORE_CONF：更新 payload 长度 + 0x30/0x31 + LA_NFCID1 ──
        // 原内层 33,00 → 33,<uidLen>,<uid...>
        // 外层 20,02,33,11 → 20,02,<0x33+uidLen>,11
        val confPayloadNew = 0x33 + uidLen
        val confRegex = Regex("NXP_CORE_CONF=\\{[^}]*\\}", RegexOption.DOT_MATCHES_ALL)
        var confBlockMatched = false
        var payloadLenMatched = false
        var bitFrameMatched = false
        var platformMatched = false
        var nfcid1Matched = false
        newText = confRegex.replace(newText) { mr ->
            confBlockMatched = true
            var block = mr.value
            // 外层 payload 长度 0x33 → 0x33+uidLen
            if ("20, 02, 33, 11," in block) {
                block = block.replace(
                    "20, 02, 33, 11,",
                    "20, 02, ${"%02X".format(confPayloadNew)}, 11,",
                )
                payloadLenMatched = true
            } else {
                Log.w(TAG, "NXP_CORE_CONF payload length pattern was not found")
            }
            // LA_BIT_FRAME_SDD 0x30: 08 → 04
            if ("30, 01, 08," in block) {
                block = block.replace("30, 01, 08,", "30, 01, 04,")
                bitFrameMatched = true
            } else if ("30, 01, 04," !in block) {
                Log.w(TAG, "LA_BIT_FRAME_SDD pattern was not found")
            }
            // LA_PLATFORM_CONFIG 0x31: 03 → 00
            if ("31, 01, 03," in block) {
                block = block.replace("31, 01, 03,", "31, 01, 00,")
                platformMatched = true
            } else if ("31, 01, 00," !in block) {
                Log.w(TAG, "LA_PLATFORM_CONFIG pattern was not found")
            }
            // LA_NFCID1 0x33: "33, 00" → "33, <len>, <uid>"
            val uidTail = buildString {
                append("33, ")
                append("%02X".format(uidLen))
                append(", ")
                append(uidBytes.joinToString(", ") { "%02X".format(it) })
            }
            if ("33, 00," in block) {
                block = block.replace("33, 00,", "$uidTail,")
                nfcid1Matched = true
            } else {
                Log.w(TAG, "LA_NFCID1 pattern was not found; read from the original backup")
            }
            block
        }
        if (confBlockMatched && (payloadLenMatched || bitFrameMatched || platformMatched || nfcid1Matched)) {
            changedAny = true
        }
        if (!confBlockMatched) {
            Log.w(TAG, "NXP_CORE_CONF block not found; UID was not written to CORE_CONF")
        }

        if (!changedAny) {
            Log.w(TAG, "NFC configuration was not changed; no supported patterns were found in the original backup")
        }
        tmp.writeText(newText)

        // 写回（保留 inode）
        val pushCmd = "cat '${tmp.absolutePath}' > '$targetPath'"
        val push = runSuCommand(pushCmd)
        tmp.delete()
        if (!push.success) {
            Log.e(TAG, "Failed to write NFC configuration (exit=${push.exitCode}): ${push.stderr.take(300)}")
            return@withContext false
        }
        true
    }

    /**
     * 刷新 NFC 状态（封包函数）。
     * su -c 'cmd nfc disable-nfc' → 等待配置的时间 → su -c 'cmd nfc enable-nfc'
     */
    private suspend fun refreshNfc() = withContext(Dispatchers.IO) {
        val r1 = runSuCommand("cmd nfc disable-nfc")
        delay(Config.refreshNfcWaitMillis.value.toLong())
        val r2 = runSuCommand("cmd nfc enable-nfc")
        if (!r1.success || !r2.success) {
            Log.w(TAG, "NFC refresh may have failed (disable exit=${r1.exitCode}, enable exit=${r2.exitCode})")
        }
    }

    // ── dump 文件解析 ────────────────────────────────────────────────────────

    /**
     * 从 dump 文件 Block 0 解析 UID / SAK / ATQA。
     *
     * Block 0 布局（MIFARE Classic）：
     *   [0..3] UID (4 字节)
     *   [4]    BCC = UID 异或
     *   [5]    SAK
     *   [6..7] ATQA
     */
    private fun parseDumpHeader(bytes: ByteArray, type: IcCardType): Triple<String, Byte, ByteArray>? {
        val need = if (type == IcCardType.MIFARE_CLASSIC_1K) 1024 else 4096
        if (bytes.size < 8) {
            Log.w(TAG, "Card dump is too short to contain a header: ${bytes.size} bytes")
            return null
        }
        if (bytes.size != need) {
            Log.w(TAG, "Card dump size does not match its card type: got ${bytes.size} bytes, expected $need; attempting to parse")
        }
        return try {
            val uid = bytes.copyOfRange(0, 4)
            val sak = bytes[5]
            val atqa = bytes.copyOfRange(6, 8)
            val uidHex = uid.joinToString("") { "%02X".format(it) }
            Triple(uidHex, sak, atqa)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse card dump header", e)
            null
        }
    }

    // ── 启动时记录原始路径 ───────────────────────────────────────────────────

    /**
     * 在软件启动时记录"原始"配置文件路径。
     *
     * nfcconf status 仅用于解析备份路径，失败时保留默认值，不作为模块是否安装的依据。
     */
    private suspend fun refreshOriginalPaths() = withContext(Dispatchers.IO) {
        val module = Config.selectedModule.value
        val result = runSuCommand("'${module.nfcconfBin}' status")
        if (result.success && result.stdout.isNotBlank()) {
            parseStatusPaths(result.stdout)
        } else {
            Log.w(TAG, "Failed to read original configuration paths; keeping defaults (exit=${result.exitCode}): ${result.stderr.take(300)}")
        }
    }
}

// ── 工具函数 ──────────────────────────────────────────────────────────────────

/** su 命令执行结果 */
private data class SuResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val success: Boolean get() = exitCode == 0
}

/** 执行 `su -c '<command>'` */
private fun runSuCommand(command: String): SuResult {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val code = process.waitFor()
        SuResult(code, stdout, stderr)
    } catch (e: Exception) {
        Log.e(TAG, "Root command failed: $command (${e.message})", e)
        SuResult(-1, "", e.message ?: "exception")
    }
}

/** 十六进制字符串（如 "09F85906"）转字节数组 */
private fun hexToBytes(hex: String): ByteArray {
    val clean = hex.removePrefix("0x").replace(" ", "")
    return clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
