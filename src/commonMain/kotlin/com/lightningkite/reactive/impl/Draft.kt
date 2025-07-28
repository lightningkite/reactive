package com.lightningkite.reactive.impl

import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.ReactiveWithMutableValue

/**
 * A mutable reactive value that supports draft editing and publishing. Essentially, this provides an input buffer for a [MutableReactive].
 *
 * This class wraps a [MutableReactive] value and provides a buffer layer using [MutableRemember].
 * Changes can be made to the draft without affecting the published value until explicitly published.
 *
 * - The draft value is calculated and updated reactively, but can be manually set.
 * - The draft is lazy: if there are no listeners, it will not calculate a value.
 * - Listeners are only notified if the draft value actually changes.
 * - Use [publish] to commit draft changes to the published value, or [cancel] to discard changes and reset the draft.
 *
 * @param T The type of value being edited and published.
 * @property published The underlying published value.
 * @property draft The mutable draft value.
 * @property changesMade A reactive value indicating if the draft differs from the published value.
 *
 * Example:
 * ```kotlin
 * val published = Signal(0)
 * val draft = Draft(published)
 *
 * draft.set(42) // changes the draft, not the published value
 * draft.publish() // commits the draft to published. published now holds the value '42'
 *
 * draft.set(43) // draft now holds '43', while published holds '42'
 * draft.cancel() // discards changes and resets the draft. draft now reads '42' again.
 * ```
 *
 * @see MutableRemember
 */
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