package com.lightningkite.reactive.impl

import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.ReactiveWithMutableValue
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext


/**
 * Creates a mutable reactive value that can be set directly or calculated automatically.
 *
 * This behaves essentially the same as [remember], but allows the value to be manually set.
 * The initial value is calculated in a reactive context and updates automatically when dependencies change,
 * unless the value is overridden by direct assignment. When overridden, listeners are notified only if the value changes.
 *
 * Note:
 * - `MutableRemember` is lazy: if it has no listeners, it will not calculate a value.
 * - Listeners are only notified if the calculated or set value changes.
 *
 * @param useLastWhileLoading If true, uses the last known value while recalculating.
 * @param coroutineContext The coroutine context for running the calculation (default: Dispatchers.Unconfined).
 * @param initialValue The block to compute the initial value reactively.
 * @return A mutable reactive value that can be set or calculated automatically.
 *
 * Example:
 * ```kotlin
 * val a = Signal(0)
 * val b = Signal(1)
 * val sum: ReactiveWithMutableValue<Int> = mutableRemember { a() + b() } // calculates '1' and shares result
 * sum.value = 42 // overrides automatic calculation and shares new value '42'
 * ```
 *
 * @see remember
 */
fun <T> mutableRemember(
    useLastWhileLoading: Boolean = false,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    initialValue: ReactiveContext.() -> T
): ReactiveWithMutableValue<T> = MutableRemember(true, useLastWhileLoading, coroutineContext, initialValue)

/**
 * A mutable reactive value that can be set directly or calculated automatically from dependencies.
 *
 * When not overridden, the value is calculated in a reactive context and updates automatically when dependencies change.
 * When overridden by direct assignment, automatic calculation is paused until `reset()` is called.
 *
 * Note:
 * - `MutableRemember` is lazy: if it has no listeners, it will not calculate a value.
 * - Listeners are only notified if the calculated or set value changes.
 * - The `reset()` method restores automatic calculation and updates the value from dependencies.
 *
 * @property stopListeningWhenOverridden If true, stops listening to dependencies when overridden.
 * @property useLastWhileLoading If true, uses the last known value while recalculating.
 * @param coroutineContext The coroutine context for running the calculation.
 * @param initialValue The block to compute the initial value reactively.
 */
class MutableRemember<T>(
    private val stopListeningWhenOverridden: Boolean = true,
    private val useLastWhileLoading: Boolean = false,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    initialValue: ReactiveContext.() -> T
): ReactiveWithMutableValue<T>, BaseReactive<T>() {
    var overridden: Boolean = false
        private set

    private val remember = Remember(coroutineContext, useLastWhileLoading, initialValue)
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
     * - If this [MutableRemember] has not been manually set this method does nothing.
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
