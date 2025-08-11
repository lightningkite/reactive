package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveValue
import kotlin.getValue
import kotlin.setValue

private open class RootValidated<T>(
    private val wrapped: Reactive<T>,
    reportTo: IssueNode?
) : Validated<T>, Reactive<T> by wrapped {
    override val node: IssueNode = reportTo?.child() ?: IssueNode(null)
}

private class RootMutableValidated<T>(
    private val wrapped: MutableReactive<T>,
    reportTo: IssueNode?
) : MutableValidated<T>, RootValidated<T>(wrapped, reportTo) {
    override suspend fun set(value: T) = wrapped.set(value)
}

private open class RootValidatedValue<T>(
    private val wrapped: ReactiveValue<T>,
    reportTo: IssueNode?
) : ValidatedValue<T>, Listenable by wrapped {
    override val node: IssueNode = reportTo?.child() ?: IssueNode(null)
    override val value: T get() = wrapped.value
}

private class RootMutableValidatedValue<T>(
    private val wrapped: MutableReactiveValue<T>,
    reportTo: IssueNode?
) : MutableValidatedValue<T>, RootValidatedValue<T>(wrapped, reportTo) {
    override var value: T by wrapped::value
}

/**
 * Wraps a [Reactive] as a [Validated].
 *
 * If the receiver is already [Validated], it is returned as-is. Otherwise, this creates the root of the validation tree for the reactive value.
 * This enables validation and issue tracking for the reactive value. See [IssueTracking] for more details about validation trees.
 *
 * @param reportTo Optionally provide a parent [IssueNode] to aggregate issues. This is only used if the receiver is not already validated and a new root validation node is constructed. Supplying a parent node allows you to aggregate validation issues across multiple unrelated data structures.
 * @return A [Validated] instance wrapping the receiver.
 *
 * @see IssueTracking
 */
fun <T> Reactive<T>.validated(reportTo: IssueNode? = null): Validated<T> = this as? Validated<T> ?: RootValidated(this, reportTo)

/**
 * Wraps a [MutableReactive] as a [MutableValidated].
 *
 * If the receiver is already a [MutableValidated], it is returned as-is. Otherwise, this creates the root of the validation tree for the reactive value.
 * This enables validation and issue tracking for the reactive value and its lenses. See [IssueTracking] for more details about validation trees.
 *
 * @param reportTo Optionally provide a parent [IssueNode] to aggregate issues. This is only used if the receiver is not already validated and a new root validation node is constructed. Supplying a parent node allows you to aggregate validation issues across multiple unrelated data structures.
 * @return A [MutableValidated] instance wrapping the receiver.
 *
 * @see IssueTracking
 */
fun <T> MutableReactive<T>.validated(reportTo: IssueNode? = null): MutableValidated<T> = this as? MutableValidated<T> ?: RootMutableValidated(this, reportTo)

/**
 * Wraps a [ReactiveValue] as a [ValidatedValue].
 *
 * If the receiver is already [ValidatedValue], it is returned as-is. Otherwise, this creates the root of the validation tree for the reactive value.
 * This enables validation and issue tracking for the reactive value. See [IssueTracking] for more details about validation trees.
 *
 * @param reportTo Optionally provide a parent [IssueNode] to aggregate issues. This is only used if the receiver is not already validated and a new root validation node is constructed. Supplying a parent node allows you to aggregate validation issues across multiple unrelated data structures.
 * @return A [ValidatedValue] instance wrapping the receiver.
 *
 * @see IssueTracking
 */
fun <T> ReactiveValue<T>.validated(reportTo: IssueNode? = null): ValidatedValue<T> = this as? ValidatedValue<T> ?: RootValidatedValue(this, reportTo)

/**
 * Wraps a [MutableReactiveValue] as a [MutableValidatedValue].
 *
 * If the receiver is already a [MutableValidatedValue], it is returned as-is. Otherwise, this creates the root of the validation tree for the reactive value.
 * This enables validation and issue tracking for the reactive value and its lenses. See [IssueTracking] for more details about validation trees.
 *
 * @param reportTo Optionally provide a parent [IssueNode] to aggregate issues. This is only used if the receiver is not already validated and a new root validation node is constructed. Supplying a parent node allows you to aggregate validation issues across multiple unrelated data structures.
 * @return A [MutableValidatedValue] instance wrapping the receiver.
 *
 * @see IssueTracking
 */
fun <T> MutableReactiveValue<T>.validated(reportTo: IssueNode? = null): MutableValidatedValue<T> = this as? MutableValidatedValue<T> ?: RootMutableValidatedValue(this, reportTo)