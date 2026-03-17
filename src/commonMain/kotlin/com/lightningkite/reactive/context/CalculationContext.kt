package com.lightningkite.reactive.context

import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Release
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context element for listening to reactive calculation status changes.
 *
 * Implementations of this interface can be added to a coroutine context to receive
 * notifications when reactive calculations enter loading or working states.
 *
 * This is primarily used for UI frameworks to show loading indicators or progress
 * when reactive calculations are in progress.
 *
 * Example:
 * ```kotlin
 * val statusListener = object : StatusListener {
 *     override fun loading(reactive: Reactive<*>) {
 *         showLoadingIndicator()
 *     }
 * }
 *
 * val scope = CoroutineScope(Dispatchers.Main + statusListener)
 * scope.reactive {
 *     // statusListener.loading() will be called when this starts
 *     someReactive()
 * }
 * ```
 */
public interface StatusListener : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    /**
     * Key for [StatusListener] instance in the coroutine context.
     */
    public companion object Key : CoroutineContext.Key<StatusListener>

    /**
     * Called when a reactive calculation is happening in the background, so this listener can respond accordingly. For example, to display loading states
     * for background calculations.
     *
     * @param status The reactive status of the process
     * @return A [Release] lambda to stop listening to the process
     */
    fun watchBackgroundProcess(status: Reactive<*>): Release

    /**
     * Called when a reactive calculation is happening in the foreground, so this listener can respond accordingly.
     *
     * The difference between this and [watchBackgroundProcess] is semantic, `foregroundProcess` should be called when a calculation is the direct result of a
     * user action (e.g. clicking a button).
     *
     * @param status The reactive status of the process
     * @return A [Release] lambda to stop listening to the process
     */
    fun watchForegroundProcess(status: Reactive<*>): Release = watchBackgroundProcess(status)
}

/**
 * Registers an [action] to be executed when this [CoroutineScope] is cancelled or completed.
 *
 * This is a convenience function that attaches a completion handler to the scope's [Job].
 * The [action] will be invoked exactly once when the scope's job completes, either through
 * normal completion, cancellation, or failure.
 *
 * Example:
 * ```kotlin
 * val scope = CoroutineScope(Job())
 * scope.onRemove {
 *     println("Scope is being cleaned up")
 *     // Release resources, cancel listeners, etc.
 * }
 * ```
 *
 * @param action The cleanup action to execute when the scope completes
 */
fun CoroutineScope.onRemove(action: () -> Unit) {
    coroutineContext[CoroutineName.Key]
    this.coroutineContext[Job]?.invokeOnCompletion { action() }
}

