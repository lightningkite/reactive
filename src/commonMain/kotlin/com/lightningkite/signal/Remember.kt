package com.lightningkite.signal

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

fun <T> remember(coroutineContext: CoroutineContext = Dispatchers.Unconfined, useLastWhileLoading: Boolean = false, action: ReactiveContext.() -> T): Reactive<T> {
    return Remember(coroutineContext = coroutineContext, useLastWhileLoading = useLastWhileLoading, action = action)
}

class Remember<T>(
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    useLastWhileLoading: Boolean = false,
    private val action: ReactiveContext.() -> T
) : Reactive<T>, CalculationContext, BaseListenable() {
    private var job = Job()
    private val restOfContext = coroutineContext +
            CoroutineExceptionHandler { coroutineContext, throwable ->
                if (throwable !is CancellationException) {
                    Reactive.reportException(throwable)
                }
            }

    override val coroutineContext get() = job + restOfContext

    private val scope = TypedReactiveContext(this, useLastWhileLoading, action = action)

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
        job.cancel()
        job = Job()
        remover?.invoke()
        scope.cancel()
    }
}