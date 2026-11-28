package com.gohj99.wearnfc.runtime

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class KsuNfcProfileFixResult(val repaired: Boolean)

internal object KsuNfcProfileFix {
    private const val HELPER_ASSET = "ksu_profile_helper"
    private const val ROOT_HELPER = "/data/local/tmp/wearnfc_ksu_profile_helper"

    suspend fun repair(context: Context, uid: Int): KsuNfcProfileFixResult = withContext(Dispatchers.IO) {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in SUPPORTED_ABIS }
            ?: throw IllegalStateException("No compatible profile helper for ${Build.SUPPORTED_ABIS.joinToString()}")
        val helper = File(context.filesDir, HELPER_ASSET)
        context.assets.open("$abi/$HELPER_ASSET").use { input ->
            helper.outputStream().use(input::copyTo)
        }

        val rootPath = shellQuote(ROOT_HELPER)
        val sourcePath = shellQuote(helper.absolutePath)
        val command = "trap 'rm -f $ROOT_HELPER' EXIT; cp $sourcePath $rootPath && chmod 700 $rootPath && $rootPath repair $uid"
        val result = RootCommands.run(command)
        if (!result.success) {
            throw IllegalStateException(result.stderr.ifBlank { result.stdout }.ifBlank { "Root command failed (${result.exitCode})" }.trim())
        }

        val fields = result.stdout.lineSequence().mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
        }.toMap()
        check(fields["custom"] == "1" && fields["umount_modules"] == "0") {
            "The profile could not be verified after repair"
        }
        KsuNfcProfileFixResult(repaired = fields["changed"] == "1")
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
}
