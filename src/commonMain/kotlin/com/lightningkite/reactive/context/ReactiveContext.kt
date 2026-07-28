@file:OptIn(InternalReactiveApi::class)

package com.lightningkite.reactive.context

import com.lightningkite.reactive.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

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
public typealias ReactiveContext = TypedReactiveContext<*>

/**
 * Implements the core logic for a single reactive calculation, managing its dependencies and lifecycle.
 *
 * [TypedReactiveContext] tracks all [Reactive] and [Listenable] dependencies accessed during the execution of its [action] lambda.
 * When any dependency changes, the context automatically reruns the calculation, ensuring the result is always up-to-date. This also
 * behaves as a [Reactive] of the resulting value of the computation.
 *
 * ### Calculation Lifecycle
 *   - The [action] lambda is executed, and its result is reported and communicated as the state of this [TypedReactiveContext].
 *   - If [useLastWhileLoading] is true, the previous value is preserved during recalculation; otherwise, the state becomes [ReactiveState.notReady] when dependencies change.
 *   - The context can be cancelled via [cancel], which stops further calculations and releases all listeners/resources.
 *   - The context inherits its lifetime from the provided [scope] (usually a [CoroutineScope]).
 *
 * ### Implementation Details
 * - **Dependency Tracking:**
 *   - All [Reactive] and [Listenable] values accessed via the provided operators (e.g., `invoke()`, `once()`, etc.) are registered as dependencies.
 *   - Each dependency registers a listener that triggers [rerun], queuing a recalculation.
 *   - Dependency tracking is managed by [DependencyTracker], which ensures only relevant dependencies are tracked and cleaned up.
 *
 * - **Loading State Behavior:**
 *   - When [useLastWhileLoading] = false (default): State may become notReady when dependencies become not ready
 *   - When [useLastWhileLoading] = true: Previous value remains visible until new ready value is available
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
 * ### ReactiveCoroutineScope Behavior
 *
 * This class implements [ReactiveCoroutineScope], providing automatic lifecycle management for
 * coroutines launched during the calculation:
 *
 * - **Job Recreation**: The internal job field is cancelled and replaced with a fresh Job
 *   on each call to [startCalculation]. This ensures complete isolation between calculation runs.
 *
 * - **Coroutine Cancellation**: Any coroutines launched within the calculation are automatically
 *   cancelled when dependencies change, preventing stale computations from continuing.
 *
 * - **Cleanup Handlers**: Use [onRemove] to register cleanup handlers that execute when the
 *   calculation scope is cancelled (either due to dependency changes or parent scope cancellation).
 *
 * Example with coroutine launch:
 * ```kotlin
 * val trigger = Signal(0)
 * reactive {
 *     val value = trigger()
 *     launch {
 *         delay(100)
 *         println("Completed: $value")
 *     }
 *     onRemove { println("Cancelled: $value") }
 * }
 *
 * // Output: "Cancelled: 0" (previous run cancelled)
 * //         "Completed: 1" (new run completes)
 * trigger.value = 1
 * ```
 *
 * **Nested Reactive Contexts**:
 *
 * Reactive contexts can be nested, with each maintaining independent lifecycle:
 *
 * ```kotlin
 * val outer = Signal(Signal(0))
 * reactive {
 *     val inner = outer()
 *     reactive {
 *         println("Inner value: ${inner()}")
 *     }
 * }
 * ```
 *
 * - When `inner.value` changes: Only the inner reactive context reruns
 * - When `outer.value` changes: The outer context reruns, cancelling and recreating both
 *   the outer scope and the nested inner context
 *
 * This works because each context has its own independent job, and cancelling the outer
 * job automatically cancels any nested contexts created within that scope.
 *
 * See [ReactiveCoroutineScope] for comprehensive lifecycle documentation.
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
 * ### Self-Triggering Calculations
 *
 * A calculation that changes one of its own dependencies - most often by writing to a signal it
 * also reads - asks to be rerun while it is still running. [reentrancyLimit] decides what happens:
 *
 * - `0` (the default) treats it as the mistake it usually is, reporting a
 *   [ReactiveReentrancyException] as this context's state.
 * - A positive limit lets the calculation *settle*: it reruns in place, publishing each
 *   intermediate result, until it stops changing its own inputs or the limit is spent. Use this
 *   only for calculations that visibly converge, such as one that clamps a value into range.
 *
 * Either way the calculation never recurses, so a runaway cycle surfaces as a named error rather
 * than a stack overflow (or, under a dispatching scheduler, a silent livelock).
 *
 * @param T The type of value produced by the calculation.
 * @property scope The coroutine scope for calculations.
 * @property useLastWhileLoading Whether to preserve the last value during recalculation (true) or show loading state (false).
 * @property reentrancyLimit How many times the calculation may trigger its own re-execution before
 *   it is reported as a [ReactiveReentrancyException]. Zero, the default, forbids self-triggering.
 * @property reportTo The underlying [RawReactive] to report state updates to.
 * @property action The calculation logic to execute in this context.
 */
