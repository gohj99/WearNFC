package com.gohj99.wearnfc.utils

import com.gohj99.wearnfc.data.IcCardType

private const val BLOCK_SIZE = 16
private const val ONE_K_BLOCKS = 64
private const val FOUR_K_BLOCKS = 256

/** Parses flat binary dumps, MCT block text, and Flipper NFC text exports. */
fun parseCardDump(bytes: ByteArray): NfcReadResult {
    require(bytes.isNotEmpty()) { "文件为空" }
    require(bytes.size <= 256 * 1024) { "文件过大，无法作为卡片 dump 导入" }

    if (bytes.size == 1024 || bytes.size == 4096) {
        return toReadResult(bytes.copyOf())
    }

    val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    return parseTextDump(text)
}

private fun parseTextDump(text: String): NfcReadResult {
    val indexedBlocks = linkedMapOf<Int, ByteArray>()
    val sequentialBlocks = mutableListOf<ByteArray>()
    val metadata = mutableMapOf<String, String>()
    val blockPattern = Regex("(?i)^Block\\s+(\\d+)\\s*:\\s*(.+)$")
    val metadataPattern = Regex("^([A-Za-z][A-Za-z0-9 _-]*):\\s*(.*?)$")

    text.lineSequence().forEach { sourceLine ->
        val line = sourceLine.substringBefore('#').trim()
        if (line.isEmpty() || line.startsWith("//")) return@forEach

        val blockMatch = blockPattern.matchEntire(line)
        if (blockMatch != null) {
            val index = blockMatch.groupValues[1].toIntOrNull()
                ?: error("dump 中包含无效的数据块编号")
            require(index !in indexedBlocks) { "dump 中的数据块 $index 重复" }
            indexedBlocks[index] = parseBlock(blockMatch.groupValues[2])
            return@forEach
        }

        val metadataMatch = metadataPattern.matchEntire(line)
        if (metadataMatch != null) {
            val key = metadataMatch.groupValues[1].trim().lowercase()
            if (key in SUPPORTED_METADATA) metadata[key] = metadataMatch.groupValues[2].trim()
            return@forEach
        }

        val block = runCatching { parseBlock(line) }.getOrNull()
        if (block != null) sequentialBlocks += block
    }

    require(indexedBlocks.isEmpty() || sequentialBlocks.isEmpty()) {
        "文件不能混用带编号和未编号的数据块"
    }

    if (indexedBlocks.isEmpty() && sequentialBlocks.isEmpty()) {
        val flatHex = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
            .joinToString("")
            .replace(Regex("[\\s,;_-]"), "")
        if (flatHex.length == 1024 * 2 || flatHex.length == 4096 * 2) {
            return toReadResult(parseHex(flatHex))
        }
    }

    val blockCount = if (indexedBlocks.isNotEmpty()) indexedBlocks.size else sequentialBlocks.size
    require(blockCount == ONE_K_BLOCKS || blockCount == FOUR_K_BLOCKS) {
        "文本 dump 必须包含完整的 1K（64 块）或 4K（256 块）数据"
    }

    val type = if (blockCount == FOUR_K_BLOCKS) IcCardType.MIFARE_CLASSIC_4K else IcCardType.MIFARE_CLASSIC_1K
    val declaredType = metadata["mifare classic type"]
        ?: metadata["device type"]
        ?: metadata["type"]
    if (declaredType != null) {
        val declaredIs4K = when {
            Regex("(?i)4\\s*k|4096").containsMatchIn(declaredType) -> true
            Regex("(?i)1\\s*k|1024").containsMatchIn(declaredType) -> false
            else -> null
        }
        require(declaredIs4K == null || declaredIs4K == (type == IcCardType.MIFARE_CLASSIC_4K)) {
            "文件标注的卡片容量与 dump 数据块数量不一致"
        }
    }

    val dump = ByteArray(blockCount * BLOCK_SIZE)
    if (indexedBlocks.isNotEmpty()) {
        for (index in 0 until blockCount) {
            val block = indexedBlocks[index] ?: error("dump 缺少数据块 $index")
            block.copyInto(dump, index * BLOCK_SIZE)
        }
    } else {
        sequentialBlocks.forEachIndexed { index, block -> block.copyInto(dump, index * BLOCK_SIZE) }
    }

    metadata["uid"]?.let { value ->
        val uid = parseHex(value)
        require(uid.size == 4) { "当前手表仅支持 4 字节 UID，文件中的 UID 长度为 ${uid.size} 字节" }
        require(uid.contentEquals(dump.copyOfRange(0, 4))) { "文件中的 UID 与数据块 0 不一致" }
    }
    metadata["sak"]?.let { value ->
        val sak = parseHex(value)
        require(sak.size == 1) { "文件中的 SAK 格式无效" }
        dump[5] = sak[0]
    }
    metadata["atqa"]?.let { value ->
        val atqa = parseHex(value)
        require(atqa.size == 2) { "文件中的 ATQA 格式无效" }
        atqa.copyInto(dump, 6)
    }

    return toReadResult(dump)
}

private fun parseBlock(value: String): ByteArray {
    val compact = value
        .replace(Regex("(?i)0x"), "")
        .replace(Regex("[\\s,;_-]"), "")
    require(compact.length == BLOCK_SIZE * 2 && compact.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
        "每个数据块必须包含 16 个十六进制字节"
    }
    return parseHex(compact)
}

private fun parseHex(value: String): ByteArray {
    val compact = value.replace(Regex("(?i)0x"), "").replace(Regex("[\\s,;_-]"), "")
    require(compact.isNotEmpty() && compact.length % 2 == 0 && compact.all {
        it.isDigit() || it.lowercaseChar() in 'a'..'f'
    }) { "文件中的十六进制数据格式无效" }
    return ByteArray(compact.length / 2) { index ->
        compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private fun toReadResult(dump: ByteArray): NfcReadResult {
    require(dump.size == 1024 || dump.size == 4096) { "仅支持 MIFARE Classic 1K 或 4K dump" }
    val uidBytes = dump.copyOfRange(0, 4)
    val uid = uidBytes.joinToString("") { "%02X".format(it) }
    return NfcReadResult(
        dump = dump,
        type = if (dump.size == 4096) IcCardType.MIFARE_CLASSIC_4K else IcCardType.MIFARE_CLASSIC_1K,
        uid = uid,
        blocksRead = dump.size / BLOCK_SIZE,
        totalBlocks = dump.size / BLOCK_SIZE,
    )
}

private val SUPPORTED_METADATA = setOf("mifare classic type", "device type", "type", "uid", "sak", "atqa")
