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

public sealed interface ReactiveCoroutineScope : CoroutineScope

@Deprecated("No longer needed", ReplaceWith("CoroutineScope"))
typealias CalculationContext = CoroutineScope

@OptIn(ExperimentalStdlibApi::class)
val CoroutineScope.requireMainThread: Boolean get() = coroutineContext[CoroutineDispatcher.Key] is MainCoroutineDispatcher

/**
 * Executes the given [action] on the thread associated with this [CoroutineScope]'s dispatcher.
 *
 * This function ensures that [action] runs on the correct thread for this scope, while optimizing
 * for the case where the caller is already on that thread.
 *
 * Behavior:
 * - If no dispatcher is configured, runs [action] immediately on the current thread
 * - If already on the correct thread, runs [action] immediately (synchronous execution)
 * - If on a different thread, dispatches [action] to run on the correct thread (asynchronous execution)
 *
 * This is particularly useful when you need to ensure thread safety but want to avoid unnecessary
 * thread switches. For example, updating UI state from a background thread will dispatch to the
 * main thread, while updates already on the main thread execute immediately.
 *
 * Example:
 * ```kotlin
 * val mainScope = CoroutineScope(Dispatchers.Main)
 *
 * // From background thread - dispatches to main thread
 * withContext(Dispatchers.Default) {
 *     mainScope.onThread {
 *         // Runs on main thread
 *         updateUI()
 *     }
 * }
 *
 * // Already on main thread - runs immediately
 * mainScope.onThread {
 *     // Runs synchronously
 *     updateUI()
 * }
 * ```
 *
 * @param action The action to execute on this scope's thread
 */
@OptIn(ExperimentalStdlibApi::class)
fun CoroutineScope.onThread(action: () -> Unit) {
    val d = coroutineContext[CoroutineDispatcher.Key] ?: return action()
    if (d.isDispatchNeeded(coroutineContext)) {
        d.dispatch(coroutineContext, Runnable(action))
    } else {
        action()
    }
}

@DslMarker
annotation class ReactiveDsl