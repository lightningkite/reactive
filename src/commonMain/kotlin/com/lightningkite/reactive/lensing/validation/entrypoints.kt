package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveValue
import kotlin.getValue
import kotlin.setValue

private open class RootValidated<T>(
    private val wrapped: Reactive<T>
) : Validated<T>, Reactive<T> by wrapped {
    override val node: IssueNode = IssueNode(null)
}

private class RootMutableValidated<T>(
    private val wrapped: MutableReactive<T>
) : MutableValidated<T>, RootValidated<T>(wrapped) {
    override suspend fun set(value: T) = wrapped.set(value)
}

private open class RootValidatedValue<T>(
    private val wrapped: ReactiveValue<T>
) : ValidatedValue<T>, Listenable by wrapped {
    override val node: IssueNode = IssueNode(null)
    override val value: T get() = wrapped.value
}

private class RootMutableValidatedValue<T>(
    private val wrapped: MutableReactiveValue<T>
) : MutableValidatedValue<T>, RootValidatedValue<T>(wrapped) {
    override var value: T by wrapped::value
}

/**
 * Wraps a [Reactive] as a [Validated].
 *
 * If the receiver is already [Validated], it is returned as-is. Otherwise, this creates the root of the validation tree for the reactive value.
 * This enables validation and issue tracking for the reactive value. See [IssueTracking] for more details about validation trees.
 *
 * @return A [Validated] instance wrapping the receiver.
 *
 * @see IssueTracking
 */
fun <T> Reactive<T>.validated(): Validated<T> = this as? Validated<T> ?: RootValidated(this)

/**
 * Wraps a [MutableReactive] as a [MutableValidated].
 *
 * If the receiver is already a [MutableValidated], it is returned as-is. Otherwise, this creates the root of the validation tree for the reactive value.
 * This enables validation and issue tracking for the reactive value. See [IssueTracking] for more details about validation trees.
 *
 * @return A [MutableValidated] instance wrapping the receiver.
 *
 * @see IssueTracking
 */
fun <T> MutableReactive<T>.validated(): MutableValidated<T> = this as? MutableValidated<T> ?: RootMutableValidated(this)

/**
 * Wraps a [ReactiveValue] as a [ValidatedValue].
 *
 * If the receiver is already [ValidatedValue], it is returned as-is. Otherwise, this creates the root of the validation tree for the reactive value.
 * This enables validation and issue tracking for the reactive value. See [IssueTracking] for more details about validation trees.
 *
 * @return A [ValidatedValue] instance wrapping the receiver.
 *
 * @see IssueTracking
 */
fun <T> ReactiveValue<T>.validated(): ValidatedValue<T> = this as? ValidatedValue<T> ?: RootValidatedValue(this)

/**
 * Wraps a [MutableReactiveValue] as a [MutableValidatedValue].
 *
 * If the receiver is already a [MutableValidatedValue], it is returned as-is. Otherwise, this creates the root of the validation tree for the reactive value.
 * This enables validation and issue tracking for the reactive value. See [IssueTracking] for more details about validation trees.
 *
 * @return A [MutableValidatedValue] instance wrapping the receiver.
 *
 * @see IssueTracking
 */
fun <T> MutableReactiveValue<T>.validated(): MutableValidatedValue<T> = this as? MutableValidatedValue<T> ?: RootMutableValidatedValue(this)