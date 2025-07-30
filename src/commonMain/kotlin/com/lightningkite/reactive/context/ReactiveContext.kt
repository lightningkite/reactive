@file:OptIn(InternalReactiveApi::class)

package com.lightningkite.reactive.context

import com.lightningkite.reactive.core.BaseReactive
import com.lightningkite.reactive.core.RawReactive
import com.lightningkite.reactive.core.InternalReactiveApi
import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.reactiveState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
var reactiveContext: ReactiveContext? = null

/**
 * [ReactiveContext] provides an environment for observing and reacting to changes in [Reactive] or [Listenable] values.
 *
 * It automatically tracks dependencies accessed during a calculation and reruns the calculation whenever any dependency changes.
 *
 * - [Reactive] Dependencies are registered when accessed using the `invoke()` operator.
 * - Long loading times and error states are automatically handled and reported.
 * - The context's lifetime is tied to a [CoroutineScope]. When the scope ends, all resources are released and listeners are stopped.
 * - Only calculations affected by changed dependencies are rerun, ensuring efficient updates.
 * - The context can be cancelled to stop further calculations and clean up resources.
 *
 * Example usage:
 * ```kotlin
 * val r1: Reactive<Int> = // ...
 * val r2: Reactive<String> = // ...
 *
 * val context = reactive {
 *     println("Value1 is: ${r1()}, value2 is: ${r2()}")
 * }
 * ```
 */
typealias ReactiveContext = TypedReactiveContext<*>

/**
 * Implements the core logic for a single reactive calculation, managing its dependencies and lifecycle.
 *
 * [TypedReactiveContext] tracks all [Reactive] and [Listenable] dependencies accessed during the execution of its [action] lambda.
 * When any dependency changes, the context automatically reruns the calculation, ensuring the result is always up-to-date.
 *
 * ### Implementation Details
 * - **Dependency Tracking:**
 *   - When [startCalculation] is called, the context sets itself as the current [reactiveContext] (thread-local).
 *   - All [Reactive] and [Listenable] values accessed via the provided operators (e.g., `invoke()`, `once()`, etc.) are registered as dependencies.
 *   - Each dependency registers a listener that triggers [rerun], queuing a recalculation.
 *   - Dependency tracking is managed by [DependencyTracker], which ensures only relevant dependencies are tracked and cleaned up.
 *
 * - **Calculation Lifecycle:**
 *   - The [action] lambda is executed inside [startCalculation], and its result is reported to [reportTo] (a [RawReactive]).
 *   - If [useLastWhileLoading] is true, the previous value is used while new results are loading; otherwise, the state is updated only when ready.
 *   - The context can be cancelled via [cancel], which stops further calculations and releases all listeners/resources.
 *   - The context inherits its lifetime from the provided [CalculationContext] (usually a [CoroutineScope]).
 *
 * - **Operators:**
 *   - `Reactive<T>.invoke()`: Returns the current value and registers the dependency.
 *   - `Reactive<T>.once()`: Returns the value once, then unregisters the dependency.
 *   - `Flow<T>.invoke()`: Returns the latest value from a [Flow], registering the dependency.
 *   - `Deferred<T>.invoke()`: Returns the value from a [Deferred], registering the dependency.
 *
 * - **Advanced Features:**
 *   - Suspending calculations can be performed using [async] and [Deferred.invoke].
 *   - The context supports custom dependency registration via [rerunOn].
 *   - The context can be used with lambdas that take parameters, using the provided operator overloads.
 *
 * ### Example Usage
 * ```kotlin
 * val context = TypedReactiveContext(scope) {
 *     val a = someReactive()
 *     val b = anotherReactive()
 *     a + b
 * }
 * context.startCalculation()
 * ```
 *
 * @param T The type of value produced by the calculation.
 * @property scope The coroutine context for calculations.
 * @property useLastWhileLoading Whether to use the last value while loading new results.
 * @property reportTo The underlying [RawReactive] to report state updates to.
 * @property action The calculation logic to execute in this context.
 */
