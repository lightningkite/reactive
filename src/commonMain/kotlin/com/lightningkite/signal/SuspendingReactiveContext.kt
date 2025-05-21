package com.lightningkite.signal

import kotlinx.coroutines.*

class SuspendingReactiveContext<T> constructor(
    val scope: CoroutineScope,
    var action: suspend () -> T,
    private val reportTo: RawSignal<T> = RawSignal<T>(),
) : DependencyChangeListener(), Signal<T> by reportTo {
    internal var lastJob: Job? = null

    override fun onDependencyNotReady() {
        reportTo.state = SignalState.notReady
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun onDependencyChange() {
        lastJob?.cancel()
        dependencyBlockStart()

        lastJob = (scope + this).let { calculationContext ->
            var done = false
            val job = calculationContext.launch(
                start = if (calculationContext.coroutineContext[CoroutineDispatcher]?.isDispatchNeeded(
                        calculationContext.coroutineContext
                    ) == false
                ) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT
            ) {
                val result = signalState {
                    action()
                }
                dependencyBlockEnd()
                done = true
                reportTo.state = result
            }

            if (done) {
                return@let null
            } else {
                // start load
                reportTo.state = SignalState.notReady
                return@let job
            }
        }
    }

    override fun cancel() {
        super.cancel()
        lastJob?.let {
            lastJob = null
            it.cancel()
        }
    }

    init {
        onDependencyChange()
        scope.onRemove {
            cancel()
        }
    }
}

@Suppress("NOTHING_TO_INLINE") inline fun CalculationContext.reactiveSuspending(noinline action: suspend () -> Unit) = SuspendingReactiveContext(this, action).also {
    coroutineContext[StatusListener.Key]?.loading(it)
}

inline fun CalculationContext.reactiveSuspending(crossinline onLoad: () -> Unit, noinline action: suspend () -> Unit): SuspendingReactiveContext<Unit> {
    return reactiveSuspending(action = action).also {
        it.addListener { if(!it.state.ready) onLoad() }
    }
}