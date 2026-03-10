package com.lightningkite.reactive.context

import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.ReactiveValue
import com.lightningkite.reactive.core.addAndRunListener
import kotlinx.coroutines.*
import kotlin.coroutines.*

abstract class DependencyChangeListener : DependencyTracker(), CoroutineContext.Element {
    override val key: CoroutineContext.Key<DependencyChangeListener> get() = Key
    object Key : CoroutineContext.Key<DependencyChangeListener> {}
    abstract fun onDependencyChange()
    open fun onDependencyNotReady() = onDependencyChange()
}

private fun <T> Continuation<T>.resumeState(state: ReactiveState<T>) {
    state.handle(
        success = { resume(it) },
        exception = { resumeWithException(it) },
        notReady = { resumeWithException(CancellationException("State not ready")) }
    )
}

suspend fun rerunOn(listenable: Listenable) {
    currentCoroutineContext()[DependencyChangeListener.Key]?.let {
        if (it.existingDependency(listenable) == null) {
            it.registerDependency(listenable, listenable.addListener { it.onDependencyChange() })
        }
    }
}

suspend inline operator fun <T> Reactive<T>.invoke(): T = await()
suspend inline operator fun <T> ReactiveValue<T>.invoke(): T = await()
suspend inline fun <T> Reactive<T>.exception(): Exception? = state { it.exception }

suspend fun <T, V> Reactive<T>.state(get: (ReactiveState<T>) -> V): V {
    return currentCoroutineContext()[DependencyChangeListener.Key]?.let {
        // and the value is ready to go, just add the listener and proceed with the value.
        var last = state.let(get)
        if(it.existingDependency(this) == null) {
            it.registerDependency(this, addListener {
                val newVal = state.let(get)
                if (last != newVal) {
                    last = newVal
                    it.onDependencyChange()
                }
            })
            // Repull in case of activation
            last = state.let(get)
        }
        last
    } ?: state.let(get)
}

suspend fun <T> Reactive<T>.state(): ReactiveState<T> {
    return currentCoroutineContext()[DependencyChangeListener.Key]?.let {
        // and the value is ready to go, just add the listener and proceed with the value.
        var last = state
        if(it.existingDependency(this) == null) {
            it.registerDependency(this, addListener {
                val newVal = state
                if (last != newVal) {
                    last = newVal
                    it.onDependencyChange()
                }
            })
            // Repull in case of activation
            last = state
        }
        last
    } ?: state
}

suspend fun <T> ReactiveValue<T>.await(): T {
    return currentCoroutineContext()[DependencyChangeListener.Key]?.let {
        // and the value is ready to go, just add the listener and proceed with the value.
        var last = value
        if(it.existingDependency(this) == null) {
            it.registerDependency(this, addListener {
                val newVal = value
                if (last != newVal) {
                    last = newVal
                    it.onDependencyChange()
                }
            })
            // Repull in case of activation
            last = value
        }
        last
    } ?: value
}

suspend fun <T> Reactive<T>.await(): T {
    return currentCoroutineContext()[DependencyChangeListener.Key]?.let {
        var cont: Continuation<T>? = null
        if (it.existingDependency(this) == null) {
            it.registerDependency(this, addListener {
                state.handle(
                    success = { r ->
                        cont?.let { c ->
                            c.resume(r)
                            cont = null
                        } ?: it.onDependencyChange()
                    },
                    exception = { r ->
                        cont?.let { c ->
                            c.resumeWithException(r)
                            cont = null
                        } ?: it.onDependencyChange()
                    },
                    notReady = {
                        if(cont == null) it.onDependencyNotReady()
                    }
                )
            })
        }

        this.state.handle(
            success = { return@let it },
            exception = { throw it },
            notReady = {
                return@let suspendCancellableCoroutine {
                    cont = it
                }
            }
        )
    } ?: awaitOnce()
}

suspend fun <T> Reactive<T>.awaitOnce(): T {
    val state = state
    return if (state.ready) state.get()
    else suspendCancellableCoroutine {
        // If it's not ready, we need to wait until it is then never bother with this again.
        var remover: (() -> Unit)? = null
        var alreadyRun = false
        var done = false
        remover = addAndRunListener {
            val state = this.state
            if (state.ready && !done) {
                done = true
                it.resumeState(state)
                remover?.invoke() ?: run {
                    alreadyRun = true
                }
            }
        }
        if (alreadyRun) remover.invoke()
        it.invokeOnCancellation {  remover.invoke() }
    }
}