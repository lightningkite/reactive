package com.lightningkite.readable


/**
 * Essentially SharedReadable but can be set.
 *
 * @property stopListeningWhenOverridden When true, the LazyProperty stops listening to its initial value calculation
 * when set. It's recommended this be set `false` when the property is likely to be reset.
 *
 * @property useLastWhileLoading When true, the most recent set value or calculated result is used while new results
 * are being calculated
 * */
class LazyProperty<T>(
    private val stopListeningWhenOverridden: Boolean = true,
    private val useLastWhileLoading: Boolean = false,
    initialValue: ReactiveContext.() -> T
): ReadableWithImmediateWrite<T> {

    private val shared = SharedReadable(useLastWhileLoading = useLastWhileLoading, action = initialValue)

    private val listeners = ArrayList<() -> Unit>()
    override fun addListener(listener: () -> Unit): () -> Unit {
        listeners.add(listener)

        if (!overridden && sharedRemover == null) startListeningToShared()

        return {
            val pos = listeners.indexOfFirst { it === listener }
            if (pos != -1) {
                listeners.removeAt(pos)
                shutdownIfNotNeeded()
            }
        }
    }

    override var state: ReadableState<T> = ReadableState.notReady
        private set(value) {
            if(field != value) {
                field = value
                listeners.invokeAllSafe()
                shutdownIfNotNeeded()
            }
        }

    var overridden: Boolean = false
        private set

    private var sharedRemover: (() -> Unit)? = null

    private fun startListeningToShared() {
        sharedRemover = shared.addListener {
            if (!overridden) {
                state = shared.state
            }
        }
        val currentSharedState = shared.state
        if(!overridden && (!useLastWhileLoading || currentSharedState.ready)) state = shared.state
    }

    private fun stopListeningToShared() {
        sharedRemover?.invoke()
        sharedRemover = null
    }
    private fun shutdownIfNotNeeded() {
        if (listeners.isNotEmpty()) return
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

    /**
     * Resets the LazyProperty to the initial value calculation.
     *
     * If [stopListeningWhenOverridden] then the LazyProperty starts the shared readable again
     *
     * If [useLastWhileLoading] then the LazyProperty will continue to use the last set value until the
     * calculation is finished
     * */
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