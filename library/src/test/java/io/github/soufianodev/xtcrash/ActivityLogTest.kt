package io.github.soufianodev.xtcrash

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityLogTest {

    @Test
    fun trimsOldestEntriesAndPreservesRemainingOrder() {
        val log = ActivityLog(Any())

        log.append("first\n", maxEntries = 2)
        log.append("second\n", maxEntries = 2)
        log.append("third\n", maxEntries = 2)

        assertEquals("second\nthird\n", log.snapshot())
    }

    @Test
    fun snapshotIsDetachedFromLaterAppends() {
        val log = ActivityLog(Any())
        log.append("before\n", maxEntries = 3)

        val snapshot = log.snapshot()
        log.append("after\n", maxEntries = 3)

        assertEquals("before\n", snapshot)
        assertEquals("before\nafter\n", log.snapshot())
    }

    @Test
    fun enforcesAtLeastOneRetainedEntry() {
        val log = ActivityLog(Any())

        log.append("discarded\n", maxEntries = 0)
        log.append("retained\n", maxEntries = 0)

        assertEquals("retained\n", log.snapshot())
    }

    @Test
    fun concurrentAppendsAndSnapshotsRemainSafeAndBounded() {
        val maxEntries = 64
        val log = ActivityLog(Any())
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(5)

        fun assertValidSnapshot(snapshot: String) {
            val lines = snapshot.lineSequence().filter { it.isNotEmpty() }.toList()
            assertTrue("Snapshot exceeded configured bound: ${lines.size}", lines.size <= maxEntries)
            assertTrue(
                "Snapshot contained a malformed entry: $snapshot",
                lines.all { it.matches(Regex("\\d+:\\d+")) }
            )
        }

        try {
            val writers = (0 until 4).map { worker ->
                executor.submit {
                    assertTrue("Timed out waiting to start", start.await(5, TimeUnit.SECONDS))
                    repeat(500) { index ->
                        log.append("$worker:$index\n", maxEntries)
                        assertValidSnapshot(log.snapshot())
                    }
                }
            }
            val reader = executor.submit {
                assertTrue("Timed out waiting to start", start.await(5, TimeUnit.SECONDS))
                repeat(1500) {
                    assertValidSnapshot(log.snapshot())
                }
            }

            start.countDown()
            (writers + reader).forEach { it.get(20, TimeUnit.SECONDS) }

            val finalSnapshot = log.snapshot()
            assertValidSnapshot(finalSnapshot)
            assertTrue("Expected the completed log to contain entries", finalSnapshot.isNotEmpty())
        } finally {
            executor.shutdownNow()
        }
    }
}
