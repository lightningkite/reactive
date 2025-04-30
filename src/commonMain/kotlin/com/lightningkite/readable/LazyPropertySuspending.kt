package com.lightningkite.readable

/**
 * Suspending version of `LazyProperty`. See [LazyProperty] for documentation.
 * */
class LazyPropertySuspending<T>(
    private val stopListeningWhenOverridden: Boolean = true,
    private val useLastWhileLoading: Boolean = false,
    initialValue: suspend CalculationContext.() -> T
) : ReadableWithImmediateWrite<T>, BaseReadable<T>() {
    var overridden: Boolean = false
        private set

    private val shared = SharedSuspendingReadable(useLastWhileLoading = useLastWhileLoading, action = initialValue)
    private var sharedRemover: (()->Unit)? = null

    private fun startListeningToShared() {
        sharedRemover = shared.addListener {
            if (!overridden) state = shared.state
        }
        val currentSharedState = shared.state
        if(!overridden && (!useLastWhileLoading || currentSharedState.ready)) state = currentSharedState
    }

    private fun stopListeningToShared() {
        sharedRemover?.invoke()
        sharedRemover = null
    }

    override fun activate() {
        if (!overridden && sharedRemover == null) startListeningToShared()
    }

    override fun deactivate() {
        if (sharedRemover == null) return
        stopListeningToShared()
        if (!overridden && !useLastWhileLoading) state = ReadableState.notReady
    }

    var value: T
        get() = state.get()
        set(value) {
            if (!overridden) {
                overridden = true
                if (stopListeningWhenOverridden) stopListeningToShared()
            }
            state = ReadableState(value)
        }

    override fun setImmediate(value: T) { this.value = value }

    fun reset() {
        if (overridden) {
            overridden = false
            if (stopListeningWhenOverridden) startListeningToShared()

            val currentSharedState = shared.state
            if (!useLastWhileLoading || currentSharedState.ready) {
                state = currentSharedState
                // useLastWhileLoading = true: If shared is not ready, the state will remain as the previously set value until shared is ready.
            }
        }
    }
}