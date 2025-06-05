package com.lightningkite.signal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.reflect.KMutableProperty0

abstract class CoroutineScopeHelpers : CoroutineScope {

    @Reactive
    inline operator fun <T, IGNORED> ((T) -> IGNORED).invoke(crossinline actionToCalculate: ReactiveContext.() -> T) = reactiveScope {
        this@invoke(actionToCalculate(this))
    }

    @Reactive
    inline operator fun <T> KMutableProperty0<T>.invoke(crossinline actionToCalculate: ReactiveContext.() -> T) = reactiveScope {
        this@invoke.set(actionToCalculate(this))
    }

    infix fun <T> MutableSignal<T>.bind(master: MutableSignal<T>) {
        var reportTo = RawSignal(SignalState(Unit))
        coroutineContext[StatusListener]?.loading(reportTo)
        launch {
            reportTo.state = SignalState.notReady
            reportTo.state = signalState {
                var intendedValue: T = master.await()
                this@bind.set(intendedValue)
                val setReplica = this@CoroutineScopeHelpers.oneAtATime(false) { value: T ->
                    this@bind.set(value)
                }
                val setMaster = this@CoroutineScopeHelpers.oneAtATime(true) { value: T ->
                    master.set(value)
                }
                master.addListener {
                    master.state.onSuccess {
                        if (intendedValue != it) {
                            intendedValue = it
                            setReplica(it)
                        }
                    }
                }.also { this@CoroutineScopeHelpers.onRemove(it) }
                this@bind.addListener {
                    this@bind.state.onSuccess {
                        if (intendedValue != it) {
                            intendedValue = it
                            setMaster(it)
                        }
                    }
                }.also { this@CoroutineScopeHelpers.onRemove(it) }
            }
        }
    }
}
@OptIn(ExperimentalStdlibApi::class)
private fun <A> CalculationContext.oneAtATime(work: Boolean, action: suspend (A) -> Unit): (A) -> Unit {
    var lastJob: Job? = null
    var reportTo = RawSignal(SignalState(Unit))
    if (work)
        coroutineContext[StatusListener]?.working(reportTo)
    else
        coroutineContext[StatusListener]?.loading(reportTo)

    return {
        lastJob?.cancel()
        lastJob = this.let { calculationContext ->
            var done = false
            val job = calculationContext.launch(
                start = if (calculationContext.coroutineContext[CoroutineDispatcher]?.isDispatchNeeded(
                        calculationContext.coroutineContext
                    ) == false
                ) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT
            ) {
                val result = signalState {
                    action(it)
                }
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
}