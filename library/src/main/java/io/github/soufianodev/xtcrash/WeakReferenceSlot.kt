package io.github.soufianodev.xtcrash

import java.lang.ref.WeakReference

/**
 * A weak reference slot whose set/take operations are serialized with the supplied state lock.
 *
 * take() obtains the current referent and clears the slot in one critical section, so a concurrent
 * set() cannot be accidentally cleared by a caller finishing an older activity.
 */
internal class WeakReferenceSlot<T : Any>(private val stateLock: Any) {

    private var reference: WeakReference<T>? = null

    fun set(value: T) {
        synchronized(stateLock) {
            reference = WeakReference(value)
        }
    }

    fun take(): T? = synchronized(stateLock) {
        reference?.get().also {
            reference = null
        }
    }
}
