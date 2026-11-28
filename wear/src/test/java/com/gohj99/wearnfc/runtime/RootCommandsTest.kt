package com.gohj99.wearnfc.runtime

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class RootCommandsTest {
    private fun fixture(mode: String): List<String> {
        val executable = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) "java.exe" else "java"
        val java = File(System.getProperty("java.home"), "bin/$executable").absolutePath
        val domain = requireNotNull(ProcessFixture::class.java.protectionDomain)
        val classes = File(requireNotNull(domain.codeSource).location.toURI()).absolutePath
        return listOf(java, "-cp", classes, ProcessFixture::class.java.name, mode)
    }

    @Test fun hungCommandReturnsFailureAfterTenSeconds() {
        val start = System.nanoTime()
        val result = ProcessCommands.run(fixture("hang"))
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000
        assertTrue(result.timedOut)
        assertFalse(result.success)
        assertTrue("elapsed=$elapsedMillis", elapsedMillis in 9_500..13_000)
    }

    @Test fun drainsBothPipesWithoutDeadlockAndPreservesExitStatus() {
        val result = ProcessCommands.run(fixture("output"))
        assertFalse(result.timedOut)
        assertFalse(result.success)
        assertEquals(7, result.exitCode)
        assertEquals(256 * 1024, result.stdout.length)
        assertEquals(256 * 1024, result.stderr.length)
    }

    @Test fun missingExecutableReturnsFailure() {
        val result = ProcessCommands.run(listOf("wearnfc-nonexistent-test-program"))
        assertFalse(result.success)
        assertFalse(result.timedOut)
    }

    @Test fun timeoutErrorIsNotOverwrittenByGenericSwitchFailure() {
        RuntimeErrors.dismiss()
        try {
            RuntimeErrors.report(RuntimeError.RootCommandTimeout)
            RuntimeErrors.report(RuntimeError.CardSwitchFailed)
            assertEquals(RuntimeError.RootCommandTimeout, RuntimeErrors.error.value)
            RuntimeErrors.dismiss()
            assertNull(RuntimeErrors.error.value)
        } finally { RuntimeErrors.dismiss() }
    }
}
