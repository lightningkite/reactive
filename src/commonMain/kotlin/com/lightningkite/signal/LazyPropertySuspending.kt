package com.lightningkite.signal

/**
 * Suspending version of `LazyProperty`. See [RememberBasicSignal] for documentation.
 * */
class LazyPropertySuspending<T>(
    private val stopListeningWhenOverridden: Boolean = true,
    private val useLastWhileLoading: Boolean = false,
    initialValue: suspend CalculationContext.() -> T
) : ImmediateMutableSignal<T>, BaseSignal<T>() {
    var overridden: Boolean = false
        private set

    private val remember = RememberSuspendingSignal(useLastWhileLoading = useLastWhileLoading, action = initialValue)
    private var forget: (()->Unit)? = null

    private fun clearMemo() {
        forget = remember.addListener {
            if (!overridden) state = remember.state
        }
        val currentSharedState = remember.state
        if(!overridden && (!useLastWhileLoading || currentSharedState.ready)) state = currentSharedState
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
        if (!overridden && !useLastWhileLoading) state = SignalState.notReady
    }

    var value: T
        get() = state.get()
        set(value) {
            if (!overridden) {
                overridden = true
                if (stopListeningWhenOverridden) stopListeningToShared()
            }
            state = SignalState(value)
        }

    override fun setImmediate(value: T) { this.value = value }

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