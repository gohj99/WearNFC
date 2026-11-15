package com.gohj99.wearnfc.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * 前台首选 HCE 服务。
 *
 * 本 App 的 Mifare Classic 卡模拟由 root 内核模块在 RF 层完成，Android HCE 通道
 * 不直接处理 APDU。此服务的作用是：
 *  - 向系统注册 HostApduService，使 [android.nfc.cardemulation.CardEmulation.setPreferredService]
 *    可以把本 App 设置为前台首选服务；
 *  - 确保 NFC 路由表中"设备主机（DH）"路由对本 App 可用，
 *    避免其他支付/HCE 应用抢占路由。
 *
 * 收到 ISO-DEP APDU 时返回 6F 00（Function not supported），
 * 实际卡模拟在更底层（内核模块）已完成，不会走到这里。
 */
class WearNfcHostApduService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "processCommandApdu: ${commandApdu.joinToString(" ") { "%02X".format(it) }}")
        // 实际模拟由内核模块完成；此处返回 SW 6F00（Function not supported）
        return SW_FUNCTION_NOT_SUPPORTED
    }

    override fun onDeactivated(reason: Int) {
        val desc = when (reason) {
            DEACTIVATION_LINK_LOSS   -> "LINK_LOSS"
            DEACTIVATION_DESELECTED  -> "DESELECTED"
            else                     -> "reason=$reason"
        }
        Log.d(TAG, "onDeactivated: $desc")
    }

    companion object {
        private const val TAG = "WearNfcHCE"
        /** SW: Function not supported (6F 00) */
        private val SW_FUNCTION_NOT_SUPPORTED = byteArrayOf(0x6F.toByte(), 0x00.toByte())
    }
}
