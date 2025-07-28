package com.lightningkite.signal

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext


/**
 * Essentially [Remember] but can be set.
 *
 * @property stopListeningWhenOverridden When true, the MutableSignal stops listening to its initial value calculation
 * when set. It's recommended this be set `false` when the mutable signal is likely to be reset.
 *
 * @property useLastWhileLoading When true, the most recent set value or calculated result is used while new results
 * are being calculated
 * */
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
     * Resets the MutableSignal to the initial value calculation.
     *
     * If [stopListeningWhenOverridden] then the MutableSignal starts the shared readable again
     *
     * If [useLastWhileLoading] then the MutableSignal will continue to use the last set value until the
     * calculation is finished
     * */
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

fun <T> mutableRemember(
    useLastWhileLoading: Boolean = false,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    initialValue: ReactiveContext.() -> T
): ReactiveWithMutableValue<T> = MutableRemember(true, useLastWhileLoading, coroutineContext, initialValue)