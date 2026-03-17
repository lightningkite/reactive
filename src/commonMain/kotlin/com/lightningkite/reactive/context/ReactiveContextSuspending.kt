package com.lightningkite.reactive.context

import com.lightningkite.reactive.core.RawReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.reactiveState
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * [ReactiveContextSuspending] manages the lifecycle and dependency tracking for a single suspending reactive calculation.
 *
 * This class is designed for use with suspending functions, allowing reactive calculations to be performed asynchronously within a [CoroutineScope].
 * It tracks all dependencies accessed during the execution of its [action] lambda and reruns the calculation whenever any dependency changes.
 *
 * ## Implementation Details
 * - **Dependency Tracking:**
 *   - When [startCalculation] is called, the context begins tracking dependencies and launches the calculation in a coroutine.
 *   - All dependencies accessed during the calculation are registered, and listeners are set up to rerun the calculation when they change.
 *   - Dependency tracking is managed by [DependencyChangeListener], ensuring only relevant dependencies are tracked and cleaned up.
 *
 * - **Calculation Lifecycle:**
 *   - The [action] lambda is executed inside a coroutine, and its result is reported to [reportTo] (a [RawReactive]).
 *   - If [useLastWhileLoading] is true, the previous value is preserved while new results are loading; otherwise, the state becomes [ReactiveState.notReady] during recalculation.
 *   - The context can be cancelled via [cancel], stopping further calculations and releasing resources.
 *   - The context inherits its lifetime from the provided [CoroutineScope].
 *
 * - **Loading State Behavior:**
 *   - When [useLastWhileLoading] = false (default): State becomes notReady during async recalculation
 *   - When [useLastWhileLoading] = true: Previous value remains visible until new value is ready
 *
 * - **Operators:**
 *   - Use `reactiveSuspending` extension functions to create instances
 *   - Access reactive values within the action using the dependency tracking mechanism
 *
 * ## ReactiveCoroutineScope Behavior
 *
 * This class implements [ReactiveCoroutineScope], providing the same automatic lifecycle
 * management as [TypedReactiveContext], but optimized for suspending calculations:
 *
 * - **Job Recreation**: The internal job field is cancelled and replaced on each
 *   [startCalculation] call, ensuring isolation between calculation runs.
 *
 * - **Suspending Execution**: The [action] is a suspending function, allowing natural use of
 *   `suspend` functions without explicit [async] wrappers.
 *
 * - **Automatic Cancellation**: When dependencies change, the previous calculation's coroutine
 *   is cancelled via [lastLoopJob], and a new one is launched with a fresh job.
 *
 * Example with suspending operations:
 * ```kotlin
 * val userId = Signal(1)
 * reactiveSuspending {
 *     val id = userId()
 *     val user = fetchUser(id)  // Suspending call
 *
 *     launch {
 *         // Background work cancelled when userId changes
 *         processUser(user)
 *     }
 *
 *     onRemove {
 *         // Cleanup called on cancellation
 *         releaseUserResources(user)
 *     }
 * }
 * ```
 *
 * **Nested Contexts**:
 *
 * Nested contexts work identically to [TypedReactiveContext], with independent Jobs:
 *
 * ```kotlin
 * reactiveSuspending {
 *     val config = await(configLoader)
 *
 *     // Nested suspending context
 *     reactiveSuspending {
 *         val data = await(dataLoader)
 *         processData(config, data)
 *     }
 * }
 * ```
 *
 * Cancellation cascades naturally from outer to inner contexts.
 *
 * See [ReactiveCoroutineScope] for comprehensive lifecycle documentation.
 *
 * @param T The type of value produced by the calculation.
 * @property scope The coroutine scope for calculations.
 * @property useLastWhileLoading Whether to preserve the last value during recalculation (true) or show loading state (false).
 * @property reportTo The underlying [RawReactive] to report state updates to.
 * @property action The suspending calculation logic to execute in this context.
 */
