package com.lightningkite.reactive.core

import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.TypedReactiveContext
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * Creates a reactive value that automatically updates when its dependencies change.
 *
 * The [action] block is executed in a reactive context, and its result is cached and shared among listeners.
 * The calculation runs in a coroutine, and will re-run whenever any reactive value it depends on changes.
 *
 * Note:
 * - `remember` is lazy: if it has no listeners, it will not calculate a value. Reading its state while it has none reports [ReactiveState.Companion.notActive].
 * - Listeners are only notified if the calculated value changes (i.e., if the new value is different from the previous value).
 *
 * @param coroutineContext The coroutine context for running the calculation (default: Dispatchers.Unconfined).
 * @param useLastWhileLoading If true, uses the last known value while recalculating.
 * @param deactivationDelay If provided, the reactive context will be kept alive for that duration after all listeners have unsubscribed.
 * @param reentrancyLimit How many times [action] may trigger its own re-execution before the value
 *   is reported as failed; see [TypedReactiveContext].
 * @param action The block to compute the value reactively.
 * @return A [Reactive] value that updates automatically.
 *
 * Example:
 * ```kotlin
 * val a = Signal(0)
 * val b = Signal(1)
 * val sum: Reactive<Int> = remember { a() + b() }
 *
 * reactive {
 *    println("sum: ${sum()}") // prints "sum: 1"
 * }
 *
 * a.value = 1 // prints "sum: 2"
 * b.value = 2 // prints "sum: 3"
 * ```
 */
public fun <T> remember(
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    useLastWhileLoading: Boolean = false,
    deactivationDelay: Duration? = null,
    reentrancyLimit: Int = 0,
    action: ReactiveContext.() -> T,
): Reactive<T> =
    Remember(coroutineContext, useLastWhileLoading, deactivationDelay, reentrancyLimit, action)

/**
 * A reactive value that remembers the result of a calculation and shares the result among its listeners.
 *
 * This class is used to create a reactive value whose state is determined by executing a block of code
 * (the [action]) within a [ReactiveContext]. The calculation is performed in a coroutine, and the result
 * is cached and updated reactively as dependencies change. The calculation is automatically re-run when
 * any of its dependencies change, and listeners are notified accordingly.
 *
 * Note:
 * - `Remember` is lazy: if it has no listeners, it will not calculate a value. Reading its state while it has none reports [ReactiveState.Companion.notActive].
 * - Listeners are only notified if the calculated value changes (i.e., if the new value is different from the previous value).
 *
 * @param T The type of value produced by the calculation.
 * @param coroutineContext The coroutine context in which the calculation runs. Defaults to [Dispatchers.Unconfined].
 * @param useLastWhileLoading If true, the last known value will be used while the calculation is loading or re-running.
 * @param deactivationDelay If provided, the reactive context will be kept alive for that duration after all listeners have unsubscribed.
 * @param reentrancyLimit How many times [action] may trigger its own re-execution before the value
 *   is reported as failed; see [TypedReactiveContext].
 * @param action The block of code to execute within the [ReactiveContext] to produce the value.
 *
 * This class manages its own coroutine job and calculation scope. When activated, it starts the calculation
 * and listens for changes. When deactivated, it cancels the job and stops listening.
 *
 * Listeners can be added to be notified when the value changes. The calculation is protected against
 * cancellation exceptions, and any other exceptions are reported via [Reactive.reportException].
 */
public class Remember<T>(
    public val incomingCoroutineContext: CoroutineContext = Dispatchers.Unconfined,
    private val useLastWhileLoading: Boolean = false,
    private val deactivationDelay: Duration? = null,
    reentrancyLimit: Int = 0,
    private val action: ReactiveContext.() -> T,
) : Reactive<T>, CoroutineScope, BaseListenable() {

    private var job = SupervisorJob()
    private val restOfContext = incomingCoroutineContext +
            CoroutineExceptionHandler { _, throwable ->
                if (throwable !is CancellationException) {
                    Reactive.reportException(throwable)
                }
            }

    // NOTE: `job` must come AFTER `restOfContext` so this Remember's own SupervisorJob is the
    // authoritative `[Job]` of the scope. If `incomingCoroutineContext` carries a long-lived Job
    // (e.g. AppScope's AppJob), putting `job` first lets that incoming Job win the `[Job]` key,
    // and TypedReactiveContext.init's `scope.onRemove { cancel() }` then attaches an
    // invokeOnCompletion handler to the app-lifetime Job that never fires — leaking every
    // Remember/shared reactive graph forever. Matches RememberSuspending's ordering.
    override val coroutineContext: CoroutineContext get() = restOfContext + job

    // Starts notActive rather than notReady: nothing is listening yet, so there is no value to
    // be had, as opposed to one that is on its way.
    private val reported = RawReactive<T>(ReactiveState.notActive)
    private val scope = TypedReactiveContext(
        scope = this,
        useLastWhileLoading = useLastWhileLoading,
        reentrancyLimit = reentrancyLimit,
        reportTo = reported,
        action = action,
    )

    // A Remember only calculates while it has listeners, and reports notActive when it has none.
    // It deliberately does not calculate on demand: doing so would either subscribe to sources
    // nobody is listening to, or run a dependency-less calculation whose result is silently never
    // updated. To read one imperatively, use awaitOnce - it subscribes for as long as it takes to
    // get a value.
    override val state: ReactiveState<T> get() = reported.state

    private var deactivating: Job? = null
    private var remover: (() -> Unit)? = null
    private var shuttingDown: Job? = null

    override fun activate() {
        if (deactivating != null) {
            deactivating?.cancel()
            deactivating = null
            return
        }

        // Something is maintaining this value again - it just doesn't have one yet. Without this,
        // useLastWhileLoading would suppress the notReady the first calculation reports and leave
        // notActive in place, claiming nobody is listening when somebody now is.
        if (reported.state.notActive && !useLastWhileLoading) reported.state = ReactiveState.notReady

        shuttingDown?.let {
            CoroutineScope(incomingCoroutineContext).launch {
                it.join()
                scope.startCalculation()
                remover = scope.addListener { invokeAllListeners() }
            }
        } ?: run {
            scope.startCalculation()
            remover = scope.addListener { invokeAllListeners() }
        }
    }

    private fun shutdown() {
        remover?.invoke()
        remover = null
        scope.cancel()
        job.cancel()
        job = SupervisorJob()
        shuttingDown = null
        // The calculation is stopped, so the value it produced is no longer maintained. Reporting
        // notActive says exactly that, rather than passing off a value that may have gone stale.
        reported.state = ReactiveState.notActive
    }

    override fun deactivate() {
        if (deactivationDelay != null) {
            if (deactivating != null) return
            deactivating = launch {
                delay(deactivationDelay)
                ensureActive()
                shuttingDown = CoroutineScope(incomingCoroutineContext).launch {
                    shutdown()
                }
                deactivating = null
            }
        } else shutdown()
    }
}