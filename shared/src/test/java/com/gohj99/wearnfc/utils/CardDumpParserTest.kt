package com.gohj99.wearnfc.utils

import com.gohj99.wearnfc.data.IcCardType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CardDumpParserTest {
    @Test
    fun parsesFlatBinaryDump() {
        val dump = sampleDump(1024)

        val result = parseCardDump(dump)

        assertEquals(IcCardType.MIFARE_CLASSIC_1K, result.type)
        assertEquals("09112233", result.uid)
        assertArrayEquals(dump, result.dump)
        assertEquals(64, result.blocksRead)
    }

    @Test
    fun parsesMctTextDump() {
        val dump = sampleDump(1024)
        val text = dump.asList().chunked(16).joinToString("\n") { block ->
            block.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        }

        val result = parseCardDump(text.toByteArray(Charsets.UTF_8))

        assertEquals(IcCardType.MIFARE_CLASSIC_1K, result.type)
        assertEquals("09112233", result.uid)
        assertArrayEquals(dump, result.dump)
    }

    @Test
    fun parsesFlipperNfcBlockExport() {
        val dump = sampleDump(1024)
        val blocks = dump.asList().chunked(16).mapIndexed { index, block ->
            "Block $index: ${block.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }}"
        }
        val text = listOf(
            "Filetype: Flipper NFC device",
            "Version: 4",
            "Device type: Mifare Classic 1K",
            "UID: 09 11 22 33",
            "SAK: 08",
            "ATQA: 04 00",
        ).plus(blocks).joinToString("\n")

        val result = parseCardDump(text.toByteArray(Charsets.UTF_8))

        assertEquals("09112233", result.uid)
        assertEquals(0x08.toByte(), result.dump[5])
        assertArrayEquals(byteArrayOf(0x04, 0x00), result.dump.copyOfRange(6, 8))
    }

    @Test
    fun rejectsIncompleteTextDump() {
        val text = sampleDump(1024).asList().take(63).chunked(16).joinToString("\n") { block ->
            block.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        }

        assertThrows(IllegalArgumentException::class.java) {
            parseCardDump(text.toByteArray(Charsets.UTF_8))
        }
    }

    private fun sampleDump(size: Int) = ByteArray(size) { (it * 17).toByte() }.apply {
        this[0] = 0x09
        this[1] = 0x11
        this[2] = 0x22
        this[3] = 0x33
        this[4] = (0x09 xor 0x11 xor 0x22 xor 0x33).toByte()
        this[5] = (if (size == 4096) 0x18 else 0x08).toByte()
        this[6] = 0x04.toByte()
        this[7] = 0x00.toByte()
    }
}
