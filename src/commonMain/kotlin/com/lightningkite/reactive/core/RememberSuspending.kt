package com.lightningkite.reactive.core

import com.lightningkite.reactive.context.CalculationContext
import com.lightningkite.reactive.context.ReactiveContextSuspending
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * This is a suspending version of [remember]. Creates a reactive value that automatically updates when its dependencies change.
 *
 * The [action] block is executed in a reactive context, and its result is cached and shared among listeners.
 * The calculation runs in a coroutine, and will re-run whenever any reactive value it depends on changes.
 *
 * Note:
 * - `remember` is lazy: if it has no listeners, it will not calculate a value.
 * - Listeners are only notified if the calculated value changes (i.e., if the new value is different from the previous value).
 *
 * @param coroutineContext The coroutine context for running the calculation (default: Dispatchers.Unconfined).
 * @param useLastWhileLoading If true, uses the last known value while recalculating.
 * @param action The block to compute the value reactively.
 * @return A [Reactive] value that updates automatically.
 *
 * Example:
 * ```kotlin
 * val a = Signal(0)
 * val b = Signal(1)
 * val sum: Reactive<Int> = rememberSuspending { a() + b() }
 *
 * reactiveSuspending {
 *    println("sum: ${sum()}") // prints "sum: 1"
 * }
 *
 * a.value = 1 // prints "sum: 2"
 * b.value = 2 // prints "sum: 3"
 * ```
 *
 * @see [remember]
 */
fun <T> rememberSuspending(coroutineContext: CoroutineContext = Dispatchers.Unconfined, useLastWhileLoading: Boolean = false, action: suspend CalculationContext.() -> T): Reactive<T> {
    return RememberSuspending(coroutineContext = coroutineContext, useLastWhileLoading = useLastWhileLoading, action = action)
}

/**
 * A reactive value that remembers the result of a calculation and shares the result among its listeners.
 *
 * This class is used to create a reactive value whose state is determined by executing a block of code
 * (the [action]) within a [ReactiveContextSuspending]. The calculation is performed in a coroutine, and the result
 * is cached and updated reactively as dependencies change. The calculation is automatically re-run when
 * any of its dependencies change, and listeners are notified accordingly.
 *
 * Note:
 * - `Remember` is lazy: if it has no listeners, it will not calculate a value.
 * - Listeners are only notified if the calculated value changes (i.e., if the new value is different from the previous value).
 *
 * @param T The type of value produced by the calculation.
 * @param coroutineContext The coroutine context in which the calculation runs. Defaults to [Dispatchers.Unconfined].
 * @param useLastWhileLoading If true, the last known value will be used while the calculation is loading or re-running.
 * @param action The block of code to execute within the [ReactiveContextSuspending] to produce the value.
 *
 * This class manages its own coroutine job and calculation scope. When activated, it starts the calculation
 * and listens for changes. When deactivated, it cancels the job and stops listening.
 *
 * Listeners can be added to be notified when the value changes. The calculation is protected against
 * cancellation exceptions, and any other exceptions are reported via [Reactive.reportException].
 *
 * @see [Remember]
 */
class RememberSuspending<T>(
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    useLastWhileLoading: Boolean = false,
    private val action: suspend CalculationContext.() -> T
) : Reactive<T>, CalculationContext, BaseListenable() {
    private var job = SupervisorJob()
    private val restOfContext = coroutineContext +
            CoroutineExceptionHandler { coroutineContext, throwable ->
                if (throwable !is CancellationException) {
                    Reactive.reportException(throwable)
                }
            }
    override val coroutineContext: CoroutineContext get() = restOfContext + job

    private val scope = ReactiveContextSuspending(this, useLastWhileLoading) { this.action() }

    override val state: ReactiveState<T>
        get() {
            if (!scope.active) scope.runOnceWhileDead()
            return scope.state
        }

    private var remover: (() -> Unit)? = null
    override fun activate() {
        scope.startCalculation()
        remover = scope.addListener { invokeAllListeners() }
    }
    override fun deactivate() {
        super.deactivate()
        job.cancel()
        job = SupervisorJob()
        remover?.invoke()
        scope.cancel()
    }
}
