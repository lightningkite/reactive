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
 *   - If [useLastWhileLoading] is true, the previous value is used while new results are loading; otherwise, the state is updated only when ready.
 *   - The context can be cancelled by calling [cancel] on the last job, stopping further calculations and releasing resources.
 *   - The context inherits its lifetime from the provided [CoroutineScope].
 *
 * - **Operators:**
 *   - The calculation logic can use suspending functions and access [Reactive] values as needed.
 *
 * @param T The type of value produced by the calculation.
 * @property scope The coroutine scope for calculations.
 * @property useLastWhileLoading Whether to use the last value while loading new results.
 * @property reportTo The underlying [RawReactive] to report state updates to.
 * @property action The suspending calculation logic to execute in this context.
 */
class ReactiveContextSuspending<T>(
    val scope: CoroutineScope,
    val useLastWhileLoading: Boolean = false,
    private val reportTo: RawReactive<T> = RawReactive(),
    val action: suspend ReactiveCoroutineScope.() -> T,
) : DependencyChangeListener(), ReactiveCoroutineScope, Reactive<T> by reportTo {
    internal var lastLoopJob: Job? = null

    var active = false
        private set

    private var job = Job() // doesn't need to be a child because we add a hook in the init block below to cancel on parent cancellation, and we don't want errors to propagate here.

    override val coroutineContext: CoroutineContext get() = scope.coroutineContext + job + this



    private fun CoroutineScope.launchWithStart(block: suspend () -> Unit) =
        launch(
            start =
                if (coroutineContext[CoroutineDispatcher]?.isDispatchNeeded(coroutineContext) == false)
                    CoroutineStart.UNDISPATCHED
                else
                    CoroutineStart.DEFAULT,
        ) { block() }

    fun startCalculation() {
        active = true
        lastLoopJob?.cancel()

        job.cancel()
        job = Job()

        dependencyBlockStart()

        lastLoopJob = this.let { calculationContext ->
            var done = false
            val job = calculationContext.launchWithStart {
                val result = reactiveState { this@ReactiveContextSuspending.action() }
                if (!useLastWhileLoading || result.ready) reportTo.state = result
                dependencyBlockEnd()
                done = true
            }

            if (dependencyCount == 0) cancel() // with no dependencies this will never rerun, so cancel and release dependencies

            if (done) return@let null
            else {
                // start load
                if (!useLastWhileLoading) reportTo.state = ReactiveState.notReady
                return@let job
            }
        }
    }

    fun runOnceWhileDead() {
        lastLoopJob = run {
            var done = false
            val job = scope.launchWithStart {
                val result = reactiveState { this@ReactiveContextSuspending.action() }
                if (!useLastWhileLoading || result.ready) reportTo.state = result
                done = true
            }

            if (done) null
            else {
                if (!useLastWhileLoading) reportTo.state = ReactiveState.notReady
                job
            }
        }
    }

    override fun onDependencyChange() {
        startCalculation()
    }
    override fun onDependencyNotReady() {
        if (!useLastWhileLoading) reportTo.state = ReactiveState.notReady
    }

    override fun cancel() {
        super.cancel()
        job.cancel()
        job = Job()
        active = false
        lastLoopJob?.let {
            lastLoopJob = null
            it.cancel()
        }
    }

    init {
        scope.onRemove { cancel() }
    }
}

/**
 * Creates a [ReactiveContextSuspending] to run the provided suspending [action] reactively.
 *
 * The [action] lambda is executed in a tracked context, automatically registering all dependencies accessed.
 * Whenever any dependency changes, the calculation is rerun asynchronously, keeping the result up-to-date.
 *
 * The returned [ReactiveContextSuspending] manages the lifecycle and cancellation of the calculation, and is tied to the provided [CalculationContext].
 *
 * @param action The suspending calculation logic to run reactively.
 * @return A [ReactiveContextSuspending] managing the calculation and its dependencies.
 */
fun CoroutineScope.reactiveSuspending(action: suspend ReactiveCoroutineScope.() -> Unit) =
    ReactiveContextSuspending(this, action = action).also {
        it.startCalculation()
        coroutineContext[StatusListener.Key]?.loading(it)
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