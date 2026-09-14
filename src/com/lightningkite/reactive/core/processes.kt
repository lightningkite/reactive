package com.lightningkite.reactive.core

import com.lightningkite.reactive.extensions.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.jvm.JvmName


public interface Emitter<T>: CoroutineScope {
    public fun emit(value: T)
}

@JvmName("reactiveProcessImplicit")
public fun <T> CoroutineScope.reactiveProcess(emitter: suspend Emitter<T>.() -> Unit): Reactive<T> {
    val prop = LateInitSignal<T>()
    launch {
        emitter(object : Emitter<T>, CoroutineScope by this {
            override fun emit(value: T) {
                prop.value = value
            }
        })
    }
    return prop
}
// The two below only run their emitter while something is listening, so with no listeners they
// report notActive: whatever the emitter last produced is no longer being kept up to date.
public fun <T> reactiveProcess(scope: CoroutineScope = AppScope, emitter: suspend Emitter<T>.() -> Unit): Reactive<T> {
    return object: BaseReactive<T>(ReactiveState.notActive) {
        var job: Job? = null
        override fun activate() {
            state = ReactiveState.notReady
            job = scope.launch {
                emitter(object : Emitter<T>, CoroutineScope by this@launch {
                    override fun emit(value: T) {
                        state = ReactiveState(value)
                    }
                })
            }
        }
        override fun deactivate() {
            job?.cancel()
            job = null
            state = ReactiveState.notActive
        }
    }
}
public fun <T> rawReactiveProcess(scope: CoroutineScope = AppScope, emitter: suspend Emitter<ReactiveState<T>>.() -> Unit): Reactive<T> {
    return object: BaseReactive<T>(ReactiveState.notActive) {
        var job: Job? = null
        override fun activate() {
            state = ReactiveState.notReady
            job = scope.launch {
                emitter(object : Emitter<ReactiveState<T>>, CoroutineScope by this@launch {
                    override fun emit(value: ReactiveState<T>) {
                        state = value
                    }
                })
            }
        }
        override fun deactivate() {
            job?.cancel()
            job = null
            state = ReactiveState.notActive
        }
    }
}