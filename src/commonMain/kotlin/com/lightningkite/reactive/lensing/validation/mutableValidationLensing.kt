package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.extensions.onNextSuccess
import com.lightningkite.reactive.lensing.ModifyLens
import com.lightningkite.reactive.lensing.ModifyValueLens
import com.lightningkite.reactive.lensing.SetLens
import com.lightningkite.reactive.lensing.SetValueLens

class ValidatedSetLens<T, L>(
    source: MutableValidated<T>,
    get: (T) -> L,
    set: (L) -> T
) : MutableValidated<L>, SetLens<T, L>(source, get, set) {
    override val node: IssueNode = source.node.child()
}

class ValidatedModifyLens<T, L>(
    source: MutableValidated<T>,
    get: (T) -> L,
    modify: (T, L) -> T
) : MutableValidated<L>, ModifyLens<T, L>(source, get, modify) {
    override val node: IssueNode = source.node.child()
}

class ValidatedSetValueLens<T, L>(
    source: MutableValidatedValue<T>,
    get: (T) -> L,
    set: (L) -> T
) : MutableValidatedValue<L>, SetValueLens<T, L>(source, get, set) {
    override val node: IssueNode = source.node.child()
}

class ValidatedModifyValueLens<T, L>(
    source: MutableValidatedValue<T>,
    get: (T) -> L,
    modify: (T, L) -> T
) : MutableValidatedValue<L>, ModifyValueLens<T, L>(source, get, modify) {
    override val node: IssueNode = source.node.child()
}


// Named anonymous object
private class ValidationRoot<T>(
    val wrapped: MutableReactive<T>
) : MutableValidated<T>, Reactive<T> by wrapped {
    override val node: IssueNode = IssueNode(null)
    override suspend fun set(value: T) = wrapped.set(value)
}

private class ValidationLens<T>(
    private val source: MutableValidated<T>,
    private val validate: (T) -> Issue?
) : MutableValidated<T>, Reactive<T> by source {
    override val node: IssueNode = source.node.child()

    private fun check(value: T): Boolean {
        val issue = validate(value)
        return if (issue == null) {
            node.report(null)
            true
        }
        else {
            node.report(issue)
            issue !is Issue.Invalid
        }
    }
    override suspend fun set(value: T) {
        if (check(value)) source.set(value)
    }
    init {
        source.onNextSuccess { check(it) }
    }
}

// Named anonymous object
private class ValidationRootValue<T>(
    val wrapped: MutableReactiveValue<T>
) : MutableValidatedValue<T>, Listenable by wrapped {
    override val node: IssueNode = IssueNode(null)
    override var value: T by wrapped::value
}

private class ValidationValueLens<T>(
    private val source: MutableValidatedValue<T>,
    private val validate: (T) -> Issue?
) : MutableValidatedValue<T>, Listenable by source {
    override val node: IssueNode = source.node.child()

    private fun check(value: T): Boolean {
        val issue = validate(value)
        return if (issue == null) {
            node.report(null)
            true
        }
        else {
            node.report(issue)
            issue !is Issue.Invalid
        }
    }
    override var value: T
        get() = source.value
        set(value) {
            if (check(value)) source.value = value
        }
    init {
        check(source.value)
    }
}

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
fun <T> MutableReactive<T>.validated(): MutableValidated<T> =
    this as? MutableValidated<T> ?: ValidationRoot(this)

/**
 * Adds a validation check to a [MutableValidated] instance.
 *
 * The [validate] function should return an [Issue] if the value is invalid, or null if valid.
 * The returned [MutableValidated] will report issues according to the provided validation function.
 *
 * @param validate Function that returns an [Issue] or null for valid values.
 * @return A [MutableValidated] that tracks issues according to [validate].
 */
fun <T> MutableValidated<T>.checkForIssue(validate: (T) -> Issue?): MutableValidated<T> = ValidationLens(this, validate)

/**
 * Adds a validation check to a [MutableReactive] instance, returning a [MutableValidated] that tracks issues.
 *
 * The [validate] function should return an [Issue] if the value is invalid, or null if valid.
 *
 * @param validate Function that returns an [Issue] or null for valid values.
 * @return A [MutableValidated] that tracks issues according to [validate].
 */
fun <T> MutableReactive<T>.checkForIssue(validate: (T) -> Issue?): MutableValidated<T> = ValidationLens(this.validated(), validate)



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
fun <T> MutableReactiveValue<T>.validated(): MutableValidatedValue<T> =
    this as? MutableValidatedValue<T> ?: ValidationRootValue(this)

/**
 * Adds a validation check to a [MutableValidatedValue] instance.
 *
 * The [validate] function should return an [Issue] if the value is invalid, or null if valid.
 * The returned [MutableValidatedValue] will report issues according to the provided validation function.
 *
 * @param validate Function that returns an [Issue] or null for valid values.
 * @return A [MutableValidatedValue] that tracks issues according to [validate].
 */
fun <T> MutableValidatedValue<T>.checkForIssue(validate: (T) -> Issue?): MutableValidatedValue<T> = ValidationValueLens(this, validate)

/**
 * Adds a validation check to a [MutableReactiveValue] instance, returning a [MutableValidatedValue] that tracks issues.
 *
 * The [validate] function should return an [Issue] if the value is invalid, or null if valid.
 *
 * @param validate Function that returns an [Issue] or null for valid values.
 * @return A [MutableValidatedValue] that tracks issues according to [validate].
 */
fun <T> MutableReactiveValue<T>.checkForIssue(validate: (T) -> Issue?): MutableValidatedValue<T> = ValidationValueLens(this.validated(), validate)