class TypedReactiveContext<T>(
    val scope: CalculationContext,
    val useLastWhileLoading: Boolean = false,
    private val reportTo: RawReactive<T> = RawReactive(),
    val action: TypedReactiveContext<T>.() -> T
): DependencyTracker(), CalculationContext by scope, Reactive<T> by reportTo {
    companion object

    var active = false
        private set
    val rerun: () -> Unit = ::startCalculation

    private var queued = false

    fun startCalculation() {
        active = true
        if (queued) return
        queued = true
        scope.onThread {
            queued = false
            if (!active) {
                return@onThread
            }
            val old = reactiveContext
            reactiveContext = this
            dependencyBlockStart()
            val state = reactiveState { action(this@TypedReactiveContext) }
            if (!useLastWhileLoading || state.ready) reportTo.state = state
            dependencyBlockEnd()
            reactiveContext = old
        }
    }

    fun runOnceWhileDead() {
        val state = reactiveState { action(this) }
        if (!useLastWhileLoading || state.ready) reportTo.state = state
    }

    init {
        scope.onRemove { cancel() }
    }

    override fun cancel() {
        active = false
        queued = false
        super.cancel()
    }

    //////////////////////////////////////////////////////////////////////////////////
    // Everything after this point only uses public API from above.
    // Eventually, this should use context receivers.  However, that's not stable yet.
    //////////////////////////////////////////////////////////////////////////////////

    // Operators for standard reactive tools
    fun rerunOn(listenable: Listenable) {
        if (existingDependency(listenable) != null) return
        registerDependency(listenable, listenable.addListener(rerun))
    }

    operator fun <R> Reactive<R>.invoke(): R {
        if (existingDependency(this) == null) {
            registerDependency(this, addListener(rerun))
        }
        return state.handle(
            success = { it },
            exception = { throw it },
            notReady = { throw ReactiveLoading }
        )
    }

    fun <R> Reactive<R?>.awaitNotNull(): R {
        if (existingDependency(this) == null) {
            registerDependency(this, addListener(rerun))
        }
        return state.handle(
            success = { it ?: throw ReactiveLoading },
            exception = { throw it },
            notReady = { throw ReactiveLoading }
        )
    }

    fun <R> Reactive<R>.state(): ReactiveState<R> {
        if (existingDependency(this) == null) {
            registerDependency(this, addListener(rerun))
        }
        return state
    }

    private data class Once<T>(val wraps: Reactive<T>)

    fun <T> Reactive<T>.once(): T {
        val key = Once(this)
        if (existingDependency(key) == null) {
            var remover: () -> Unit = {}
            remover = addListener {
                remover()
                rerun()
            }
            registerDependency(key, remover)
        }
        return state.handle(
            success = { it },
            exception = { throw it },
            notReady = { throw ReactiveLoading }
        )
    }

    // Hack: fixes compiler weirdness around lambdas with 'this'
    @Suppress("NOTHING_TO_INLINE")
    inline operator fun <T> (ReactiveContext.() -> T).invoke(): T = invoke(this@TypedReactiveContext)

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun <A, T> (ReactiveContext.(A) -> T).invoke(a: A): T = invoke(this@TypedReactiveContext, a)

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun <A, B, T> (ReactiveContext.(A, B) -> T).invoke(a: A, b: B): T = invoke(this@TypedReactiveContext, a, b)

    @Deprecated("Just use the invoke operator", ReplaceWith("this()"))
    fun <T> Reactive<T>.await(): T = invoke()

    @Deprecated("Just use the once function", ReplaceWith("this.once()"))
    fun <T> Reactive<T>.awaitOnce(): T = once()


    // Suspending calculations

    private class SuspendCalculation<T>(val key: Any) : BaseReactive<T>() {
        override var state: ReactiveState<T>
            get() = super.state
            public set(value) {
                super.state = value
            }

        override fun equals(other: Any?): Boolean = other is SuspendCalculation<*> && other.key == key
        override fun hashCode(): Int = key.hashCode() + 1
        override fun toString(): String = "SuspendCalculation($key)"
    }

    fun <T> async(vararg dependencies: Any?, action: suspend () -> T): T {
        val key = setOf(*dependencies)
        val calc = SuspendCalculation<T>(key)
        existingDependency(calc)?.let {
            return it.invoke()
        }
        scope.launch {
            calc.state = reactiveState { action() }
        }
        registerDependency(calc, calc.addListener(rerun))
        return calc.state.handle(
            success = { it },
            exception = { throw it },
            notReady = { throw ReactiveLoading }
        )
    }

    operator fun <T> Deferred<T>.invoke(): T {
        val calc = SuspendCalculation<T>(this)
        existingDependency(calc)?.let {
            return it.invoke()
        }
        scope.launch {
            calc.state = reactiveState { this@invoke.await() }
        }
        registerDependency(calc, calc.addListener(rerun))
        return calc.state.handle(
            success = { it },
            exception = { throw it },
            notReady = { throw ReactiveLoading }
        )
    }


    // Flows

    private class FlowLoader<T>(val flow: Flow<T>) {
        var state: ReactiveState<T> = ReactiveState.notReady
        override fun hashCode(): Int = flow.hashCode()
        override fun equals(other: Any?): Boolean = other is FlowLoader<*> && flow == other.flow
        override fun toString(): String = "${super.toString()}/$flow"
    }

    operator fun <T> Flow<T>.invoke(): T {
        val new = FlowLoader(this)

        val existing = existingDependency(new)
        if (existing == null) {
            var job: Job? = null
            registerDependency(new, { job?.cancel() })
            job = scope.launch {
                collect { v ->
                    try {
                        new.state = ReactiveState(v)
                        rerun()
                    } catch (e: Exception) {
                        new.state = ReactiveState.exception<T>(e)
                    }
                }
            }
            if (this is StateFlow<T>) return this.value
            else throw ReactiveLoading
        } else {
            return existing.state.handle(
                success = { it },
                exception = { throw it },
                notReady = { throw ReactiveLoading }
            )
        }
    }
}

