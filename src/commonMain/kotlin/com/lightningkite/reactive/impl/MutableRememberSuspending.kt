package com.lightningkite.reactive.impl

import com.lightningkite.reactive.context.CalculationContext
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.ReactiveWithMutableValue
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * This is a suspending version of [mutableRemember]. Creates a mutable reactive value that can be set directly or calculated automatically using a suspending block.
 *
 * The initial value is calculated in a suspending reactive context and updates automatically when dependencies change,
 * unless the value is overridden by direct assignment. When overridden, listeners are notified only if the value changes.
 *
 * Note:
 * - lazy: if this has no listeners, it will not calculate a value.
 * - Listeners are only notified if the calculated or set value changes. I.e., if '1' is calculated, and then '1' is set, it will not notify listeners.
 * - The `reset()` method restores automatic calculation and updates the value from dependencies.
 *
 * @param useLastWhileLoading If true, uses the last known value while recalculating.
 * @param coroutineContext The coroutine context for running the calculation (default: Dispatchers.Unconfined).
 * @param initialValue The suspending block to compute the initial value reactively.
 * @return A mutable reactive value that can be set or calculated automatically.
 *
 * Example:
 * ```kotlin
 * val sum: ReactiveWithMutableValue<Int> = mutableRememberSuspending { someSuspendCalculation() }
 * sum.valueSet(42) // overrides automatic calculation
 * sum.reset() // restores automatic calculation
 * ```
 *
 * @see [mutableRemember]
 */
fun <T> mutableRememberSuspending(
    useLastWhileLoading: Boolean = false,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    initialValue: suspend CalculationContext.() -> T
): ReactiveWithMutableValue<T> = MutableRememberSuspending(true, useLastWhileLoading, coroutineContext, initialValue)

/**
 * This is a suspending version of [MutableRemember]. A mutable reactive value that can be set directly or calculated automatically from dependencies using a suspending block.
 *
 * When not overridden, the value is calculated in a suspending reactive context and updates automatically when dependencies change.
 * When overridden by direct assignment, automatic calculation is paused until `reset()` is called.
 *
 * Note:
 * - lazy: if this has no listeners, it will not calculate a value.
 * - Listeners are only notified if the calculated or set value changes. I.e., if '1' is calculated, and then '1' is set, it will not notify listeners.
 * - The `reset()` method restores automatic calculation and updates the value from dependencies.
 *
 * @property stopListeningWhenOverridden If true, stops listening to automatic calculation when overridden.
 * @property useLastWhileLoading If true, uses the last known value while recalculating.
 * @param coroutineContext The coroutine context for running the calculation.
 * @param initialValue The suspending block to compute the initial value reactively.
 *
 * @see [MutableRemember]
 */
class MutableRememberSuspending<T>(
    private val stopListeningWhenOverridden: Boolean = true,
    private val useLastWhileLoading: Boolean = false,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    initialValue: suspend CalculationContext.() -> T
) : ReactiveWithMutableValue<T>, BaseReactive<T>() {
    var overridden: Boolean = false
        private set

    private val remember = RememberSuspending(coroutineContext, useLastWhileLoading, initialValue)
    private var forget: (()->Unit)? = null

    private fun updateOnce() {
        val currentRememberedState = remember.state
        if(!overridden && (!useLastWhileLoading || currentRememberedState.ready)) state = currentRememberedState
    }

    private fun startListening() {
        forget = remember.addListener {
            if (!overridden) state = remember.state
        }
        updateOnce()
    }

    private fun stopListening() {
        forget?.invoke()
        forget = null
    }

    override var state: ReactiveState<T>
        get() {
            if (!overridden && forget == null) updateOnce()
            return super.state
        }
        set(value) {
            super.state = value
        }

    override fun activate() {
        if (!overridden && forget == null) startListening()
    }
    override fun deactivate() {
        if (forget == null) return
        stopListening()
        if (!overridden && !useLastWhileLoading) state = ReactiveState.notReady
    }

    override fun valueSet(value: T) {
        if (!overridden) {
            overridden = true
            if (stopListeningWhenOverridden) stopListening()
        }
        state = ReactiveState(value)
    }

    /**
     * Restores automatic calculation and updates the value from dependencies.
     * If the value was overridden, this resumes listening and updates the value.
     *
     * Note:
     * - If this [MutableRememberSuspending] has not been manually set this method does nothing.
     * - This does not forcefully notify listeners. If the value calculated after resetting is the same as the previously set value,
     *   then no listeners will be notified.
     */
    fun reset() {
        if (overridden) {
            overridden = false
            if (stopListeningWhenOverridden) startListening()

            val currentSharedState = remember.state
            if (!useLastWhileLoading || currentSharedState.ready) {
                state = currentSharedState
                // useLastWhileLoading = true: If shared is not ready, the state will remain as the previously set value until shared is ready.
            }
        }
    }
}
