package com.lightningkite.signal

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

    private fun clearMemo() {
        forget = remember.addListener {
            if (!overridden) state = remember.state
        }
        val currentRememberedState = remember.state
        if(!overridden && (!useLastWhileLoading || currentRememberedState.ready)) state = currentRememberedState
    }

    private fun stopListeningToShared() {
        forget?.invoke()
        forget = null
    }

    override fun activate() {
        if (!overridden && forget == null) clearMemo()
    }

    override fun deactivate() {
        if (forget == null) return
        stopListeningToShared()
        if (!overridden && !useLastWhileLoading) state = ReactiveState.notReady
    }

    var value: T
        get() = state.get()
        set(value) {
            if (!overridden) {
                overridden = true
                if (stopListeningWhenOverridden) stopListeningToShared()
            }
            state = ReactiveState(value)
        }

    override fun setValue(value: T) { this.value = value }

    fun reset() {
        if (overridden) {
            overridden = false
            if (stopListeningWhenOverridden) clearMemo()

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