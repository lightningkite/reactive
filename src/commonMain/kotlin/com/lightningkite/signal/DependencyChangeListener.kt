package com.lightningkite.signal

import kotlinx.coroutines.*
import kotlin.coroutines.*

abstract class DependencyChangeListener : DependencyTracker(), CoroutineContext.Element {
    override val key: CoroutineContext.Key<DependencyChangeListener> get() = Key
    object Key : CoroutineContext.Key<DependencyChangeListener> {}
    abstract fun onDependencyChange()
    open fun onDependencyNotReady() = onDependencyChange()
}

private fun <T> Continuation<T>.resumeState(state: SignalState<T>) {
    state.handle(
        success = { resume(it) },
        exception = { resumeWithException(it) },
        notReady = { resumeWithException(CancellationException("State not ready")) }
    )
}

suspend fun rerunOn(listenable: Listenable) {
    coroutineContext[DependencyChangeListener.Key]?.let {
        if(it.existingDependency(listenable) == null) {
            it.registerDependency(listenable, listenable.addListener { it.onDependencyChange() })
        }
    }
}

suspend inline operator fun <T> Signal<T>.invoke(): T = await()
suspend inline operator fun <T> ValueSignal<T>.invoke(): T = await()
suspend inline fun <T> Signal<T>.exception(): Exception? = state { it.exception }

suspend fun <T, V> Signal<T>.state(get: (SignalState<T>) -> V): V {
    coroutineContext[DependencyChangeListener.Key]?.let {
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
        return last
    } ?: return state.let(get)
}

suspend fun <T> Signal<T>.state(): SignalState<T> {
    coroutineContext[DependencyChangeListener.Key]?.let {
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
        return last
    } ?: return state
}

suspend fun <T> ValueSignal<T>.await(): T {
    coroutineContext[DependencyChangeListener.Key]?.let {
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
        return last
    } ?: return value
}

suspend fun <T> Signal<T>.await(): T {
    coroutineContext[DependencyChangeListener.Key]?.let {
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
    }
    return awaitOnce()
}

@Deprecated("Replace with 'awaitOnce'", ReplaceWith("this.awaitOnce()", "com.lightningkite.readable.awaitOnce"))
suspend fun <T> Signal<T>.awaitRaw(): T = awaitOnce()

suspend fun <T> Signal<T>.awaitOnce(): T {
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

@Deprecated("STAHP")
fun <T> Signal<Signal<T>>.flatten(): Signal<T> {
    val first = remember { this@flatten() }
    return remember { first()() }
}

suspend operator fun <R> (ReactiveContext.()->R).invoke(): R {
    return remember { this@invoke() }.awaitOnce()
}
suspend operator fun <A, R> (ReactiveContext.(A)->R).invoke(a: A): R {
    return remember { this@invoke(a) }.awaitOnce()
}
suspend operator fun <A, B, R> (ReactiveContext.(A, B)->R).invoke(a: A, b: B): R {
    return remember { this@invoke(a, b) }.awaitOnce()
}
suspend operator fun <A, B, C, R> (ReactiveContext.(A, B, C)->R).invoke(a: A, b: B, c: C): R {
    return remember { this@invoke(a, b, c) }.awaitOnce()
}