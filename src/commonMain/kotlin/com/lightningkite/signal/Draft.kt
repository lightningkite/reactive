package com.lightningkite.signal

class Draft<T> private constructor(
    val published: MutableReactive<T>,
    private val draft: MutableRemember<T>
): ReactiveWithMutableValue<T> by draft {
    constructor(published: MutableReactive<T>) : this(published, MutableRemember(stopListeningWhenOverridden = false) { published() })
    constructor(initialValue: ReactiveContext.() -> T) : this(
        MutableRemember(
            useLastWhileLoading = true,
            initialValue = initialValue
        )
    )
    constructor(initialValue: T) : this(Signal(initialValue))

    val changesMade = remember { draft() != published() }

    suspend fun publish(): T {
        published.set(draft.awaitOnce())
        draft.reset()
        return awaitOnce()
    }
    fun cancel() { draft.reset() }

    override suspend fun set(value: T) { draft.valueSet(value) }
}