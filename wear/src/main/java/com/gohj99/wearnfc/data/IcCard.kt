package com.gohj99.wearnfc.data

import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// MIFARE Classic dump 格式说明：
//   1K：16 扇区 × 4 块 × 16 字节 = 1024 字节
//   4K：32 扇区（各 4 块）+ 8 扇区（各 16 块），共 4096 字节
//   .dump 文件即原始字节数组（binary flat dump）。
//   Block 0 (扇区 0 第 0 块) 前 4 字节 = UID，第 4 字节 = BCC，
//   第 5 字节 = SAK，第 6-7 字节 = ATQA。
// ─────────────────────────────────────────────────────────────────────────────

/** IC 卡类型 */
enum class IcCardType {
    MIFARE_CLASSIC_1K,  // 16 扇区，1024 字节
    MIFARE_CLASSIC_4K,  // 40 扇区，4096 字节
}

/**
 * IC 卡底层数据，含完整 dump 文件路径。
 *
 * [dumpFilePath] 指向 app 私有目录下 `ic/` 子目录中的 .dump 文件。
 * 该文件为 MIFARE Classic 原始 flat dump（1024 或 4096 字节）。
 */
data class IcCardData(
    val id: String,
    val type: IcCardType,
    /** 相对于 app 私有存储的路径，例如 "ic/uuid.dump" */
    val dumpFilePath: String,
    /** UID，4 字节十六进制字符串，如 "09F85906"（从 dump Block 0 解析） */
    val uid: String,
    /** SAK，1 字节，如 0x08 = MIFARE Classic 1K */
    val sak: Byte,
    /** ATQA，2 字节，如 [0x04, 0x00] */
    val atqa: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IcCardData) return false
        return id == other.id
    }
    override fun hashCode() = id.hashCode()
}

/**
 * 供 Compose UI 订阅的卡片展示模型（无底层字节数据）。
 *
 * [id] 与 [IcCardData.id] 对应，可通过 CardManager 查询完整底层数据。
 */
data class IcCardUi(
    val id: String,
    val name: String,
    val detail: String,
    /** 卡片展示用卡号（如 "**** 4821"） */
    val number: String,
    /** 卡片颜色，ARGB Long */
    val color: Long,
    val type: IcCardType,
    /** UID 展示字符串 */
    val uid: String,
)

/** 把 [IcCardData] 和用户填写的展示字段合并成 [IcCardUi] */
fun IcCardData.toUi(name: String, detail: String, number: String, color: Long): IcCardUi =
    IcCardUi(
        id = id,
        name = name,
        detail = detail,
        number = number,
        color = color,
        type = type,
        uid = uid,
    )

/** 生成新卡片 ID */
fun newCardId(): String = UUID.randomUUID().toString()
