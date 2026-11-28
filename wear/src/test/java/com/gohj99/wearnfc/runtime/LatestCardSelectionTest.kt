package com.gohj99.wearnfc.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

class LatestCardSelectionTest {
    @Test fun rapidSwipesKeepOnlyNewestQueuedCardAndNeverOverlapWrites() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            withTimeout(5_000) {
                val firstStarted = CompletableDeferred<Unit>()
                val finishFirst = CompletableDeferred<Unit>()
                val applied = Collections.synchronizedList(mutableListOf<String>())
                val queue = LatestCardSelection(scope) { id, _, _ ->
                    applied += id
                    if (id == "B") { firstStarted.complete(Unit); finishFirst.await() }
                    true
                }
                val first = queue.submit("B")
                firstStarted.await()
                val skipped = queue.submit("C")
                val latest = queue.submit("D")
                assertFalse(skipped.await())
                assertEquals("D", queue.requestedCardId.value)
                assertEquals(listOf("B"), applied)
                finishFirst.complete(Unit)
                assertTrue(first.await())
                assertTrue(latest.await())
                queue.requestedCardId.first { it == null }
                assertEquals(listOf("B", "D"), applied)
            }
        } finally { scope.cancel() }
    }

    @Test fun cancelledPageDoesNotCancelSubmittedSelection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            withTimeout(5_000) {
                val started = CompletableDeferred<Unit>()
                val finish = CompletableDeferred<Unit>()
                var active = "A"
                val queue = LatestCardSelection(scope) { id, _, _ ->
                    started.complete(Unit)
                    finish.await()
                    active = id
                    true
                }
                val page = launch { queue.submit("B").await() }
                started.await()
                page.cancelAndJoin()
                finish.complete(Unit)
                queue.requestedCardId.first { it == null }
                assertEquals("B", active)
            }
        } finally { scope.cancel() }
    }

    @Test fun swipingBackToActiveCardStillUndoesInFlightSwitch() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            withTimeout(5_000) {
                val started = CompletableDeferred<Unit>()
                val finish = CompletableDeferred<Unit>()
                var active = "A"
                val applied = mutableListOf<String>()
                val queue = LatestCardSelection(scope) { id, _, _ ->
                    if (id == "B") { started.complete(Unit); finish.await() }
                    active = id
                    applied += id
                    true
                }
                queue.submit("B")
                started.await()
                val latest = queue.submit("A")
                finish.complete(Unit)
                assertTrue(latest.await())
                assertEquals(listOf("B", "A"), applied)
                assertEquals("A", active)
            }
        } finally { scope.cancel() }
    }

    @Test fun requestSupersededWhileWaitingForMetadataLockCanBeSkipped() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            withTimeout(5_000) {
                val waiting = CompletableDeferred<Unit>()
                val lockReleased = CompletableDeferred<Unit>()
                val applied = mutableListOf<String>()
                val queue = LatestCardSelection(scope) { id, _, isLatest ->
                    waiting.complete(Unit)
                    lockReleased.await()
                    if (isLatest()) { applied += id; true } else false
                }
                val old = queue.submit("B")
                waiting.await()
                val latest = queue.submit("C", force = true)
                lockReleased.complete(Unit)
                assertFalse(old.await())
                assertTrue(latest.await())
                assertEquals(listOf("C"), applied)
            }
        } finally { scope.cancel() }
    }
}
