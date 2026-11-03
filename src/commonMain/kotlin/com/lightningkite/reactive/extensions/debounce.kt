package com.lightningkite.reactive.extensions

import com.lightningkite.reactive.core.BaseListenable
import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.Release
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A [Reactive] wrapper that debounces listener notifications from the [source].
 *
 * When the source changes, listeners are not notified immediately. Instead, notification is delayed
 * by [duration]. If the source changes again during this delay, the timer resets. Listeners are only
 * notified after the source has been stable for the full duration.
 *
 * The [state] always reflects the current state of the source immediately (no delay), only listener
 * notifications are debounced.
 *
 * @param T The type of value held by the reactive.
 * @property source The underlying reactive to debounce.
 * @property scope The coroutine scope used for launching the debounce coroutine.
 * @property duration The debounce delay duration.
 *
 * @see DebounceListenable
 */
@Suppress("DEPRECATION")
public class DebounceReactive<T> @Deprecated("Use source.debounce() instead", ReplaceWith("source.debounce(duration, scope)")) constructor(
    public val source: Reactive<T>,
    public val scope: CoroutineScope,
    public val duration: Duration,
) : Reactive<T>, Listenable by DebounceListenable(source, scope, duration) {
    override val state: ReactiveState<T> get() = source.state
}

/**
 * A [Listenable] wrapper that debounces listener notifications from the [source].
 *
 * When the source fires, listeners are not notified immediately. Instead, notification is delayed
 * by [duration]. If the source fires again during this delay, the timer resets. Listeners are only
 * notified after the source has been quiet for the full duration.
 *
 * This is useful for scenarios like search-as-you-type, where you want to wait for the user to
 * stop typing before triggering an expensive operation.
 *
 * **Threading note:** This implementation is not synchronized. This is acceptable for debouncing where
 * the consequence of a race is at most one extra or missed notification. For typical single-threaded
 * reactive patterns, this is not an issue.
 *
 * @property source The underlying listenable to debounce.
 * @property scope The coroutine scope used for launching the debounce coroutine.
 * @property duration The debounce delay duration.
 *
 * @see DebounceReactive
 */
public class DebounceListenable @Deprecated("Use .debounce() instead") constructor(
    public val source: Listenable,
    public val scope: CoroutineScope,
    public val duration: Duration,
) : BaseListenable() {
    private var job: Job? = null
    private var releaseListener: Release? = null

    override fun activate() {
        releaseListener = source.addListener {
            // Time is measured only by delay(), never by a separate clock, so the wait follows the
            // scope's own scheduler; under runTest, a wall clock would never see the wait end.
            job?.cancel()
            job = scope.launch {
                delay(duration)
                job = null
                invokeAllListeners()
            }
        }
    }

    override fun deactivate() {
        job?.cancel()
        job = null
        releaseListener?.invoke()
        releaseListener = null
    }
}

/**
 * Debounces listener notifications by [timeMs] milliseconds. State is always current.
 * @see DebounceReactive
 */
@Deprecated("Use Duration instead of milliseconds.", ReplaceWith("this.debounce(timeMs.milliseconds)", "kotlin.time.Duration.Companion.milliseconds"))
@Suppress("DEPRECATION")
public fun <T> Reactive<T>.debounce(timeMs: Long, scope: CoroutineScope): Reactive<T> = DebounceReactive(this, scope, timeMs.milliseconds)

/**
 * Debounces listener notifications by [duration]. State is always current.
 * @see DebounceReactive
 */
@Suppress("DEPRECATION")
public fun <T> Reactive<T>.debounce(duration: Duration, scope: CoroutineScope): Reactive<T> = DebounceReactive(this, scope, duration)

/**
 * Debounces listener notifications by [timeMs] milliseconds.
 * @see DebounceListenable
 */
@Deprecated("Use Duration instead of milliseconds.", ReplaceWith("this.debounce(timeMs.milliseconds)", "kotlin.time.Duration.Companion.milliseconds"))
@Suppress("DEPRECATION")
public fun Listenable.debounce(timeMs: Long, scope: CoroutineScope): Listenable = DebounceListenable(this, scope, timeMs.milliseconds)

/**
 * Debounces listener notifications by [duration].
 * @see DebounceListenable
 */
@Suppress("DEPRECATION")
public fun Listenable.debounce(duration: Duration, scope: CoroutineScope): Listenable = DebounceListenable(this, scope, duration)

/**
 * Debounces write operations to this [MutableReactive].
 *
 * When [set] is called, the write is delayed by [duration]. If [set] is called again during
 * this delay, the previous write is cancelled and the timer resets. Only the last value
 * written within any [duration] window is actually applied.
 *
 * Reads are not affected - the reactive's state reflects the last successfully written value.
 *
 * @param duration The debounce delay for write operations.
 * @return A [MutableReactive] wrapper with debounced writes.
 */
public fun <T> MutableReactive<T>.debounceWrite(duration: Duration): MutableReactive<T> = object: MutableReactive<T> by this {
    @Volatile
    var setIndex = 0

    override suspend fun set(value: T) {
        val mine = ++setIndex
        delay(duration)
        if (mine == setIndex) this@debounceWrite.set(value)
    }
}