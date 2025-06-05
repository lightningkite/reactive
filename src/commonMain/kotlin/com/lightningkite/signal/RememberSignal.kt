package com.lightningkite.signal

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

fun <T> remember(coroutineContext: CoroutineContext = Dispatchers.Unconfined, useLastWhileLoading: Boolean = false, action: ReactiveContext.() -> T): Signal<T> {
    return RememberSignal(coroutineContext = coroutineContext, useLastWhileLoading = useLastWhileLoading, action = action)
}

class RememberSignal<T>(
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    useLastWhileLoading: Boolean = false,
    private val action: ReactiveContext.() -> T
) : Signal<T>, CalculationContext {

    private var job = Job()
    private val restOfContext = coroutineContext +
            CoroutineExceptionHandler { coroutineContext, throwable ->
                if (throwable !is CancellationException) {
                    Signal.reportException(throwable)
                }
            }
//    override val coroutineContext = job + restOfContext
    override val coroutineContext get() = job + restOfContext

    private fun cancel() {
        job.cancel()
        job = Job()
        scope.cancel()
    }

    private val scope = TypedReactiveContext(this, action = action)

    override val state: SignalState<T>
        get() {
            if (!scope.active) scope.runOnceWhileDead()
            return scope.state
        }
    private var lcount = 0
    override fun addListener(listener: () -> Unit): () -> Unit {
        if (lcount++ == 0) {
            scope.startCalculation()
        }
        val r = scope.addListener(listener)
        var removed = false
        return label@{
            if(removed) return@label
            removed = true
            r()
            if (--lcount == 0) {
                cancel()
            }
        }
    }
}