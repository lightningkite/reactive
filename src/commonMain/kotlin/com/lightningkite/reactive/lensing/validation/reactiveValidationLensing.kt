package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.ReactiveValue
import com.lightningkite.reactive.lensing.lens

private class ValidatedLens<T>(
    parent: IssueNode?,
    source: Reactive<T>,
    val validate: (T) -> Issue?
) : Validated<T> {
    private val _node = IssueNode(parent)

    private val lens = source.lens {
        node.report(validate(it))
        it
    }

    override val node: IssueNode
        get() {
            lens.state // Getting the state guarantees that the validation state will be updated
            return _node
        }

    override val state: ReactiveState<T> by lens::state
    override fun addListener(listener: () -> Unit): () -> Unit = lens.addListener(listener)
}

private class ValidatedValueLens<T>(
    parent: IssueNode?,
    source: ReactiveValue<T>,
    val validate: (T) -> Issue?
) : ValidatedValue<T> {
    private val _node = IssueNode(parent)

    private val lens = source.lens {
        node.report(validate(it))
        it
    }

    override val node: IssueNode
        get() {
            lens.state // Getting the state guarantees that the validation state will be updated
            return _node
        }

    override val value: T by lens::value
    override fun addListener(listener: () -> Unit): () -> Unit = lens.addListener(listener)
}

fun <T> Reactive<T>.validated(validate: (T) -> Issue?): Validated<T> = ValidatedLens(null, this, validate)
fun <T> ReactiveValue<T>.validated(validate: (T) -> Issue?): ValidatedValue<T> = ValidatedValueLens(null, this, validate)

fun <T> Validated<T>.validated(validate: (T) -> Issue?): Validated<T> = ValidatedLens(node, this, validate)
fun <T> ValidatedValue<T>.validated(validate: (T) -> Issue?): ValidatedValue<T> = ValidatedValueLens(node, this, validate)
