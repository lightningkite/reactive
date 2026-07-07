package com.lightningkite.reactive.core

/**
 * Opt-in debug aid that asserts the reactive graph is only mutated from a single thread.
 *
 * The reactive graph is designed to be single-threaded (typically the UI/main thread): listener
 * lists and dependency trackers are plain unsynchronized [ArrayList]s. Mutating them from more than
 * one thread corrupts them silently. When [enabled], each [BaseListenable] records the thread that
 * first mutated it (via [currentThread]) and throws a clear [IllegalStateException] if a later
 * mutation comes from a different thread, surfacing the violation instead of producing corruption.
 *
 * This is intentionally an assertion, not real synchronization — it only reports misuse.
 *
 * ### Why opt-in / off by default
 * This library has only a common source set (no per-platform `expect`/`actual`), so there is no
 * built-in way to identify the current thread portably. Instead of adding platform source sets, the
 * thread identity is pluggable via [currentThread]. A platform consumer that wants the check enables
 * it and installs a hook, e.g. on the JVM:
 *
 * ```kotlin
 * ReactiveThreadCheck.currentThread = { Thread.currentThread() }
 * ReactiveThreadCheck.enabled = true
 * ```
 *
 * With [enabled] left `false` (the default) the check is a single boolean read and does nothing,
 * so it is safe for existing single-threaded code and tests.
 */
object ReactiveThreadCheck {
    /** When true, mutations of [BaseListenable]s are checked for thread confinement. */
    var enabled: Boolean = false

    /**
     * Returns an identity for the current thread, or `null` if thread identity is unavailable
     * (in which case the check is skipped). Defaults to `null`; platform consumers install a real
     * implementation such as `{ Thread.currentThread() }`.
     */
    var currentThread: () -> Any? = { null }
}
