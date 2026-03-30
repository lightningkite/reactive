package com.lightningkite.reactive.context

import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.reactiveState
import com.lightningkite.reactive.core.RawReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.extensions.DebounceListenable
import com.lightningkite.reactive.extensions.DebounceReactive
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.reflect.KMutableProperty0
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Interface for helper functions which require an additional [CoroutineScope] context. This will eventually
 * be removed in favor of context receivers.
 * */
interface CoroutineScopeHelpers : CoroutineScope {
    @ReactiveDsl
    operator fun <T, IGNORED> ((T) -> IGNORED).invoke(actionToCalculate: ReactiveContext.() -> T) =
        this@CoroutineScopeHelpers.reactive(action = { this@invoke(actionToCalculate(this)) })

    /**
     * Syntax sugar for reactively setting values.
     *
     * ```kotlin
     * val reactiveThing: Reactive<Int> = ...
     * var value = 0
     *
     * ::value { reactiveThing() }
     *
     * \\ Equivalent To
     *
     * reactive {
     *    value = reactiveThing()
     * }
     * ```
     * */
    @ReactiveDsl
    operator fun <T> KMutableProperty0<T>.invoke(actionToCalculate: ReactiveContext.() -> T) = this@CoroutineScopeHelpers.reactive(action = { set(actionToCalculate(this)) })


    /**
     * Syntax sugar for reactively setting a value equal to a reactive value.
     *
     * ```kotlin
     * val reactiveThing: Reactive<Int> = ...
     * var value = 0
     *
     * ::value bind reactiveThing
     *
     * \\ Equivalent To
     *
     * reactive {
     *    value = reactiveThing()
     * }
     * ```
     * */
    @ReactiveDsl
    infix fun <T> KMutableProperty0<T>.bind(reactive: Reactive<T>) = this@CoroutineScopeHelpers.reactive(action = { set(reactive()) })

    /**
     * Bidirectionally binds this [MutableReactive] to another [MutableReactive], keeping both values in sync.
     * Changes to either reactive value will propagate to the other.
     */
    infix fun <T> MutableReactive<T>.bind(master: MutableReactive<T>) {
        val reportTo = RawReactive(ReactiveState(Unit))
        coroutineContext[StatusListener]?.watchBackgroundProcess(reportTo)
        launch {
            reportTo.state = ReactiveState.notReady
            reportTo.state = reactiveState {
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

    /**
     * Debounces listener notifications by [timeMs] milliseconds using this scope. State is always current.
     * @see DebounceReactive
     */
    fun <T> Reactive<T>.debounce(timeMs: Long): Reactive<T> = DebounceReactive(this, this@CoroutineScopeHelpers, timeMs.milliseconds)

    /**
     * Debounces listener notifications by [duration] using this scope. State is always current.
     * @see DebounceReactive
     */
    fun <T> Reactive<T>.debounce(duration: Duration): Reactive<T> = DebounceReactive(this, this@CoroutineScopeHelpers, duration)

    /**
     * Debounces listener notifications by [timeMs] milliseconds using this scope.
     * @see DebounceListenable
     */
    fun Listenable.debounce(timeMs: Long): Listenable = DebounceListenable(this, this@CoroutineScopeHelpers, timeMs.milliseconds)

    /**
     * Debounces listener notifications by [duration] using this scope.
     * @see DebounceListenable
     */
    fun Listenable.debounce(duration: Duration): Listenable = DebounceListenable(this, this@CoroutineScopeHelpers, duration)
}

@OptIn(ExperimentalStdlibApi::class)
private fun <A> CoroutineScope.oneAtATime(work: Boolean, action: suspend (A) -> Unit): (A) -> Unit {
    var lastJob: Job? = null
    val reportTo = RawReactive(ReactiveState(Unit))

    if (work)
        coroutineContext[StatusListener]?.watchForegroundProcess(reportTo)
    else
        coroutineContext[StatusListener]?.watchBackgroundProcess(reportTo)

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
                val result = reactiveState {
                    action(it)
                }
                done = true
                reportTo.state = result
            }

            if (done) {
                return@let null
            } else {
                // start load
                reportTo.state = ReactiveState.notReady
                return@let job
            }
        }
    }
}