package com.lightningkite.signal

class Draft<T> private constructor(
    val published: MutableSignal<T>,
    private val draft: MutableRememberSignal<T>
): SignalWithMutableValue<T> by draft {
    constructor(published: MutableSignal<T>) : this(published, MutableRememberSignal(stopListeningWhenOverridden = false) { published() })
    constructor(initialValue: ReactiveContext.() -> T) : this(
        MutableRememberSignal(
            useLastWhileLoading = true,
            initialValue = initialValue
        )
    )
    constructor(initialValue: T) : this(BasicSignal(initialValue))

    val changesMade = remember { draft() != published() }

    suspend fun publish(): T {
        published.set(draft.awaitOnce())
        draft.reset()
        return awaitOnce()
    }
    fun cancel() { draft.reset() }

    override suspend fun set(value: T) { draft.setValue(value) }
}