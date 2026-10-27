package io.github.soufianodev.xtcrash

import java.util.ArrayDeque

/**
 * Bounded activity log shared by lifecycle callbacks and crash handling.
 *
 * The caller supplies the lock so the log can share the same critical section as
 * related activity state. Every access to the deque must use this lock.
 */
internal class ActivityLog(private val stateLock: Any) {

    private val entries = ArrayDeque<String>()

    fun append(line: String, maxEntries: Int) {
        val limit = maxEntries.coerceAtLeast(1)
        synchronized(stateLock) {
            while (entries.size >= limit) {
                entries.pollFirst()
            }
            entries.offerLast(line)
        }
    }

    fun snapshot(): String {
        // Copy while holding the lock; formatting the detached copy needs no lock.
        val stableEntries = synchronized(stateLock) {
            entries.toList()
        }
        return stableEntries.joinToString("")
    }
}
