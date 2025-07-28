package com.lightningkite.reactive

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Suspending version of `LazyProperty`. See [MutableRemember] for documentation.
 * */
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

fun <T> mutableRememberSuspending(
    useLastWhileLoading: Boolean = false,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    initialValue: suspend CalculationContext.() -> T
): ReactiveWithMutableValue<T> = MutableRememberSuspending(true, useLastWhileLoading, coroutineContext, initialValue)