package com.gohj99.wearnfc.utils

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcA

/**
 * 从 NFC 标签读取完整 MIFARE Classic dump（flat binary，1024 或 4096 字节）。
 *
 * 流程：
 *  1. 从 NfcA 获取 UID / ATQA / SAK
 *  2. 尝试以 MifareClassic 读取，用常见 Key A 逐扇区认证并读块
 *  3. 无法认证的扇区保留为零；连接失败则回退到仅含 UID 头部的最小 1K dump
 *
 * 返回 null 表示连基本 NFC-A 信息都拿不到（非 NFC-A 标签）。
 */
fun readCardDump(tag: Tag): ByteArray? {
    val nfcA = NfcA.get(tag) ?: return null
    val uid = tag.id
    val atqa = nfcA.atqa
    val sak = (nfcA.sak.toInt() and 0xFF).toByte()

    val mfc = MifareClassic.get(tag)
    if (mfc != null) {
        var dump: ByteArray? = null
        try {
            mfc.connect()
            val dumpSize = if (mfc.size == MifareClassic.SIZE_4K) 4096 else 1024
            dump = ByteArray(dumpSize)
            fillBlock0(dump, uid, sak, atqa)
            for (sector in 0 until mfc.sectorCount) {
                if (!tryAuth(mfc, sector)) continue
                val firstBlock = mfc.sectorToBlock(sector)
                val blockCount = mfc.getBlockCountInSector(sector)
                for (b in 0 until blockCount) {
                    val idx = firstBlock + b
                    val off = idx * 16
                    if (off + 16 <= dump.size) {
                        try { mfc.readBlock(idx).copyInto(dump, off) } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {
            // 连接或读取异常；dump 可能为部分填充，仍可使用
        } finally {
            try { mfc.close() } catch (_: Exception) {}
        }
        if (dump != null) return dump
    }
    return buildMinimalDump(uid, sak, atqa)
}

/** 用常见 Key A 尝试认证某扇区。 */
private fun tryAuth(mfc: MifareClassic, sector: Int): Boolean {
    val keys = listOf(
        MifareClassic.KEY_DEFAULT,                               // FF FF FF FF FF FF
        byteArrayOf(0, 0, 0, 0, 0, 0),                          // 全零
        byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), // MAD
                    0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()),
    )
    for (key in keys) {
        if (try { mfc.authenticateSectorWithKeyA(sector, key) } catch (_: Exception) { false }) return true
    }
    return false
}

/** 写入 Block 0 头部：UID(4) + BCC(1) + SAK(1) + ATQA(2)。 */
private fun fillBlock0(dump: ByteArray, uid: ByteArray, sak: Byte, atqa: ByteArray) {
    if (uid.size >= 4 && dump.size >= 8) {
        uid.copyInto(dump, 0, 0, 4)
        dump[4] = (uid[0].toInt() xor uid[1].toInt() xor uid[2].toInt() xor uid[3].toInt()).toByte()
        dump[5] = sak
        if (atqa.size >= 2) { dump[6] = atqa[0]; dump[7] = atqa[1] }
    }
}

/** 构造仅含 UID 头部和默认扇区尾块的最小 1K dump（无法读取完整内存时使用）。 */
private fun buildMinimalDump(uid: ByteArray, sak: Byte, atqa: ByteArray): ByteArray {
    val dump = ByteArray(1024)
    fillBlock0(dump, uid, sak, atqa)
    for (sector in 0 until 16) {
        val off = (sector * 4 + 3) * 16
        for (i in 0..5) dump[off + i] = 0xFF.toByte()          // Key A
        dump[off + 6] = 0xFF.toByte(); dump[off + 7] = 0x07.toByte(); dump[off + 8] = 0x80.toByte()
        for (i in 9..14) dump[off + i] = 0xFF.toByte()         // Key B
    }
    return dump
}
