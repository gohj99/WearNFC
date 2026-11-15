package com.gohj99.wearnfc.runtime

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gohj99.wearnfc.data.IcCardData
import com.gohj99.wearnfc.data.IcCardType
import com.gohj99.wearnfc.data.IcCardUi
import com.gohj99.wearnfc.data.newCardId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
private const val ORIGINAL_CARD_ID = "__original__"

/** 持久化的卡片元数据（UI 字段 + dump 文件路径），不含底层字节。 */
private data class CardMeta(
    val id: String,
    val name: String,
    val detail: String,
    val number: String,
    val color: Long,
    val type: IcCardType,
    val dumpFilePath: String,
    val uid: String,
    val sak: Byte,
    val atqa: String,
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

    /** IC dump 文件存放目录（app 私有 filesDir/ic），init 后赋值 */
    private lateinit var icDir: File

    // ── 可用性 ───────────────────────────────────────────────────────────────

    private val _isAvailable = MutableStateFlow(false)

    /**
     * 当前模块是否可用。
     *
     * 为 true 需同时满足：
     *  - 设备有 root（`su` 可用）
     *  - 当前选中模块（见 [Config]）的 `nfcconf status` 能正常输出
     */
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

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
        Log.i(TAG, "CardManager 初始化完成")
        initialize()
    }

    private fun initialize() {
        scope.launch {
            // 1. 从 DataStore 恢复卡片元数据与激活状态
            restoreCards()
            // 2. 启动时记录"原始"配置文件路径
            refreshOriginalPaths()
            // 3. 检测 root + 模块可用性
            checkAvailability()
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
                Log.w(TAG, "解析卡片元数据失败：${it.message}")
                emptyList()
            }
        }
        _cardsMeta.value = metaList
        refreshCardsUi()

        val activeId = prefs[KEY_ACTIVE_CARD] ?: ORIGINAL_CARD_ID
        _activeCardId.value = activeId
        Log.i(TAG, "恢复卡片：${metaList.size} 张，激活=$activeId")
    }

    private fun refreshCardsUi() {
        val uiList = _cardsMeta.value.map { meta ->
            IcCardUi(
                id = meta.id,
                name = meta.name,
                detail = meta.detail,
                number = meta.number,
                color = meta.color,
                type = meta.type,
                uid = meta.uid,
            )
        }
        _cards.value = uiList
    }

    // ── root & 模块检测 ──────────────────────────────────────────────────────

    /**
     * 检测当前模块可用性，更新 [isAvailable]。
     *
     * 流程：
     * 1. 检测 root（直接命令行 `su`）
     * 2. 读取 [Config] 当前模块
     * 3. 执行 `nfcconf status`，有输出则可用，否则不可用
     */
    suspend fun checkAvailability() = withContext(Dispatchers.IO) {
        Log.i(TAG, "开始检测可用性…")
        val module = Config.selectedModule.value
        Log.d(TAG, "当前模块：${module.moduleId}")

        val hasRoot = checkRoot()
        if (!hasRoot) {
            Log.w(TAG, "无 root，isAvailable=false")
            _isAvailable.value = false
            return@withContext
        }

        // 执行 nfcconf status
        val statusCmd = "${module.nfcconfBin} status"
        Log.d(TAG, "执行：su -c '$statusCmd'")
        val result = runSuCommand(statusCmd)
        if (result.success && result.stdout.isNotBlank()) {
            Log.i(TAG, "nfcconf status 成功，模块可用")
            // 顺便从 status 输出解析原始文件路径
            parseStatusPaths(result.stdout)
            _isAvailable.value = true
        } else {
            Log.w(TAG, "nfcconf status 无输出或失败，isAvailable=false（exit=${result.exitCode}）")
            _isAvailable.value = false
        }
    }

    /** 直接命令行 `su` 检测 root。 */
    private fun checkRoot(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val ok = p.waitFor() == 0
        Log.d(TAG, "checkRoot: su -c id 退出码=${p.waitFor()} -> $ok")
        ok
    } catch (e: Exception) {
        Log.w(TAG, "checkRoot 异常：${e.message}")
        false
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
                Log.d(TAG, "解析到原始 routing xml：${OriginalPaths.routingXml}")
            } else if (trimmed.startsWith("ORIGINAL_BACKUP=")) {
                OriginalPaths.originalConf = trimmed.substringAfter('=').trim()
                Log.d(TAG, "解析到原始 conf：${OriginalPaths.originalConf}")
            }
        }
    }

    // ── 卡片 CRUD ────────────────────────────────────────────────────────────

    /**
     * 加入一张卡片。
     *
     * @param name 卡片名称
     * @param detail 副标题
     * @param number 展示用卡号
     * @param color 卡片颜色（ARGB Long）
     * @param type IC 卡类型
     * @param dumpBytes 完整 dump 字节数组（1024 或 4096 字节）
     * @return 新卡片 ID；失败返回 null
     */
    suspend fun addCard(
        name: String,
        detail: String,
        number: String,
        color: Long,
        type: IcCardType,
        dumpBytes: ByteArray,
    ): String? = withContext(Dispatchers.IO) {
        val cardId = newCardId()
        val dumpFile = File(icDir, "$cardId.dump")
        try {
            dumpFile.writeBytes(dumpBytes)
            Log.d(TAG, "写入 dump 文件：${dumpFile.absolutePath} (${dumpBytes.size} bytes)")
        } catch (e: IOException) {
            Log.e(TAG, "写入 dump 文件失败：${e.message}")
            return@withContext null
        }

        // 从 dump Block 0 解析 UID/SAK/ATQA
        val parsed = parseDumpHeader(dumpBytes, type)
        if (parsed == null) {
            Log.e(TAG, "dump 文件头部解析失败")
            dumpFile.delete()
            return@withContext null
        }
        val (uid, sak, atqa) = parsed
        Log.i(TAG, "解析 dump：uid=$uid, sak=0x${"%02X".format(sak)}, atqa=${atqa.joinToString("") { "%02X".format(it) }}")

        val meta = CardMeta(
            id = cardId,
            name = name,
            detail = detail,
            number = number,
            color = color,
            type = type,
            dumpFilePath = "ic/$cardId.dump",
            uid = uid,
            sak = sak,
            atqa = atqa.joinToString("") { "%02X".format(it) },
        )

        val newList = _cardsMeta.value + meta
        _cardsMeta.value = newList
        refreshCardsUi()
        persistCards()
        Log.i(TAG, "添加卡片：$cardId（${newList.size} 张）")
        cardId
    }

    /**
     * 删除一张卡片。若删除的是当前激活卡片，自动回退到"原始"。
     */
    suspend fun removeCard(cardId: String) = withContext(Dispatchers.IO) {
        val meta = _cardsMeta.value.firstOrNull { it.id == cardId } ?: run {
            Log.w(TAG, "删除卡片：未找到 id=$cardId")
            return@withContext
        }
        // 删除 dump 文件
        val dumpFile = File(appContext.filesDir, meta.dumpFilePath)
        if (dumpFile.exists() && !dumpFile.delete()) {
            Log.w(TAG, "删除 dump 文件失败：${dumpFile.absolutePath}")
        }
        val newList = _cardsMeta.value.filterNot { it.id == cardId }
        _cardsMeta.value = newList
        refreshCardsUi()
        persistCards()
        Log.i(TAG, "删除卡片：$cardId（剩余 ${newList.size} 张）")

        // 若删除的是当前激活卡片，回退到原始
        if (_activeCardId.value == cardId) {
            Log.i(TAG, "删除的是激活卡片，回退到原始")
            switchToCard(ORIGINAL_CARD_ID)
        }
    }

    /**
     * 更新卡片信息（不修改 dump 文件）。
     */
    suspend fun updateCard(
        cardId: String,
        name: String? = null,
        detail: String? = null,
        number: String? = null,
        color: Long? = null,
    ) = withContext(Dispatchers.IO) {
        val list = _cardsMeta.value.toMutableList()
        val idx = list.indexOfFirst { it.id == cardId }
        if (idx < 0) {
            Log.w(TAG, "更新卡片：未找到 id=$cardId")
            return@withContext
        }
        val old = list[idx]
        list[idx] = old.copy(
            name = name ?: old.name,
            detail = detail ?: old.detail,
            number = number ?: old.number,
            color = color ?: old.color,
        )
        _cardsMeta.value = list
        refreshCardsUi()
        persistCards()
        Log.i(TAG, "更新卡片：$cardId")
    }

    /** 查询某张卡片的底层数据（含完整 dump 字节）。 */
    suspend fun getCardData(cardId: String): IcCardData? = withContext(Dispatchers.IO) {
        val meta = _cardsMeta.value.firstOrNull { it.id == cardId } ?: return@withContext null
        val dumpFile = File(appContext.filesDir, meta.dumpFilePath)
        if (!dumpFile.exists()) {
            Log.w(TAG, "dump 文件不存在：${dumpFile.absolutePath}")
            return@withContext null
        }
        val bytes = dumpFile.readBytes()
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

    private suspend fun persistCards() {
        val json = encodeCardMetaList(_cardsMeta.value)
        appContext.cardDataStore.edit { prefs ->
            prefs[KEY_CARDS_META] = json
            prefs[KEY_ACTIVE_CARD] = _activeCardId.value
        }
        Log.d(TAG, "持久化卡片元数据完成")
    }

    private fun encodeCardMetaList(list: List<CardMeta>): String = JSONArray().apply {
        list.forEach { m ->
            put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("detail", m.detail)
                    .put("number", m.number)
                    .put("color", m.color)
                    .put("type", m.type.name)
                    .put("dumpFilePath", m.dumpFilePath)
                    .put("uid", m.uid)
                    .put("sak", m.sak.toInt() and 0xFF)
                    .put("atqa", m.atqa)
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
                        number = o.optString("number"),
                        color = o.optLong("color", 0xFF146C65),
                        type = runCatching { IcCardType.valueOf(o.optString("type")) }.getOrDefault(IcCardType.MIFARE_CLASSIC_1K),
                        dumpFilePath = o.optString("dumpFilePath"),
                        uid = o.optString("uid"),
                        sak = (o.optInt("sak", 0x08) and 0xFF).toByte(),
                        atqa = o.optString("atqa"),
                    )
                )
            }
        }
    }

    // ── 卡片切换 ─────────────────────────────────────────────────────────────

    /**
     * 切换到指定卡片。
     *
     * @param cardId 目标卡片 ID；[ORIGINAL_CARD_ID] 表示还原原始
     */
    suspend fun switchToCard(cardId: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "切换卡片 → $cardId")

        if (cardId == ORIGINAL_CARD_ID) {
            restoreOriginal()
        } else {
            val meta = _cardsMeta.value.firstOrNull { it.id == cardId }
            if (meta == null) {
                Log.w(TAG, "切换失败：未找到卡片 id=$cardId")
                return@withContext
            }
            applyCard(meta)
        }
        _activeCardId.value = cardId
        appContext.cardDataStore.edit { prefs ->
            prefs[KEY_ACTIVE_CARD] = cardId
        }
        Log.i(TAG, "卡片切换完成：$cardId")
    }

    /**
     * 还原"原始"配置。
     * 把软件启动时记录的两个原始文件搬到模块挂载目录：
     *   routing/original.xml  → system/etc/nfc_rule_configs.xml
     *   config/original.conf   → system/vendor/etc/libnfc-nxp.conf
     */
    private suspend fun restoreOriginal() = withContext(Dispatchers.IO) {
        val module = Config.selectedModule.value
        val routingTarget = "${module.moduleDir}/system/etc/nfc_rule_configs.xml"
        val confTarget = "${module.moduleDir}/system/vendor/etc/libnfc-nxp.conf"
        Log.i(TAG, "还原原始：${OriginalPaths.routingXml} → $routingTarget")
        Log.i(TAG, "还原原始：${OriginalPaths.originalConf} → $confTarget")

        // 用 cp 原地覆盖（保留 inode，符合 Magic Mount-rs 要求）
        val r1 = runSuCommand("cp '${OriginalPaths.routingXml}' '$routingTarget'")
        val r2 = runSuCommand("cp '${OriginalPaths.originalConf}' '$confTarget'")
        Log.d(TAG, "cp routing exit=${r1.exitCode} err=${r1.stderr.take(200)}")
        Log.d(TAG, "cp conf exit=${r2.exitCode} err=${r2.stderr.take(200)}")

        refreshNfc()
    }

    /**
     * 应用某张模拟卡：修改 nfc_rule_configs.xml 与 libnfc-nxp.conf，并刷新 NFC。
     */
    private suspend fun applyCard(meta: CardMeta) = withContext(Dispatchers.IO) {
        val module = Config.selectedModule.value
        val routingPath = "${module.moduleDir}/system/etc/nfc_rule_configs.xml"
        val confPath = "${module.moduleDir}/system/vendor/etc/libnfc-nxp.conf"

        // 1) 修改 nfc_rule_configs.xml：把 eSE 路由改为 DH
        Log.i(TAG, "修改 nfc_rule_configs.xml（eSE → DH）：$routingPath")
        val rRouting = rewriteRoutingXml(routingPath)
        if (!rRouting) {
            Log.e(TAG, "修改 routing xml 失败，中止切换")
            return@withContext
        }

        // 2) 修改 libnfc-nxp.conf：UID + 默认路由 + NXP_CORE_CONF / NXP_CORE_RF_FIELD
        Log.i(TAG, "修改 libnfc-nxp.conf：$confPath")
        val rConf = rewriteNxpConf(confPath, meta)
        if (!rConf) {
            Log.e(TAG, "修改 conf 失败，中止切换")
            return@withContext
        }

        // 3) 刷新 NFC 状态
        refreshNfc()
    }

    /**
     * 修改 nfc_rule_configs.xml：route_aid/route_iso_dep/route_tech/route_system_code
     * 的值从 eSE 改为 DH。原文件在挂载目录下，通过 su 原地覆盖（保留 inode）。
     *
     * @return 成功与否
     */
    private suspend fun rewriteRoutingXml(path: String): Boolean = withContext(Dispatchers.IO) {
        // 1. cat 到本地临时文件
        val tmp = File(appContext.cacheDir, "nfc_rule_configs.xml.tmp")
        val cat = runSuCommand("cat '$path'")
        if (!cat.success) {
            Log.e(TAG, "读取 routing xml 失败：${cat.stderr}")
            return@withContext false
        }
        tmp.writeBytes(cat.stdout.toByteArray())

        // 2. 在本地做 sed 替换：eSE -> DH
        // 用 sed -i 会改 inode，这里改用本地读取-替换-写回再 su cat 覆盖回去的方式
        val originalText = tmp.readText()
        val newText = originalText
            .replace("route_aid=\"eSE\"", "route_aid=\"DH\"")
            .replace("route_iso_dep=\"eSE\"", "route_iso_dep=\"DH\"")
            .replace("route_tech=\"eSE\"", "route_tech=\"DH\"")
            .replace("route_system_code=\"eSE\"", "route_system_code=\"DH\"")
        if (newText == originalText) {
            Log.w(TAG, "routing xml 无需替换（可能已经是 DH）")
        }
        tmp.writeText(newText)

        // 3. 通过 su cat 原地覆盖目标文件（保留 inode）
        val pushCmd = "cat '${tmp.absolutePath}' > '$path'"
        val push = runSuCommand(pushCmd)
        tmp.delete()
        if (!push.success) {
            Log.e(TAG, "写回 routing xml 失败：${push.stderr}")
            return@withContext false
        }
        Log.d(TAG, "routing xml 修改完成")
        true
    }

    /**
     * 修改 libnfc-nxp.conf：
     *  - UID 写入 NXP_CORE_CONF 与 NXP_CORE_RF_FIELD 的 LA_NFCID1（0x33）
     *  - 默认路由 0x01 → 0x00（DEFAULT_*_ROUTE）
     *  - LA_BIT_FRAME_SDD (0x30) → 0x04，LA_PLATFORM_CONFIG (0x31) → 0x00
     *
     * 按 [摘要文档] 规则：数组长度需同步更新，不能只插入字节。
     */
    private suspend fun rewriteNxpConf(path: String, meta: CardMeta): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(appContext.cacheDir, "libnfc-nxp.conf.tmp")
        val cat = runSuCommand("cat '$path'")
        if (!cat.success) {
            Log.e(TAG, "读取 conf 失败：${cat.stderr}")
            return@withContext false
        }
        tmp.writeBytes(cat.stdout.toByteArray())

        val uidBytes = hexToBytes(meta.uid)
        val uidLen = uidBytes.size
        if (uidLen !in setOf(4, 7, 10)) {
            Log.e(TAG, "UID 长度不合法：$uidLen")
            return@withContext false
        }

        val originalText = tmp.readText()

        // ── 1) 默认路由 0x01 → 0x00 ──
        var newText = originalText
            .replace("DEFAULT_ROUTE=0x01", "DEFAULT_ROUTE=0x00")
            .replace("DEFAULT_ISODEP_ROUTE=0x01", "DEFAULT_ISODEP_ROUTE=0x00")
            .replace("DEFAULT_MIFARE_CLT_ROUTE=0x01", "DEFAULT_MIFARE_CLT_ROUTE=0x00")
            .replace("DEFAULT_OFFHOST_ROUTE=0x01", "DEFAULT_OFFHOST_ROUTE=0x00")
            .replace("DEFAULT_FELICA_CLT_ROUTE=0x01", "DEFAULT_FELICA_CLT_ROUTE=0x00")
            .replace("DEFAULT_SYS_CODE_ROUTE=0x01", "DEFAULT_SYS_CODE_ROUTE=0x00")

        // ── 2) NXP_CORE_RF_FIELD：追加 LA_NFCID1 ──
        // 原: { 20, 02, 05, 01, A0, 62, 01, 00 }
        // 改: { 20, 02, <len>, 02, A0, 62, 01, 00, 33, <uidLen>, <uid...> }
        val rfFieldPayload = 0x05 + uidLen
        val rfFieldNewInner = buildString {
            append("20, 02, ")
            append("%02X".format(rfFieldPayload))
            append(", 02, A0, 62, 01, 00, 33, ")
            append("%02X".format(uidLen))
            append(", ")
            append(uidBytes.joinToString(", ") { "%02X".format(it) })
        }
        // 匹配原 NXP_CORE_RF_FIELD 块（从 { 到 }，含内部换行/空格）
        val rfFieldRegex = Regex("NXP_CORE_RF_FIELD=\\{[^}]*\\}", RegexOption.DOT_MATCHES_ALL)
        newText = rfFieldRegex.replace(newText) { _ ->
            "NXP_CORE_RF_FIELD={\n    $rfFieldNewInner\n}"
        }

        // ── 3) NXP_CORE_CONF：更新 payload 长度 + 0x30/0x31 + LA_NFCID1 ──
        // 原内层 33,00 → 33,<uidLen>,<uid...>
        // 外层 20,02,33,11 → 20,02,<0x33+uidLen>,11
        val confPayloadNew = 0x33 + uidLen
        // 替换内层 "33, 00" → "33, 04, ..."（只在 NXP_CORE_CONF 块内）
        val confRegex = Regex("NXP_CORE_CONF=\\{[^}]*\\}", RegexOption.DOT_MATCHES_ALL)
        newText = confRegex.replace(newText) { mr ->
            var block = mr.value
            // 外层 payload 长度 0x33 → 0x33+uidLen
            block = block.replace(
                "20, 02, 33, 11,",
                "20, 02, ${"%02X".format(confPayloadNew)}, 11,",
            )
            // LA_BIT_FRAME_SDD 0x30: 08 → 04
            block = block.replace("30, 01, 08,", "30, 01, 04,")
            // LA_PLATFORM_CONFIG 0x31: 03 → 00
            block = block.replace("31, 01, 03,", "31, 01, 00,")
            // LA_NFCID1 0x33: "33, 00" → "33, <len>, <uid>"
            val uidTail = buildString {
                append("33, ")
                append("%02X".format(uidLen))
                append(", ")
                append(uidBytes.joinToString(", ") { "%02X".format(it) })
            }
            block = block.replace("33, 00,", "$uidTail,")
            block
        }

        if (newText == originalText) {
            Log.w(TAG, "conf 无任何变化")
        }
        tmp.writeText(newText)

        // 写回（保留 inode）
        val pushCmd = "cat '${tmp.absolutePath}' > '$path'"
        val push = runSuCommand(pushCmd)
        tmp.delete()
        if (!push.success) {
            Log.e(TAG, "写回 conf 失败：${push.stderr}")
            return@withContext false
        }
        Log.d(TAG, "conf 修改完成")
        true
    }

    /**
     * 刷新 NFC 状态（封包函数）。
     * su -c 'cmd nfc disable-nfc' → sleep 2 → su -c 'cmd nfc enable-nfc'
     */
    private suspend fun refreshNfc() = withContext(Dispatchers.IO) {
        Log.i(TAG, "刷新 NFC 状态…")
        val r1 = runSuCommand("cmd nfc disable-nfc")
        Log.d(TAG, "disable-nfc exit=${r1.exitCode}")
        Thread.sleep(2000)
        val r2 = runSuCommand("cmd nfc enable-nfc")
        Log.d(TAG, "enable-nfc exit=${r2.exitCode}")
        if (r1.success && r2.success) {
            Log.i(TAG, "NFC 已刷新")
        } else {
            Log.w(TAG, "NFC 刷新可能未完全成功")
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
            Log.w(TAG, "dump 字节数不足：${bytes.size}")
            return null
        }
        if (bytes.size != need) {
            Log.w(TAG, "dump 字节数与类型不符：${bytes.size} (期望 $need)，仍尝试解析")
        }
        return try {
            val uid = bytes.copyOfRange(0, 4)
            val sak = bytes[5]
            val atqa = bytes.copyOfRange(6, 8)
            val uidHex = uid.joinToString("") { "%02X".format(it) }
            Triple(uidHex, sak, atqa)
        } catch (e: Exception) {
            Log.e(TAG, "解析 dump 头部异常：${e.message}")
            null
        }
    }

    // ── 启动时记录原始路径 ───────────────────────────────────────────────────

    /**
     * 在软件启动时记录"原始"配置文件路径。
     *
     * 首先用 [checkAvailability] 中 `nfcconf status` 解析；若尚未拿到，则使用模块默认路径。
     * 注意：原始文件路径来自模块 backup 目录，会随设备固件变（因此以 nfcconf status 为准）。
     */
    private suspend fun refreshOriginalPaths() {
        // 默认值已设置；若 status 已解析则跳过
        Log.d(TAG, "原始路径：routing=${OriginalPaths.routingXml}, conf=${OriginalPaths.originalConf}")
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
        Log.e(TAG, "su 命令异常：$command — ${e.message}")
        SuResult(-1, "", e.message ?: "exception")
    }
}

/** 十六进制字符串（如 "09F85906"）转字节数组 */
private fun hexToBytes(hex: String): ByteArray {
    val clean = hex.removePrefix("0x").replace(" ", "")
    return clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
