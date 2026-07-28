package com.lightningkite.reactive.core

/**
 * Assertion that the reactive graph is only ever mutated from a single thread.
 *
 * The reactive graph is single-threaded by design (typically the UI/main thread): listener lists and
 * dependency trackers are plain unsynchronized [ArrayList]s, so mutating them from more than one
 * thread corrupts them silently. Each [BaseListenable] and
 * [com.lightningkite.reactive.context.DependencyTracker] records the thread that first mutated it and
 * throws a clear [IllegalStateException] if a later mutation arrives from a different thread.
 *
 * This is an assertion, not synchronization - it reports misuse, it does not make anything safe.
 *
 * On Kotlin/JS the check compiles away: web workers do not share an object graph, so there is no way
 * for two threads to touch the same reactive node.
 */
object ReactiveThreadCheck {
    /**
     * Kill switch for the confinement assertion, on by default.
     *
     * Set to `false` to unblock an app that trips the assertion in code that cannot be fixed
     * immediately. That does not make the offending mutation safe; it only stops reporting it.
     */
    var enabled: Boolean = true
}

/**
 * Identity of the current thread, or `null` on platforms with no shared-memory threads - in which
 * case confinement cannot be violated and the check is skipped.
 */
internal expect fun currentReactiveThread(): Any?

/**
 * Backs the thread-confinement assertion.
 *
 * Callers keep a single nullable `owningThread` field and write the result back:
 * `owningThread = checkThreadConfinement(owningThread)`. Keeping that field in the caller rather than
 * in a helper object avoids an extra allocation per reactive node, of which there are many.
 *
 * @param owningThread the thread that previously mutated the structure, or `null` if never mutated.
 * @return the owning thread, to be stored back by the caller.
 */
internal fun checkThreadConfinement(owningThread: Any?): Any? {
    if (!ReactiveThreadCheck.enabled) return owningThread
    val current = currentReactiveThread() ?: return owningThread
    if (owningThread == null) return current
    if (owningThread != current) throw IllegalStateException(
        "Reactive graph mutated from thread '$current' but it is confined to thread '$owningThread'. " +
                "The reactive graph is single-threaded; mutate it only from its owning thread " +
                "(typically the UI/main thread)."
    )
    return owningThread
}
