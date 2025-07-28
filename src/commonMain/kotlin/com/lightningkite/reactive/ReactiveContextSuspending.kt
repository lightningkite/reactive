package com.lightningkite.reactive

import kotlinx.coroutines.*

class ReactiveContextSuspending<T>(
    val scope: CoroutineScope,
    val useLastWhileLoading: Boolean = false,
    private val reportTo: RawReactive<T> = RawReactive<T>(),
    val action: suspend () -> T,
) : DependencyChangeListener(), Reactive<T> by reportTo {
    internal var lastJob: Job? = null

    var active = false
        private set

    private fun CoroutineScope.launchWithStart(block: suspend CoroutineScope.() -> Unit) =
        launch(
            start =
                if (coroutineContext[CoroutineDispatcher]?.isDispatchNeeded(coroutineContext) == false)
                    CoroutineStart.UNDISPATCHED
                else
                    CoroutineStart.DEFAULT,

            block = block
        )

    fun startCalculation() {
        active = true
        lastJob?.cancel()
        dependencyBlockStart()
        lastJob = (scope + this).let { calculationContext ->
            var done = false
            val job = calculationContext.launchWithStart {
                val result = reactiveState { action() }
                if (!useLastWhileLoading || result.ready) reportTo.state = result
                dependencyBlockEnd()
                done = true
            }

            if (done) return@let null
            else {
                // start load
                if (!useLastWhileLoading) reportTo.state = ReactiveState.notReady
                return@let job
            }
        }
    }

    fun runOnceWhileDead() {
        lastJob = run {
            var done = false
            val job = scope.launchWithStart {
                val result = reactiveState { action() }
                if (!useLastWhileLoading || result.ready) reportTo.state = result
                done = true
            }

            if (done) null
            else {
                if (!useLastWhileLoading) reportTo.state = ReactiveState.notReady
                job
            }
        }
    }

    override fun onDependencyChange() {
        startCalculation()
    }
    override fun onDependencyNotReady() {
        if (!useLastWhileLoading) reportTo.state = ReactiveState.notReady
    }

    override fun cancel() {
        super.cancel()
        active = false
        lastJob?.let {
            lastJob = null
            it.cancel()
        }
    }

    init {
        scope.onRemove { cancel() }
    }
}

@Suppress("NOTHING_TO_INLINE") inline fun CalculationContext.reactiveSuspending(noinline action: suspend () -> Unit) =
    ReactiveContextSuspending(this, action = action).also {
        it.startCalculation()
        coroutineContext[StatusListener.Key]?.loading(it)
    }

inline fun CalculationContext.reactiveSuspending(crossinline onLoad: () -> Unit, noinline action: suspend () -> Unit): ReactiveContextSuspending<Unit> {
    return reactiveSuspending(action = action).also {
        it.addListener { if(!it.state.ready) onLoad() }.let(::onRemove)
    }
}