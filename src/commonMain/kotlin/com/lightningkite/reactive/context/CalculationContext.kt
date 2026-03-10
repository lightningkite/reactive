package com.lightningkite.reactive.context

import com.lightningkite.reactive.core.Reactive
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

public interface StatusListener : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    /**
     * Key for [Job] instance in the coroutine context.
     */
    public companion object Key : CoroutineContext.Key<StatusListener>

    fun loading(reactive: Reactive<*>)
    fun working(reactive: Reactive<*>) = loading(reactive)
}

fun CoroutineScope.onRemove(action: () -> Unit) {
    coroutineContext[CoroutineName.Key]
    this.coroutineContext[Job]?.invokeOnCompletion { action() }
}

@Deprecated("No longer needed", ReplaceWith("CoroutineScope"))
typealias CalculationContext = CoroutineScope

@OptIn(ExperimentalStdlibApi::class)
val CoroutineScope.requireMainThread: Boolean get() = coroutineContext[CoroutineDispatcher.Key] is MainCoroutineDispatcher

@OptIn(ExperimentalStdlibApi::class)
fun CoroutineScope.onThread(action: ()->Unit) {
    val d = coroutineContext[CoroutineDispatcher.Key] ?: return action()
    if(d.isDispatchNeeded(coroutineContext)) {
        d.dispatch(coroutineContext, Runnable(action))
    } else {
        action()
    }
}

@DslMarker
annotation class ReactiveDsl

@Deprecated("Only exists to not break imports", level = DeprecationLevel.ERROR)
fun <T> Nothing.invoke(): Nothing = TODO()