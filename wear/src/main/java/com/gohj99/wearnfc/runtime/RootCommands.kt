package com.gohj99.wearnfc.runtime

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal data class SuResult(
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
    val timedOut: Boolean = false,
) {
    val success: Boolean get() = !timedOut && exitCode == 0
}

internal object RootCommands {
    fun run(command: String): SuResult = ProcessCommands.run(listOf("su", "-c", command)).also {
        if (it.timedOut) RuntimeErrors.report(RuntimeError.RootCommandTimeout)
    }
}

/** 同时读取两个输出流；进程和输出读取共用同一个截止时间。 */
internal object ProcessCommands {
    const val TIMEOUT_MILLIS = 10_000L

    fun run(command: List<String>, timeoutMillis: Long = TIMEOUT_MILLIS): SuResult {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val process = try {
            ProcessBuilder(command).start()
        } catch (error: Exception) {
            return SuResult(-1, stderr = error.message.orEmpty())
        }
        val readers = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "root-command-output").apply { isDaemon = true }
        }
        val stdout = readers.submit<String> { process.inputStream.bufferedReader().use { it.readText() } }
        val stderr = readers.submit<String> { process.errorStream.bufferedReader().use { it.readText() } }
        fun remainingNanos() = (deadline - System.nanoTime()).coerceAtLeast(0L)
        return try {
            if (!process.waitFor(remainingNanos(), TimeUnit.NANOSECONDS)) throw TimeoutException()
            SuResult(
                process.exitValue(),
                stdout.get(remainingNanos(), TimeUnit.NANOSECONDS),
                stderr.get(remainingNanos(), TimeUnit.NANOSECONDS),
            )
        } catch (_: TimeoutException) {
            SuResult(-1, stderr = "Root command timed out", timedOut = true)
        } catch (error: Exception) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            SuResult(-1, stderr = error.message.orEmpty())
        } finally {
            // 超时时不等待输出线程，避免后代进程持有管道导致调用再次阻塞。
            if (process.isAlive) process.destroyForcibly()
            stdout.cancel(true)
            stderr.cancel(true)
            readers.shutdownNow()
        }
    }
}
