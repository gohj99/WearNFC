package com.gohj99.wearnfc.utils

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcA
import com.gohj99.wearnfc.data.IcCardType

data class NfcReadResult(
    val dump: ByteArray,
    val type: IcCardType,
    val uid: String,
    val blocksRead: Int,
    val totalBlocks: Int,
)

/** 与手表的 flat dump 格式一致；明确区分只读到 UID 和读到卡片内存。 */
fun readNfcCard(tag: Tag, onProgress: (Int, Int) -> Unit = { _, _ -> }): NfcReadResult {
    val nfcA = NfcA.get(tag) ?: error("暂不支持此卡片，请使用 NFC-A 卡片")
    val uid = tag.id
    require(uid.size == 4) { "当前手表模块仅支持 4 字节 UID，此卡的 UID 为 ${uid.size} 字节" }
    val mfc = MifareClassic.get(tag)
    require(mfc == null || mfc.size == MifareClassic.SIZE_1K || mfc.size == MifareClassic.SIZE_4K) {
        "暂不支持此 MIFARE Classic 容量"
    }
    val size = if (mfc?.size == MifareClassic.SIZE_4K) 4096 else 1024
    val dump = minimalDump(size)
    var blocksRead = 0
    if (mfc != null) {
        try {
            mfc.connect()
            mfc.timeout = 1_000
            for (sector in 0 until mfc.sectorCount) {
                if (Thread.currentThread().isInterrupted) break
                if (!tryAuth(mfc, sector)) continue
                val first = mfc.sectorToBlock(sector)
                for (block in first until first + mfc.getBlockCountInSector(sector)) {
                    mfc.readBlock(block).copyInto(dump, block * 16)
                    blocksRead++
                    onProgress(blocksRead, size / 16)
                }
            }
        } catch (error: android.nfc.TagLostException) {
            throw IllegalStateException("卡片已移开，请保持贴近手机后重试", error)
        } catch (error: java.io.IOException) {
            throw IllegalStateException("读取失败，请保持卡片贴近手机后重试", error)
        } finally {
            runCatching { mfc.close() }
        }
    }
    // Block 0 使用实际射频参数，不能让读到的厂商块覆盖 SAK / ATQA。
    uid.copyInto(dump, 0)
    dump[4] = uid.reduce { a, b -> (a.toInt() xor b.toInt()).toByte() }
    dump[5] = nfcA.sak.toByte()
    nfcA.atqa.copyInto(dump, 6, 0, 2)
    return NfcReadResult(dump,
        if (size == 4096) IcCardType.MIFARE_CLASSIC_4K else IcCardType.MIFARE_CLASSIC_1K,
        uid.joinToString("") { "%02X".format(it) }, blocksRead, size / 16)
}

/** 保留手表端现有调用入口。 */
fun readCardDump(tag: Tag): ByteArray? = runCatching { readNfcCard(tag).dump }.getOrNull()

private fun tryAuth(mfc: MifareClassic, sector: Int): Boolean {
    val keys = listOf(MifareClassic.KEY_DEFAULT, ByteArray(6),
        byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()))
    return keys.any { key -> mfc.authenticateSectorWithKeyA(sector, key) }
}

private fun minimalDump(size: Int): ByteArray = ByteArray(size).also { dump ->
    val sectors = if (size == 4096) 40 else 16
    for (sector in 0 until sectors) {
        val trailer = if (sector < 32) sector * 4 + 3 else 128 + (sector - 32) * 16 + 15
        val offset = trailer * 16
        for (i in 0..5) dump[offset + i] = 0xFF.toByte()
        dump[offset + 6] = 0xFF.toByte()
        dump[offset + 7] = 0x07
        dump[offset + 8] = 0x80.toByte()
        dump[offset + 9] = 0x69
        for (i in 10..15) dump[offset + i] = 0xFF.toByte()
    }
}