/**
 * Marker interface for [CoroutineScope] instances used within reactive contexts.
 *
 * This interface identifies scopes that have special lifecycle characteristics tied to reactive
 * calculations. The scope provides automatic cleanup and isolation guarantees that make it safe
 * to launch coroutines within reactive calculations without manual lifecycle management.
 *
 * ## Lifecycle Phases
 *
 * A ReactiveCoroutineScope goes through the following phases during reactive calculation:
 *
 * 1. **Creation**: A new scope (with fresh Job) is created at the start of each calculation run
 * 2. **Active Execution**: The reactive calculation executes, coroutines can be launched
 * 3. **Dependency Change**: When any tracked dependency changes, the scope's job is cancelled
 * 4. **Recreation**: A new scope is created for the next calculation run (returns to phase 1)
 * 5. **Final Cleanup**: When the parent context ends, all scopes and resources are released
 *
 * ## Automatic Cleanup Guarantees
 *
 * - **On Dependency Change**: When a reactive calculation reruns due to dependency changes,
 *   the previous scope's Job is cancelled, immediately stopping all coroutines launched within it.
 * - **On Parent Cancellation**: When the parent CoroutineScope is cancelled, the reactive
 *   context is cancelled, which cancels all active coroutines.
 * - **Resource Cleanup**: Use `onRemove { }` to register cleanup handlers that run when the
 *   scope is cancelled (either due to dependency change or parent cancellation).
 *
 * ## Nested Reactive Contexts
 *
 * Reactive contexts can be safely nested because each context maintains its own independent Job:
 *
 * ```kotlin
 * val outer = Signal(Signal(0))
 *
 * reactive {
 *     val inner = outer()  // Access outer signal
 *
 *     // Create nested reactive context
 *     reactive {
 *         inner()  // Access inner signal
 *         onRemove { println("Inner cleaned up") }
 *     }
 *
 *     onRemove { println("Outer cleaned up") }
 * }
 * ```
 *
 * **Behavior**:
 * - When `inner.value` changes: Only the inner context reruns (inner scope cancelled/recreated)
 * - When `outer.value` changes: Outer context reruns, which cancels outer scope including
 *   the nested reactive context, then both are recreated
 * - Cancellation cascades from outer to inner automatically
 *
 * ## Isolation Between Runs
 *
 * Each calculation run gets a completely fresh scope with a new Job. This provides isolation:
 *
 * ```kotlin
 * val dependency = Signal(0)
 *
 * reactive {
 *     val currentValue = dependency()
 *
 *     launch {
 *         delay(1000)
 *         println("Delayed: $currentValue")
 *     }
 * }
 *
 * dependency.value = 1  // First launch is cancelled, new one starts with value=1
 * dependency.value = 2  // Second launch is cancelled, new one starts with value=2
 * ```
 *
 * Each launched coroutine only sees the value from its calculation run, and is automatically
 * cancelled when that run becomes stale.
 *
 * ## Usage Patterns
 *
 * ### Basic Coroutine Launch
 * ```kotlin
 * reactive {
 *     val data = dataSource()
 *     launch {
 *         // Coroutine automatically cancelled when dataSource changes
 *         processData(data)
 *     }
 * }
 * ```
 *
 * ### Resource Management
 * ```kotlin
 * reactiveSuspending {
 *     val config = configReactive()
 *     val resource = acquireResource(config)
 *
 *     onRemove {
 *         // Always called when scope is cancelled
 *         resource.release()
 *     }
 *
 *     useResource(resource)
 * }
 * ```
 *
 * ### Nested Contexts with Independent Lifecycles
 * ```kotlin
 * reactive {
 *     val outerDep = outerSignal()
 *
 *     // Inner context has its own lifecycle
 *     reactive {
 *         val innerDep = innerSignal()
 *         // Reruns only when innerSignal changes
 *     }
 *
 *     // This reruns when outerSignal changes (and recreates inner context)
 * }
 * ```
 *
 * ## Implementation Details
 *
 * This interface is implemented by:
 * - [TypedReactiveContext]: Non-suspending reactive calculations
 * - [ReactiveContextSuspending]: Suspending reactive calculations
 *
 * Both implementations:
 * - Maintain a `job` field that is cancelled and recreated on each calculation run
 * - Combine the parent scope's context with the calculation-specific job
 * - Provide the combined context via [coroutineContext]
 *
 * The job is intentionally NOT a child of the parent scope's job to prevent calculation
 * exceptions from propagating to the parent. Instead, cancellation is managed through
 * explicit hooks registered with [onRemove].
 *
 * @see TypedReactiveContext
 * @see ReactiveContextSuspending
 * @see onRemove
 */
public sealed interface ReactiveCoroutineScope : CoroutineScope

@Deprecated("No longer needed", ReplaceWith("CoroutineScope"))
typealias CalculationContext = CoroutineScope

/**
 * Checks whether this [CoroutineScope]'s dispatcher is a main thread dispatcher.
 *
 * Returns true if the scope's dispatcher is [MainCoroutineDispatcher], false otherwise.
 * This is useful for determining whether code is running on the main/UI thread.
 *
 * Example:
 * ```kotlin
 * val scope = CoroutineScope(Dispatchers.Main)
 * if (scope.requireMainThread) {
 *     // Safe to update UI directly
 * }
 * ```
 */
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

/**
 * DSL marker for reactive context APIs.
 *
 * This annotation marks classes and interfaces that are part of the reactive DSL,
 * helping prevent accidental nesting of reactive contexts and providing better IDE support.
 */
@DslMarker
annotation class ReactiveDsl