/**
 * Creates a [ReactiveContext] in which to run the provided [action] reactively.
 *
 * The [action] lambda is executed in a tracked context, automatically registering all [Reactive] and [Listenable] dependencies accessed.
 * Whenever any dependency changes, the calculation is rerun, keeping the result up-to-date.
 *
 * @param action The calculation logic to run reactively.
 * @return A [TypedReactiveContext] managing the calculation and its dependencies.
 */
fun <T> CalculationContext.reactive(action: ReactiveContext.() -> T): TypedReactiveContext<T> {
    val trc = TypedReactiveContext(this, action = action)
    trc.startCalculation()
    coroutineContext[StatusListener.Key]?.loading(trc)
    return trc
}

/**
 * Creates a [ReactiveContext] in which to run the provided [action] reactively, with support for loading state.
 *
 * The [action] lambda is executed in a tracked context, automatically registering all [Reactive] and [Listenable] dependencies accessed.
 * If the calculation enters a loading state, the [onLoad] callback is invoked.
 *
 * The returned [TypedReactiveContext] manages the lifecycle and cancellation of the calculation, and is tied to the provided [CalculationContext].
 *
 * Example usage:
 * ```kotlin
 * val context = reactive(onLoad = { println("Loading...") }) {
 *     // calculation logic
 * }
 * ```
 *
 * @param onLoad Callback invoked when the calculation enters a loading state.
 * @param action The calculation logic to run reactively.
 * @return A [TypedReactiveContext] managing the calculation and its dependencies.
 */
inline fun <T> CalculationContext.reactive(crossinline onLoad: () -> Unit, crossinline action: ReactiveContext.() -> Unit): TypedReactiveContext<Unit> {
    var wasLoadingLastTime = false
    return reactive {
        try {
            action(this)
            wasLoadingLastTime = false
        } catch(e: ReactiveLoading) {
            if(wasLoadingLastTime) {
                onLoad()
                wasLoadingLastTime = true
            }
            throw e
        } catch(e: Exception) {
            wasLoadingLastTime = false
        }
    }
}

/**
 * Creates a [ReactiveContext] in which to run the provided [action] reactively, discarding the result.
 *
 * This is a convenience wrapper for [reactive], used when the result of the calculation is not needed.
 *
 * @param action The calculation logic to run reactively.
 */
fun CalculationContext.reactiveScope(action: ReactiveContext.() -> Unit): ReactiveContext = reactive(action = action)

/**
 * Creates a [ReactiveContext] in which to run the provided [action] reactively, discarding the result, with support for loading state.
 *
 * This is a convenience wrapper for [reactive], used when the result of the calculation is not needed.
 * If the calculation enters a loading state, the [onLoad] callback is invoked.
 *
 * @param onLoad Callback invoked when the calculation enters a loading state.
 * @param action The calculation logic to run reactively.
 */
inline fun CalculationContext.reactiveScope(crossinline onLoad: () -> Unit, crossinline action: ReactiveContext.() -> Unit): ReactiveContext = reactive<Unit>(onLoad = onLoad, action = action)

object ReactiveLoading : Throwable()
