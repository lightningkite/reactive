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
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

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
 * @property scope The coroutine scope used for launching delay coroutines.
 * @property duration The debounce delay duration.
 *
 * @see DebounceListenable
 */
public class DebounceReactive<T> internal constructor(
    public val source: Reactive<T>,
    public val scope: CoroutineScope,
    public val duration: Duration,
    testingClock: Clock? = null,
) : Reactive<T>, Listenable by DebounceListenable(source, scope, duration, testingClock) {
    // for backwards compatibility
    public constructor(source: Reactive<T>, scope: CoroutineScope, duration: Duration) : this(source, scope, duration, null)

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
 * A single coroutine is launched per burst of changes. It keeps sleeping until [duration] has passed
 * since the most recent change, then notifies listeners and finishes.
 *
 * **Threading note:** This implementation uses `@Volatile` for visibility but is not otherwise
 * synchronized. This is acceptable for debouncing where the consequence of a race is at most one
 * extra or missed notification. For typical single-threaded reactive patterns, this is not an issue.
 *
 * @property source The underlying listenable to debounce.
 * @property scope The coroutine scope used for launching delay coroutines.
 * @property duration The debounce delay duration.
 *
 * @see DebounceReactive
 */
public class DebounceListenable internal constructor(
    public val source: Listenable,
    public val scope: CoroutineScope,
    public val duration: Duration,
    testingClock: Clock?,
) : BaseListenable() {
    // for backwards compatibility
    public constructor(source: Listenable, scope: CoroutineScope, duration: Duration) : this(source, scope, duration, null)

    private val clock = testingClock ?: Clock.System

    @Volatile
    private var sourceLastFired: Instant = Instant.DISTANT_PAST

    private var job: Job? = null
    private var releaseListener: Release? = null

    override fun activate() {
        releaseListener = source.addListener {
            sourceLastFired = clock.now()

            if (job?.isActive == true) return@addListener
            job = scope.launch {
                while (true) {
                    val remaining = sourceLastFired + duration - clock.now()
                    if (remaining <= Duration.ZERO) break
                    delay(remaining)
                }
                invokeAllListeners()
            }
        }
    }

    override fun deactivate() {
        sourceLastFired = Instant.DISTANT_PAST
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
@Deprecated("Use Duration instead of milliseconds.")
public fun <T> Reactive<T>.debounce(timeMs: Long, scope: CoroutineScope): Reactive<T> = DebounceReactive(this, scope, timeMs.milliseconds)

/**
 * Debounces listener notifications by [duration]. State is always current.
 * @see DebounceReactive
 */
public fun <T> Reactive<T>.debounce(duration: Duration, scope: CoroutineScope): Reactive<T> = DebounceReactive(this, scope, duration)

/**
 * Debounces listener notifications by [timeMs] milliseconds.
 * @see DebounceListenable
 */
@Deprecated("Use Duration instead of milliseconds.")
public fun Listenable.debounce(timeMs: Long, scope: CoroutineScope): Listenable = DebounceListenable(this, scope, timeMs.milliseconds)

/**
 * Debounces listener notifications by [duration].
 * @see DebounceListenable
 */
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