package com.lightningkite.signal

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

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
/**
 * Desired behavior for shared:
 *
 * - Outside a reactive scope, [Reactive.await] invokes the action with no sharing
 * - Inside a reactive scope, [Reactive.await] starts the whole system listening and sharing the calculation.
 */
fun <T> rememberSuspending(coroutineContext: CoroutineContext = Dispatchers.Unconfined, useLastWhileLoading: Boolean = false, action: suspend CalculationContext.() -> T): Reactive<T> {
    return RememberSuspending(coroutineContext = coroutineContext, useLastWhileLoading = useLastWhileLoading, action = action)
}