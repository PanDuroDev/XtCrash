package io.github.soufianodev.xtcrash

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WeakReferenceSlotTest {

    @Test
    fun takeReturnsNewestValueAndClearsTheSlot() {
        val slot = WeakReferenceSlot<Any>(Any())
        val first = Any()
        val newest = Any()

        slot.set(first)
        slot.set(newest)

        assertSame(newest, slot.take())
        assertNull(slot.take())
    }

    @Test
    fun concurrentSetAndTakeKeepTheSlotUsable() {
        val slot = WeakReferenceSlot<Any>(Any())
        val values = (0 until 4).map { Any() }
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(5)

        try {
            val setters = values.map { value ->
                executor.submit {
                    assertTrue("Timed out waiting to start", start.await(5, TimeUnit.SECONDS))
                    repeat(500) { slot.set(value) }
                }
            }
            val taker = executor.submit {
                assertTrue("Timed out waiting to start", start.await(5, TimeUnit.SECONDS))
                repeat(1500) {
                    val value = slot.take()
                    if (value != null) {
                        assertTrue("Slot returned a value that was never stored", values.contains(value))
                    }
                }
            }

            start.countDown()
            (setters + taker).forEach { it.get(20, TimeUnit.SECONDS) }

            val marker = Any()
            slot.set(marker)
            assertSame(marker, slot.take())
            assertNull(slot.take())
        } finally {
            executor.shutdownNow()
        }
    }
}
