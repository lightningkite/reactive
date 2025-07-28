package com.lightningkite.reactive.core

import com.lightningkite.reactive.extensions.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.jvm.JvmName


interface Emitter<T>: CoroutineScope {
    fun emit(value: T)
}

@JvmName("reactiveProcessImplicit")
fun <T> CoroutineScope.reactiveProcess(emitter: suspend Emitter<T>.() -> Unit): Reactive<T> {
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
fun <T> reactiveProcess(scope: CoroutineScope = AppScope, emitter: suspend Emitter<T>.() -> Unit): Reactive<T> {
    return object: BaseReactive<T>() {
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
        }
    }
}
fun <T> rawReactiveProcess(scope: CoroutineScope = AppScope, emitter: suspend Emitter<ReactiveState<T>>.() -> Unit): Reactive<T> {
    return object: BaseReactive<T>() {
        var job: Job? = null
        override fun activate() {
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
        }
    }
}