public class TypedReactiveContext<T>(
    public val scope: CoroutineScope,
    public val useLastWhileLoading: Boolean = false,
    public val reentrancyLimit: Int = 0,
    private val reportTo: RawReactive<T> = RawReactive(),
    public val action: TypedReactiveContext<T>.() -> T
) : DependencyChangeListener(), ReactiveCoroutineScope, Reactive<T> by reportTo {
    public companion object {}

    /**
     * Whether this context is currently active and tracking dependencies.
     * Set to false when [cancel] is called.
     */
    public var active: Boolean = false
        private set

    /**
     * Reference to [startCalculation] used as a listener callback.
     * Dependencies invoke this when they change to trigger recalculation.
     *
     * Invariant: a dependency registered *during* a run must not use this to deliver its initial
     * value. It must hand that value to the run that registered it instead - see how the `async`,
     * `Deferred.invoke` and `Flow.invoke` operators below arrange to do so. A value that first
     * arrives through this path while the calculation is still running is indistinguishable from
     * the calculation triggering itself, and so spends [reentrancyLimit].
     */
    public val rerun: () -> Unit = ::startCalculation

    /**
     * The current job for this calculation run.
     * Gets cancelled and replaced with a new job on each [startCalculation] call.
     *
     * Note: This doesn't need to be a child job because we add a hook in the init block
     * to cancel on parent cancellation, and we don't want exceptions in the calculation
     * to propagate to the parent scope.
     */
    private var job = Job()

    /**
     * The coroutine context for this reactive calculation, combining:
     * - Parent scope's context (dispatcher, etc.)
     * - Current calculation's job (for cancellation control)
     * - This context itself (as DependencyChangeListener)
     */
    override val coroutineContext: CoroutineContext get() = scope.coroutineContext + job + this

    /**
     * A recalculation has been requested but not yet performed. Set when a dependency changes and
     * cleared as each run begins, so that several changes arriving before the run happens collapse
     * into one, and so that a change arriving *during* a run is picked up by the settling loop in
     * [startCalculation] rather than recursing.
     */
    private var queued = false

    /**
     * True while this context's [action] is executing. A recalculation requested while this is set
     * came from the calculation itself - directly, by writing a signal it also reads, or by way of
     * something it triggered. That is what [reentrancyLimit] governs.
     */
    private var calculating = false

    /**
     * Starts or restarts the reactive calculation.
     *
     * When no calculation is running, this cancels the previous run's coroutines, then runs the
     * calculation on the scope's thread: dependencies accessed during [action] are tracked, the
     * result is reported as this context's state, and dependencies that went unused are released.
     *
     * When a calculation *is* running, the request is left for that run's settling loop instead of
     * recursing into a new one. The calculation reruns in place until it stops re-triggering
     * itself, publishing each intermediate result as it goes. [reentrancyLimit] bounds how many
     * times it may do so; beyond that the context reports a [ReactiveReentrancyException] rather
     * than recursing until the stack overflows, or livelocking under a dispatching scheduler. The
     * default limit of zero forbids self-triggering outright.
     */
    public fun startCalculation() {
        active = true
        if (queued) return // Prevent duplicate queuing; the pending run will see the new state
        queued = true
        // Requested from inside the calculation. The settling loop below picks this up on its next
        // pass; we must not cancel `job` here, as it holds the coroutines of the run in progress.
        if (calculating) return

        // The previous run's inputs have changed, so anything it launched is now producing stale
        // results. Cancel at the moment of the change rather than when the rerun is dispatched.
        endRun()

        scope.onThread {
            if (!active) return@onThread // Check if cancelled while queued

            var runs = 0
            while (queued) {
                queued = false
                if (runs++ > reentrancyLimit) {
                    reportTo.state =
                        ReactiveState.exception(ReactiveReentrancyException(this, reentrancyLimit))
                    break
                }
                try {
                    calculating = true
                    dependencyBlockStart() // Begin tracking dependencies
                    val state = reactiveState { action(this@TypedReactiveContext) }

                    // Update state unless useLastWhileLoading is true and result isn't ready
                    if (!useLastWhileLoading || state.ready) reportTo.state = state
                } finally {
                    dependencyBlockEnd() // Clean up dependencies not used in this run
                    calculating = false
                }
                // The calculation re-triggered itself, so this run's coroutines are stale for the
                // same reason any other superseded run's are.
                if (queued) endRun()
            }
        }
    }

    /** Cancels the current run's coroutines and opens a fresh job for the next run. */
    private fun endRun() {
        job.cancel()
        job = Job()
    }

    init {
        // Automatically cancel when parent scope is cancelled
        scope.onRemove { cancel() }
    }

    /**
     * Called by the dependency tracker when a dependency changes.
     * Triggers a recalculation with [startCalculation].
     */
    override fun onDependencyChange() {
        startCalculation()
    }

    /**
     * Called by the dependency tracker when a dependency becomes not ready.
     * If [useLastWhileLoading] is false or current state is already ready, updates state to notReady.
     */
    override fun onDependencyNotReady() {
        if (!useLastWhileLoading || state.ready) reportTo.state = ReactiveState.notReady
    }

    /**
     * Cancels this reactive context, stopping all calculations and releasing resources.
     *
     * This method:
     * 1. Cancels the current context job
     * 2. Marks the context as inactive
     * 3. Clears any queued recalculation
     * 4. Cancels all dependency listeners (via super.cancel())
     *
     * After cancellation, the context will not respond to dependency changes.
     */
    override fun cancel() {
        endRun()
        active = false
        queued = false
        super.cancel() // Cancel dependency listeners
    }

    //////////////////////////////////////////////////////////////////////////////////
    // Everything after this point only uses public API from above.
    // Eventually, this should use context receivers.  However, that's not stable yet.
    //////////////////////////////////////////////////////////////////////////////////

    // Operators for standard reactive tools
    /**
     * Starts using this [ResourceUse] and tracks it as a dependency in future loops.
     * */
    public fun use(resourceUse: ResourceUse) {
        if (existingDependency(resourceUse) != null) return
        registerDependency(resourceUse, resourceUse.beginUse())
    }

    /**
     * Registers a [Listenable] as a dependency, causing this context to rerun when it changes.
     *
     * This is useful for custom dependency types or when you want to listen to changes
     * without accessing a value.
     *
     * Example:
     * ```kotlin
     * reactive {
     *     rerunOn(customListenable)
     *     // Calculation will rerun whenever customListenable changes
     * }
     * ```
     */
    public fun rerunOn(listenable: Listenable) {
        if (existingDependency(listenable) != null) return
        registerDependency(listenable, listenable.addListener(rerun))
    }

    /**
     * Helper function to extract a value from a [ReactiveState], throwing [ReactiveLoading]
     * if the state is not ready.
     *
     * This allows the reactive calculation to be paused until dependencies become ready.
     *
     * A notActive dependency counts as loading rather than as an error: every operator here
     * registers its listener before reading, so the dependency is being maintained by the time we
     * see it, and any notActive we do observe is a source part-way through activating. The
     * listener is in place, so the calculation reruns when its value arrives.
     */
    private fun <R> ReactiveState<R>.getOrLoading() =
        handle(
            success = { it },
            exception = { throw it },
            notReady = { throw ReactiveLoading }
        )

    /**
     * Accesses the current value of this [Reactive] and registers it as a dependency.
     *
     * If the reactive value is not ready, throws [ReactiveLoading] to pause the calculation
     * until the value becomes available.
     *
     * Example:
     * ```kotlin
     * reactive {
     *     val value = myReactive() // Registers myReactive as dependency
     *     println(value)
     * }
     * ```
     *
     * @return The current value of this reactive
     * @throws ReactiveLoading if the value is not ready
     */
    public operator fun <R> Reactive<R>.invoke(): R {
        if (existingDependency(this) == null) {
            registerDependency(this, addListener(rerun))
        }
        return state.getOrLoading()
    }

    /**
     * Accesses the current value of this nullable [Reactive], waiting until it becomes non-null.
     *
     * This is useful when a reactive value starts as null but you want to wait for a non-null value
     * before proceeding with the calculation.
     *
     * Example:
     * ```kotlin
     * reactive {
     *     val user = userReactive.awaitNotNull() // Waits for non-null user
     *     println(user.name)
     * }
     * ```
     *
     * @return The non-null value
     * @throws ReactiveLoading if the value is null or not ready
     */
    public fun <R> Reactive<R?>.awaitNotNull(): R {
        if (existingDependency(this) == null) {
            registerDependency(this, addListener(rerun))
        }
        return state.getOrLoading() ?: throw ReactiveLoading
    }

    /**
     * Accesses the [ReactiveState] of this [Reactive] and registers it as a dependency.
     *
     * Unlike [invoke], this returns the full state including loading and error states,
     * allowing you to handle them explicitly.
     *
     * Example:
     * ```kotlin
     * reactive {
     *     myReactive.state().handle(
     *         success = { value -> println(value) },
     *         exception = { error -> println("Error: $error") },
     *         notReady = { println("Loading...") }
     *     )
     * }
     * ```
     *
     * @return The current [ReactiveState]
     */
    public fun <R> Reactive<R>.state(): ReactiveState<R> {
        if (existingDependency(this) == null) {
            registerDependency(this, addListener(rerun))
        }
        return state
    }

    /**
     * Accesses a transformed version of this [Reactive]'s state and registers a smart dependency.
     *
     * This is an optimization that only triggers reruns when the transformed value actually changes,
     * not when the underlying reactive changes to a different state with the same transformed value.
     *
     * Example:
     * ```kotlin
     * reactive {
     *     val isReady = myReactive.state { it.ready }
     *     // Only reruns when ready state changes, not on every state update
     * }
     * ```
     *
     * @param get Function to extract a value from the [ReactiveState]
     * @return The transformed value
     */
    public fun <R, V> Reactive<R>.state(get: (ReactiveState<R>) -> V): V {
        var current: V = state.let(get)
        if (existingDependency(this) == null) {
            registerDependency(this, addListener {
                state.let(get)
                    .takeUnless { it == current }
                    ?.let {
                        current = it
                        rerun()
                    }
            })
            // Repull in case of activation: a lazy source calculates inside addListener, before
            // our listener is in place, so the value read above can predate that calculation.
            current = state.let(get)
        }
        return current
    }

    /**
     * Wrapper class for tracking "once" dependencies.
     * This creates a unique key for each reactive value accessed via [once].
     */
    private data class Once<T>(val wraps: Reactive<T>) {
        /** Whether a value has been obtained; changes stop causing reruns once one has. */
        var have = false
    }

    /**
     * Accesses the value of this [Reactive] once, without rerunning when it later changes.
     *
     * This is useful when you want to wait for an initial value but don't want subsequent
     * changes to trigger reruns.
     *
     * Example:
     * ```kotlin
     * reactive {
     *     val initialConfig = configReactive.once()
     *     // Calculation won't rerun when config changes after first load
     * }
     * ```
     *
     * @return The value once it's ready
     * @throws ReactiveLoading if the value is not ready yet
     */
    public fun <T> Reactive<T>.once(): T {
        val key = existingDependency(Once(this)) ?: Once(this).also { key ->
            registerDependency(key, addListener { if (!key.have) rerun() })
        }
        val state = state
        key.have = state.ready
        return state.getOrLoading()
    }

    // Hack: fixes compiler weirdness around lambdas with 'this'
    @Suppress("NOTHING_TO_INLINE")
    public inline operator fun <T> (ReactiveContext.() -> T).invoke(): T = invoke(this@TypedReactiveContext)

    @Suppress("NOTHING_TO_INLINE")
    public inline operator fun <A, T> (ReactiveContext.(A) -> T).invoke(a: A): T = invoke(this@TypedReactiveContext, a)

    @Suppress("NOTHING_TO_INLINE")
    public inline operator fun <A, B, T> (ReactiveContext.(A, B) -> T).invoke(a: A, b: B): T = invoke(this@TypedReactiveContext, a, b)

    @Deprecated("Just use the invoke operator", ReplaceWith("this()"))
    public fun <T> Reactive<T>.await(): T = invoke()

    @Deprecated("Just use the once function", ReplaceWith("this.once()"))
    public fun <T> Reactive<T>.awaitOnce(): T = once()

    // Suspending calculations

    /**
     * Internal reactive wrapper for suspending calculations.
     *
     * This class acts as a stable key for caching async results based on their dependencies.
     * Multiple calls with the same key will reuse the same calculation.
     */
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

    /**
     * Launches a suspending calculation and caches its result based on the provided dependencies.
     *
     * The calculation is executed in a coroutine and its result is tracked reactively.
     * Multiple calls with the same dependencies will reuse the cached result.
     *
     * Example:
     * ```kotlin
     * reactive {
     *     val userId = userIdReactive()
     *     val userData = async(userId) {
     *         fetchUserFromApi(userId) // Only refetches when userId changes
     *     }
     * }
     * ```
     *
     * @param identity The identifier for uniqueness on this item.
     * @param dependencies Values that uniquely identify this calculation (changes trigger recalculation)
     * @param action The suspending function to execute
     * @return The result of the calculation once complete
     * @throws ReactiveLoading if the calculation is not yet complete
     */
    public fun <T> async(identity: String, vararg dependencies: Any?, action: suspend () -> T): T {
        // action::class distinguishes call sites: two different `async(...) { }` call sites compile
        // to distinct anonymous classes, so folding it into the key stops two call sites that
        // happen to pass identical dependencies from colliding on the same cached calculation.
        val key = setOf(identity, *dependencies)
        val calc = SuspendCalculation<T>(key)

        // Reuse existing calculation if already running
        existingDependency(calc)?.let {
            return it.state.getOrLoading()
        }

        // Launch new calculation and register as dependency. The launch happens before the
        // listener is attached so a synchronously-completing action (e.g. under an Unconfined
        // dispatcher) can't re-enter rerun() while this calculation is still in progress.
        val job = scope.launch {
            calc.state = reactiveState { action() }
        }
        val removeListener = calc.addListener(rerun)
        registerDependency(calc) {
            // A rerun means this calculation is no longer used - cancel it so stale work
            // doesn't keep running and overwrite `calc` after it's been orphaned.
            removeListener()
            job.cancel()
        }

        return calc.state.getOrLoading()
    }

    /**
     * Awaits the result of this [Deferred] and tracks it as a reactive dependency.
     *
     * The deferred value is awaited in a coroutine, and the calculation reruns when
     * the deferred completes.
     *
     * Example:
     * ```kotlin
     * reactive {
     *     val deferred = scope.async { fetchData() }
     *     val result = deferred() // Waits for deferred and tracks it
     * }
     * ```
     *
     * @return The deferred value once available
     * @throws ReactiveLoading if the deferred is not yet complete
     */
    public operator fun <T> Deferred<T>.invoke(): T {
        val calc = SuspendCalculation<T>(this)

        // Reuse existing calculation if already running
        existingDependency(calc)?.let {
            return it.invoke()
        }

        // Launch await operation and register as dependency. Same ordering and cancellation
        // rationale as `async` above: launch before attaching the listener, and cancel the
        // launch when this dependency is no longer used.
        val job = scope.launch {
            calc.state = reactiveState { this@invoke.await() }
        }
        val removeListener = calc.addListener(rerun)
        registerDependency(calc) {
            removeListener()
            job.cancel()
        }

        return calc.state.getOrLoading()
    }


    // Flows

    /**
     * Internal wrapper for tracking Flow emissions as reactive dependencies.
     *
     * This class maintains the latest state emitted by a Flow and serves as a stable
     * key for the dependency tracker.
     */
    private class FlowLoader<T>(val flow: Flow<T>) {
        var state: ReactiveState<T> = ReactiveState.notReady
        override fun hashCode(): Int = flow.hashCode()
        override fun equals(other: Any?): Boolean = other is FlowLoader<*> && flow == other.flow
        override fun toString(): String = "${super.toString()}/$flow"
    }

    /**
     * Collects the latest value from this [Flow] and tracks it as a reactive dependency.
     *
     * The flow is collected in a coroutine, and each emission triggers a recalculation.
     * For [StateFlow], the current value is returned immediately; for other flows, the
     * calculation waits for the first emission.
     *
     * Example:
     * ```kotlin
     * reactive {
     *     val value = myFlow() // Reruns on each flow emission
     *     println(value)
     * }
     * ```
     *
     * @return The latest emitted value
     * @throws ReactiveLoading if no value has been emitted yet (except for StateFlow)
     */
    public operator fun <T> Flow<T>.invoke(): T {
        val new = FlowLoader(this)

        val existing = existingDependency(new)
        if (existing == null) {
            var job: Job? = null
            // Register cleanup to cancel collection when dependency is removed
            registerDependency(new, { job?.cancel() })

            // An emission that arrives while we are still registering (a hot flow under an
            // undispatched scheduler collects synchronously) belongs to this run, so it is
            // delivered by the return below rather than through rerun(). A dependency's initial
            // value is not the calculation triggering itself, and pushing it through rerun()
            // makes it indistinguishable from reentrancy. Same rationale as the
            // launch-before-listen ordering in `async` above.
            var registering = true
            job = scope.launch {
                collect { v ->
                    try {
                        new.state = ReactiveState(v)
                        if (!registering) rerun() // Later emissions trigger recalculation
                    } catch (e: Exception) {
                        new.state = ReactiveState.exception<T>(e)
                    }
                }
            }
            registering = false

            // StateFlow always has a current value available immediately
            if (this is StateFlow<T>) return this.value
            // A flow that emitted during registration already has a value for this run
            else return new.state.getOrLoading()
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
 * The [action] lambda is executed in a tracked context, automatically registering all
 * [Reactive] and [Listenable] dependencies accessed. Whenever any dependency changes,
 * the calculation is rerun, keeping the result up-to-date.
 *
 * ## ReactiveCoroutineScope
 *
 * [ReactiveContext] is a [ReactiveCoroutineScope], which provides:
 *
 * - **Automatic Cancellation**: Coroutines launched within [action] are automatically
 *   cancelled when dependencies change
 * - **Fresh Scope Per Run**: Each calculation run gets a new scope with a fresh Job
 *
 * Example with coroutine launch:
 * ```kotlin
 * val dataSource = Signal(listOf(1, 2, 3))
 *
 * reactive {
 *     val data = dataSource()
 *
 *     // Launch background processing
 *     launch {
 *         // This action will be cancelled and recreated if `data`, or any other dependency, changes.
 *         processData(data)
 *     }
 * }
 * ```
 *
 * ## Nested Reactive Contexts
 *
 * You can create nested reactive contexts, each with independent lifecycle:
 *
 * ```kotlin
 * val outer = Signal(Signal(0))
 *
 * reactive {
 *     val inner = outer()
 *
 *     // Nested context - reruns only when inner changes
 *     reactive {
 *         val value = inner()
 *         println("Inner: $value")
 *     }
 * }
 * ```
 *
 * When the outer signal changes, both the outer and inner contexts are cancelled and recreated.
 * When only the inner signal changes, only the inner context reruns.
 *
 * @param reentrancyLimit How many times [action] may trigger its own re-execution - by changing a
 *   dependency it also reads - before the calculation is reported as failed with a
 *   [ReactiveReentrancyException]. Leave at zero unless the calculation is meant to settle over
 *   several runs; see [TypedReactiveContext] for the trade-off.
 * @param action The calculation logic to run reactively
 * @return A [TypedReactiveContext] managing the calculation and its dependencies
 *
 * @see ReactiveCoroutineScope for detailed lifecycle documentation
 * @see TypedReactiveContext for implementation details
 * @see reactiveSuspending for suspending calculations
 */
public fun <T> CoroutineScope.reactive(reentrancyLimit: Int = 0, action: ReactiveContext.() -> T): TypedReactiveContext<T> {
    val trc = TypedReactiveContext(this, reentrancyLimit = reentrancyLimit, action = action)
    trc.startCalculation()
    coroutineContext[StatusListener]?.watchBackgroundProcess(trc)
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
public inline fun CoroutineScope.reactive(crossinline onLoad: () -> Unit, crossinline action: ReactiveContext.() -> Unit): TypedReactiveContext<Unit> {
    var wasLoadingLastTime = false
    return reactive {
        try {
            action(this)
            wasLoadingLastTime = false
        } catch (e: ReactiveLoading) {
            if (!wasLoadingLastTime) {
                wasLoadingLastTime = true
                onLoad()
            }
            throw e
        } catch (e: Exception) {
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
@Deprecated("renamed to 'reactive'", ReplaceWith("this.reactive(action)"))
public fun CoroutineScope.reactiveScope(action: ReactiveContext.() -> Unit): ReactiveContext = reactive(action = action)

/**
 * Creates a [ReactiveContext] in which to run the provided [action] reactively, discarding the result, with support for loading state.
 *
 * This is a convenience wrapper for [reactive], used when the result of the calculation is not needed.
 * If the calculation enters a loading state, the [onLoad] callback is invoked.
 *
 * @param onLoad Callback invoked when the calculation enters a loading state.
 * @param action The calculation logic to run reactively.
 */
@Deprecated("renamed to 'reactive'", ReplaceWith("this.reactive(onLoad, action)"))
public inline fun CoroutineScope.reactiveScope(crossinline onLoad: () -> Unit, crossinline action: ReactiveContext.() -> Unit): ReactiveContext = reactive(onLoad = onLoad, action = action)

@InternalReactiveApi
public object ReactiveLoading : Throwable()

/**
 * Thrown when a reactive calculation triggers its own re-execution, typically by writing to a
 * signal it also reads inside the same [reactive] block. This would otherwise recurse until the
 * stack overflows (or livelock under a dispatching scheduler), so it is surfaced as a clear error.
 */
public class ReactiveReentrancyException(context: ReactiveContext, reentrancyLimit: Int) : IllegalStateException(
    if (reentrancyLimit <= 0)
        "A reactive calculation triggered its own re-execution ($context). This usually means the " +
                "calculation wrote to a signal it also reads. Break the cycle so the calculation " +
                "does not mutate its own dependencies. If it is instead meant to settle over " +
                "several runs, raise reentrancyLimit to the number of reruns it needs."
    else
        "A reactive calculation kept triggering its own re-execution and did not settle within " +
                "its reentrancyLimit of $reentrancyLimit reruns ($context). Either it never " +
                "converges - every run changes a signal it reads, so there is no stable value to " +
                "reach - or it genuinely needs a higher limit."
)
