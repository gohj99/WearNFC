package com.gohj99.wearnfc.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class CardSwitchTransactionTest {
    private class Operations : CardSwitchOperations {
        var routing = "A"
        var conf = "A"
        var persisted = "A"
        var active = "A"
        var enabled = true
        var disableFailures = 0
        var writeFails = false
        var restoreFails = false
        var enableFailures = 0
        var commitFails = false
        var writeBarrier: CompletableDeferred<Unit>? = null
        val writing = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        override suspend fun disableNfc(): Boolean {
            events += "disable"
            if (disableFailures-- > 0) return false
            enabled = false
            return true
        }

        override suspend fun writeConfiguration(): Boolean {
            events += "write"
            routing = "B"
            writing.complete(Unit)
            writeBarrier?.await()
            if (writeFails) return false
            conf = "B"
            return true
        }

        override suspend fun restoreConfiguration(): Boolean {
            events += "restore"
            if (restoreFails) return false
            routing = "A"
            conf = "A"
            return true
        }

        override suspend fun enableNfc(): Boolean {
            events += "enable"
            if (enableFailures-- > 0) return false
            enabled = true
            return true
        }

        override suspend fun commit() {
            events += "commit"
            if (commitFails) throw java.io.IOException("disk unavailable")
            persisted = "B"
            active = "B"
        }
    }

    @Test fun successCommitsOnlyAfterNfcIsEnabled() = runBlocking {
        val ops = Operations()
        assertTrue(performCardSwitch(ops).success)
        assertEquals(listOf("disable", "write", "enable", "commit"), ops.events)
        assertEquals("B", ops.persisted)
        assertEquals("B", ops.active)
        assertTrue(ops.enabled)
    }

    @Test fun failedSecondWriteRestoresOriginalConfigurationBeforeEnabling() = runBlocking {
        val ops = Operations().apply { writeFails = true }
        val result = performCardSwitch(ops)
        assertFalse(result.success)
        assertTrue(result.recovered)
        assertTrue(result.originalRestored)
        assertEquals(listOf("disable", "write", "restore", "enable"), ops.events)
        assertEquals("A", ops.routing)
        assertEquals("A", ops.conf)
        assertEquals("A", ops.active)
        assertEquals("A", ops.persisted)
        assertTrue(ops.enabled)
    }

    @Test fun failedDisableRetriesBeforeRestoringOriginalConfiguration() = runBlocking {
        val ops = Operations().apply { disableFailures = 1 }
        val result = performCardSwitch(ops)
        assertFalse(result.success)
        assertTrue(result.recovered)
        assertTrue(result.originalRestored)
        assertEquals(listOf("disable", "disable", "restore", "enable"), ops.events)
        assertEquals("A", ops.conf)
        assertEquals("A", ops.active)
    }

    @Test fun failedDisableStillAttemptsOriginalConfigurationRestore() = runBlocking {
        val ops = Operations().apply { disableFailures = 2 }
        val result = performCardSwitch(ops)
        assertFalse(result.success)
        assertFalse(result.recovered)
        assertTrue(result.originalRestored)
        assertEquals(listOf("disable", "disable", "restore", "enable"), ops.events)
        assertEquals("A", ops.conf)
    }

    @Test fun failedEnableDisablesAgainAndRestoresOriginalConfiguration() = runBlocking {
        val ops = Operations().apply { enableFailures = 1 }
        val result = performCardSwitch(ops)
        assertFalse(result.success)
        assertTrue(result.recovered)
        assertTrue(result.originalRestored)
        assertEquals(listOf("disable", "write", "enable", "disable", "restore", "enable"), ops.events)
        assertEquals("A", ops.conf)
        assertEquals("A", ops.active)
        assertTrue(ops.enabled)
    }

    @Test fun failedRollbackDoesNotEnableMixedConfiguration() = runBlocking {
        val ops = Operations().apply { writeFails = true; restoreFails = true }
        val result = performCardSwitch(ops)
        assertFalse(result.success)
        assertFalse(result.recovered)
        assertFalse(result.originalRestored)
        assertFalse(ops.enabled)
        assertEquals(listOf("disable", "write", "restore"), ops.events)
        assertEquals("A", ops.active)
    }

    @Test fun failedPersistenceRollsBackHardwareAndKeepsOldActiveCard() = runBlocking {
        val ops = Operations().apply { commitFails = true }
        val result = performCardSwitch(ops)
        assertFalse(result.success)
        assertTrue(result.recovered)
        assertTrue(result.originalRestored)
        assertTrue(result.cause is java.io.IOException)
        assertEquals("A", ops.routing)
        assertEquals("A", ops.conf)
        assertEquals("A", ops.persisted)
        assertEquals("A", ops.active)
    }

    @Test fun leavingPageDuringWriteStillFinishesHardwareAndPersistence() = runBlocking {
        withTimeout(5_000) {
            val barrier = CompletableDeferred<Unit>()
            val ops = Operations().apply { writeBarrier = barrier }
            val page = launch { performCardSwitch(ops) }
            ops.writing.await()
            page.cancel()
            barrier.complete(Unit)
            page.join()
            assertEquals("B", ops.routing)
            assertEquals("B", ops.conf)
            assertEquals("B", ops.persisted)
            assertEquals("B", ops.active)
            assertTrue(ops.enabled)
        }
    }
}
