package com.lightningkite.reactive.core

import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.awaitOnce

/**
 * A mutable reactive value that supports draft editing and publishing. Essentially,
 * this represents an input buffer for a "published" [MutableReactive].
 *
 * A [Draft] copies its [published] value until you set a new value in the draft. After
 * you set a new value, the draft keeps this change separate from the published value.
 * The draft will keep your change until you call [publish] (to save it) or [cancel]
 * (to discard it and revert to the published value).
 *
 * Example:
 * ```kotlin
 * val published = Signal(0)
 * val draft = Draft(published)
 *
 * draft.value = 42 // changes the draft, not the published value
 * draft.publish() // commits the draft to published. published now holds the value '42'
 *
 * draft.value = 43 // draft now holds '43', while published holds '42'
 * draft.cancel() // discards changes and resets the draft. draft now reads '42' again.
 * ```
 */
interface Draft<T> : ReactiveWithMutableValue<T> {
    /**
     * The current saved value that this [Draft] is buffering.
     *
     * NOTE: Manually setting values for [published] will not by-default update values in the draft buffer.
     * */
    val published: MutableReactive<T>

    /**
     * Saves all changes made to this [Draft] to the published [MutableReactive]
     * */
    suspend fun publish(): T

    /**
     * Discards all changes made to this [Draft] and reverts back to the [published] state
     * */
    fun cancel()

    /**
     * Reads `true` if there are any differences between the [published] value and the value stored in the draft buffer.
     * */
    val changesMade: Reactive<Boolean>
}

private class BaseDraft<T> private constructor(
    override val published: MutableReactive<T>,
    val buffer: MutableRemember<T>
): Draft<T>, ReactiveWithMutableValue<T> by buffer {
    constructor(published: MutableReactive<T>) : this(published, MutableRemember(stopListeningWhenOverridden = false) { published() })

    override val changesMade = remember { buffer() != published() }

    override suspend fun publish(): T {
        published.set(buffer.awaitOnce())
        buffer.reset()
        return awaitOnce()
    }
    override fun cancel() { buffer.reset() }
}

/**
 * Creates a [Draft] using the specified [MutableReactive] as the published value.
 * */
fun <T> Draft(published: MutableReactive<T>): Draft<T> = BaseDraft(published)

/**
 * Creates a [Draft] where the published value is the provided [initialValue]
 * */
fun <T> Draft(initialValue: T): Draft<T> = BaseDraft(Signal(initialValue))

/**
 * Creates a [Draft] where the published value is calculated based off the provided [initialValue] calculation.
 * */
fun <T> Draft(initialValue: ReactiveContext.() -> T): Draft<T> = BaseDraft(MutableRemember(useLastWhileLoading = true, initialValue = initialValue))