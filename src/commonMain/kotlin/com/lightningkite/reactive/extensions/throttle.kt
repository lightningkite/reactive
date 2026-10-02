package com.lightningkite.reactive.extensions

import com.lightningkite.reactive.core.BaseListenable
import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.Release
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

/**
 * A [Reactive] wrapper that throttles listener notifications from the [source]
 *
 * The first time the source changes, listeners are notified immediately. Subsequent changes are only sent if
 * the previous change was not sent within the given [duration]. If [tailScope] is not null, the last change
 * will also be sent after the full duration has passed from the moment the previous change was sent.
 *
 * The [state] always reflects the current state of the source immediately (no delay), only listener
 * notifications are throttled.
 *
 * @param T The type of the value held by the reactive.
 * @property source The underlying reactive to throttle.
 * @property duration The throttle delay duration.
 * @property tailScope The coroutine scope used for launching tail update coroutines, or `null` if tail
 *  updates are not required.
 *
 * @see ThrottleListenable
 */
internal class ThrottleReactive<T>(
    val source: Reactive<T>,
    val duration: Duration,
    val head: Boolean,
    val tailScope: CoroutineScope?,
    timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) : Reactive<T>, Listenable by ThrottleListenable(source, duration, head, tailScope, timeSource) {
    override val state: ReactiveState<T> get() = source.state
}

/**
 * A [Listenable] wrapper that throttles listener notifications from [source].
 *
 * The first time the source fires listeners are notified immediately. Subsequent notifications are only sent
 * if the previous notification was not sent within the given [duration]. Additionally, if [tailScope] is
 * not null, the last change will also be sent after the full duration has passed from the time the previous
 * notification was sent.
 *
 * @property source The underlying listenable to throttle.
 * @property duration The throttle delay duration.
 * @property head Whether to include the head call.
 * @property tailScope The coroutine scope used for launching tail update coroutines, or `null` if tail
 *  updates are not required.
 *
 * @see ThrottleReactive
 */
internal class ThrottleListenable(
    val source: Listenable,
    val duration: Duration,
    val head: Boolean,
    val tailScope: CoroutineScope?,
    val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) : BaseListenable() {
    @Volatile
    private var lastInvoked: ComparableTimeMark? = null

    private var tailJob: Job? = null
    private var releaseListener: Release? = null

    override fun activate() {
        releaseListener = source.addListener {
            val now = timeSource.markNow()

            val unthrottled = lastInvoked.let { it == null || now - it >= duration }
            if (unthrottled) {
                lastInvoked = now
                if (head) {
                    tailJob?.cancel()
                    invokeAllListeners()
                    return@addListener
                }
            }

            if (tailScope == null || tailJob?.isActive == true) return@addListener
            tailJob = tailScope.launch(start = CoroutineStart.UNDISPATCHED) {
                val tailAt = (lastInvoked ?: now) + duration
                delay(tailAt - now)
                lastInvoked = timeSource.markNow()
                tailJob = null
                invokeAllListeners()
            }
        }
    }

    override fun deactivate() {
        lastInvoked = null
        tailJob?.cancel()
        tailJob = null
        releaseListener?.invoke()
        releaseListener = null
    }
}

/**
 * Throttles listener notifications by [duration]. Includes only head notifications. State is always current.
 * @see ThrottleReactive
 */
public fun <T> Reactive<T>.headThrottle(duration: Duration): Reactive<T> = ThrottleReactive(this, duration, true, null)

/**
 * Throttles listener notifications by [duration]. Includes only tail notifications. State is always current.
 * @see ThrottleReactive
 */
public fun <T> Reactive<T>.tailThrottle(duration: Duration, scope: CoroutineScope): Reactive<T> = ThrottleReactive(this, duration, false, scope)

/**
 * Throttles listener notifications by [duration]. Includes both head and tail notifications. State is always current.
 * @see ThrottleReactive
 */
public fun <T> Reactive<T>.fullThrottle(duration: Duration, scope: CoroutineScope): Reactive<T> = ThrottleReactive(this, duration, true, scope)

/**
 * Throttles listener notifications by [duration]. Includes only head notifications.
 * @see ThrottleListenable
 */
public fun Listenable.headThrottle(duration: Duration): Listenable = ThrottleListenable(this, duration, true, null)

/**
 * Throttles listener notifications by [duration]. Includes only tail notifications.
 * @see ThrottleListenable
 */
public fun Listenable.tailThrottle(duration: Duration, scope: CoroutineScope): Listenable = ThrottleListenable(this, duration, false, scope)

/**
 * Throttles listener notifications by [duration]. Includes both head and tail notifications.
 * @see ThrottleListenable
 */
public fun Listenable.fullThrottle(duration: Duration, scope: CoroutineScope): Listenable = ThrottleListenable(this, duration, true, scope)