class ReactiveContextSuspending<T>(
    val scope: CoroutineScope,
    val useLastWhileLoading: Boolean = false,
    private val reportTo: RawReactive<T> = RawReactive(),
    val action: suspend ReactiveCoroutineScope.() -> T,
) : DependencyChangeListener(), ReactiveCoroutineScope, Reactive<T> by reportTo {
    /**
     * The job for the current calculation run's coroutine.
     * Null when no calculation is in progress.
     */
    internal var lastLoopJob: Job? = null

    /**
     * Whether this context is currently active and tracking dependencies.
     * Set to false when [cancel] is called.
     */
    var active = false
        private set

    /**
     * The current job for this reactive context.
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
     * Launches a coroutine with optimized start strategy.
     *
     * Uses [CoroutineStart.UNDISPATCHED] when already on the correct dispatcher to avoid
     * unnecessary thread switches, otherwise uses [CoroutineStart.DEFAULT].
     *
     * This optimization allows synchronous execution when possible, which is important
     * for determining whether the calculation completed immediately or needs to run asynchronously.
     */
    private fun CoroutineScope.launchWithStart(block: suspend () -> Unit) =
        launch(
            start =
                if (coroutineContext[CoroutineDispatcher]?.isDispatchNeeded(coroutineContext) == false)
                    CoroutineStart.UNDISPATCHED
                else
                    CoroutineStart.DEFAULT,
        ) { block() }

    /**
     * Starts or restarts the reactive calculation asynchronously.
     *
     * This method:
     * 1. Cancels the previous calculation's jobs (if any)
     * 2. Creates a fresh job for this calculation run
     * 3. Begins tracking dependencies
     * 4. Launches the suspending calculation in a coroutine
     * 5. Updates the reactive state with the result when complete
     * 6. Cleans up unused dependencies
     *
     * The calculation may complete synchronously (if already on correct dispatcher and no suspension points)
     * or asynchronously. If [useLastWhileLoading] is false, the state is set to notReady during async execution.
     *
     * If the calculation has no dependencies after completion, the context is automatically
     * cancelled to release resources (since it will never rerun).
     */
    fun startCalculation() {
        active = true
        lastLoopJob?.cancel() // Cancel previous calculation if still running

        // Cancel previous job and create fresh one
        job.cancel()
        job = Job()

        dependencyBlockStart() // Begin tracking dependencies

        lastLoopJob = this.let { calculationContext ->
            var done = false
            val job = calculationContext.launchWithStart {
                val result = reactiveState { this@ReactiveContextSuspending.action() }
                // Update state unless useLastWhileLoading is true and result isn't ready
                if (!useLastWhileLoading || result.ready) reportTo.state = result
                dependencyBlockEnd() // Clean up dependencies not used in this run
                done = true

                // If no dependencies, this will never rerun, so cancel and release resources
                if (dependencyCount == 0) cancel()
            }

            // Check if calculation completed synchronously
            if (done) return@let null
            else {
                // Calculation is running asynchronously - set loading state if configured
                if (!useLastWhileLoading) reportTo.state = ReactiveState.notReady
                return@let job
            }
        }
    }

    /**
     * Runs the calculation once without activating the context or tracking dependencies.
     *
     * This is useful for getting an initial value or running the calculation in a test
     * environment without setting up the full reactive machinery.
     *
     * The result is still reported to [reportTo], but no dependency tracking occurs and
     * the calculation will not rerun when dependencies change.
     */
    fun runOnceWhileDead() {
        lastLoopJob = run {
            var done = false
            val job = scope.launchWithStart {
                val result = reactiveState { this@ReactiveContextSuspending.action() }
                if (!useLastWhileLoading || result.ready) reportTo.state = result
                done = true
            }

            // Check if calculation completed synchronously
            if (done) null
            else {
                if (!useLastWhileLoading) reportTo.state = ReactiveState.notReady
                job
            }
        }
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
     * If [useLastWhileLoading] is false, immediately updates state to notReady.
     */
    override fun onDependencyNotReady() {
        if (!useLastWhileLoading) reportTo.state = ReactiveState.notReady
    }

    /**
     * Cancels this reactive context, stopping all calculations and releasing resources.
     *
     * This method:
     * 1. Cancels all dependency listeners (via super.cancel())
     * 2. Cancels the current context job
     * 3. Cancels any running calculation coroutine
     * 4. Marks the context as inactive
     *
     * After cancellation, the context will not respond to dependency changes.
     */
    override fun cancel() {
        super.cancel() // Cancel dependency listeners
        job.cancel()
        job = Job()
        active = false
        lastLoopJob?.let {
            lastLoopJob = null
            it.cancel()
        }
    }

    init {
        // Automatically cancel when parent scope is cancelled
        scope.onRemove { cancel() }
    }
}

/**
 * Creates a [ReactiveContextSuspending] to run the provided suspending [action] reactively.
 *
 * The [action] lambda is executed in a tracked context, automatically registering all
 * dependencies accessed. Whenever any dependency changes, the calculation is rerun
 * asynchronously, keeping the result up-to-date.
 *
 * ## ReactiveCoroutineScope
 *
 * The suspending [action] receives a [ReactiveCoroutineScope] as its receiver, which provides:
 *
 * - **Suspending Execution**: The action is a suspend function, allowing natural use of
 *   suspending APIs without explicit [async] wrappers
 * - **Automatic Cancellation**: The calculation coroutine is cancelled when dependencies change
 * - **Fresh Scope Per Run**: Each calculation run gets a new scope with a fresh Job
 * - **Cleanup Handlers**: Use `onRemove { }` to register cleanup code
 *
 * Example with suspending operations:
 * ```kotlin
 * val userId = Signal(1)
 *
 * reactiveSuspending {
 *     val id = userId()
 *     val user = fetchUserFromApi(id)  // Suspending network call
 *
 *     // Launch background work
 *     launch {
 *         cacheUser(user)
 *     }
 *
 *     // Register cleanup
 *     onRemove {
 *         cancelPendingRequests()
 *     }
 *
 *     updateUI(user)
 * }
 * ```
 *
 * When `userId` changes, the previous `fetchUserFromApi` call is cancelled, and a new
 * one is started with the new ID.
 *
 * ## Nested Reactive Contexts
 *
 * Nested suspending contexts work seamlessly:
 *
 * ```kotlin
 * val configId = Signal(1)
 *
 * reactiveSuspending {
 *     val config = fetchConfig(configId())
 *
 *     // Nested suspending context
 *     reactiveSuspending {
 *         val data = fetchData(config)
 *         processData(data)
 *     }
 * }
 * ```
 *
 * When `configId` changes, both the outer fetch and inner context are cancelled and restarted.
 *
 * ## Synchronous vs Asynchronous Execution
 *
 * If the calculation completes synchronously (no suspension points or already on correct
 * dispatcher), it runs immediately. Otherwise, it runs asynchronously and the state may
 * temporarily become [ReactiveState.notReady] (unless [useLastWhileLoading] is configured).
 *
 * @param action The suspending calculation logic to run reactively
 * @return A [ReactiveContextSuspending] managing the calculation and its dependencies
 *
 * @see ReactiveCoroutineScope for detailed lifecycle documentation
 * @see ReactiveContextSuspending for implementation details
 * @see reactive for non-suspending calculations
 */
fun CoroutineScope.reactiveSuspending(action: suspend ReactiveCoroutineScope.() -> Unit) =
    ReactiveContextSuspending(this, action = action).also {
        it.startCalculation()
        coroutineContext[StatusListener.Key]?.watchBackgroundProcess(it)
    }

/**
 * Creates a [ReactiveContextSuspending] to run the provided suspending [action] reactively, with support for loading state.
 *
 * The [action] lambda is executed in a tracked context, automatically registering all dependencies accessed.
 * If the calculation enters a loading state, the [onLoad] callback is invoked.
 *
 * @param onLoad Callback invoked when the calculation enters a loading state.
 * @param action The suspending calculation logic to run reactively.
 * @return A [ReactiveContextSuspending] managing the calculation and its dependencies.
 */
inline fun CoroutineScope.reactiveSuspending(crossinline onLoad: () -> Unit, noinline action: suspend ReactiveCoroutineScope.() -> Unit): ReactiveContextSuspending<Unit> {
    return reactiveSuspending(action = action).also {
        it.addListener { if (!it.state.ready) onLoad() }.let(::onRemove)
    }
}