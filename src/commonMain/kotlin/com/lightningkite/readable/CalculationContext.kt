package com.lightningkite.readable

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KMutableProperty0

public interface StatusListener : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    /**
     * Key for [Job] instance in the coroutine context.
     */
    public companion object Key : CoroutineContext.Key<StatusListener>

    fun loading(readable: Readable<*>)
    fun working(readable: Readable<*>) = loading(readable)
}

fun CoroutineScope.onRemove(action: () -> Unit) {
    coroutineContext[CoroutineName.Key]
    this.coroutineContext[Job]?.invokeOnCompletion { action() }
}

typealias CalculationContext = CoroutineScope
@OptIn(ExperimentalStdlibApi::class)
val CoroutineScope.requireMainThread: Boolean get() = coroutineContext[CoroutineDispatcher.Key] is MainCoroutineDispatcher
@OptIn(ExperimentalStdlibApi::class)
inline fun CoroutineScope.onThread(crossinline action: ()->Unit) {
    val d = coroutineContext[CoroutineDispatcher.Key] ?: return action()
    if(d.isDispatchNeeded(coroutineContext)) {
        d.dispatch(coroutineContext, Runnable(action))
    } else {
        action()
    }
}

@DslMarker
annotation class Reactive

@Deprecated("Only exists to not break imports", level = DeprecationLevel.ERROR)
fun <T> Nothing.invoke(): Nothing = TODO()