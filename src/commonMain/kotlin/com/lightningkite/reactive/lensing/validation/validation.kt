package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.BaseReactive
import com.lightningkite.reactive.core.BaseReactiveValue
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember

private open class ValidatedLens<S : Validated<T>, T>(
    val source: S,
    val validate: (T) -> Issue?
) : Validated<T>, BaseReactive<T>(source.state) {
    protected val baseNode = IssueNode(parent = source.node)
    private var myListen: (() -> Unit)? = null

    private fun check(state: ReactiveState<T>) =
        state.onSuccess { baseNode.report(validate(it)) }

    override val node: IssueNode
        get() = baseNode.also { if (myListen == null) check(source.state) }

    override var state: ReactiveState<T>
        get() {
            if (myListen == null) super.state = source.state.also(::check)
            return super.state
        }
        set(value) {
            super.state = value
        }

    override fun activate() {
        super.activate()
        baseNode.connect()
        state = source.state.also(::check)
        myListen = source.addListener {
            state = source.state.also(::check)
        }
    }
    override fun deactivate() {
        super.deactivate()
        baseNode.disconnect()
        myListen?.invoke()
        myListen = null
    }
}

private open class ValidatedValueLens<S : ValidatedValue<T>, T>(
    val source: S,
    val validate: (T) -> Issue?
) : ValidatedValue<T>, BaseReactiveValue<T>(source.value) {
    protected val baseNode = IssueNode(parent = source.node)
    private var myListen: (() -> Unit)? = null

    private fun check(value: T) = baseNode.report(validate(value))

    override val node: IssueNode
        get() = baseNode.also { if (myListen == null) check(source.value) }

    override var value: T
        get() {
            if (myListen == null) super.value = source.value.also(::check)
            return super.value
        }
        set(value) {
            super.value = value
        }

    override fun activate() {
        super.activate()
        baseNode.connect()
        value = source.value.also(::check)
        myListen = source.addListener {
            value = source.value.also(::check)
        }
    }
    override fun deactivate() {
        super.deactivate()
        baseNode.disconnect()
        myListen?.invoke()
        myListen = null
    }
}

private class MutableValidationLens<T>(
    source: MutableValidated<T>,
    validate: (T) -> Issue?
) : MutableValidated<T>, ValidatedLens<MutableValidated<T>, T>(source, validate) {
    override suspend fun set(value: T) {
        val issue = validate(value)
        baseNode.report(issue)
        super.state = ReactiveState(value)
        if (issue == null || issue is Issue.Warning) source.set(value)
    }
}

private class ValidationValueLens<T>(
    source: MutableValidatedValue<T>,
    validate: (T) -> Issue?
) : MutableValidatedValue<T>, ValidatedValueLens<MutableValidatedValue<T>, T>(source, validate) {
    override var value
        get() = super.value
        set(value) {
            val issue = validate(value)
            baseNode.report(issue)
            super.value = value
            if (issue == null || issue is Issue.Warning) source.value = value
        }
}

fun <T> Validated<T>.checkForIssue(validate: (T) -> Issue?): Validated<T> = ValidatedLens(this, validate)

fun <T> ValidatedValue<T>.checkForIssue(validate: (T) -> Issue?): ValidatedValue<T> = ValidatedValueLens(this, validate)

/**
 * Adds a validation check to a [MutableValidated] instance.
 *
 * The [validate] function should return an [Issue] if the value is invalid, or null if valid.
 * The returned [MutableValidated] will report issues according to the provided validation function.
 *
 * @param validate Function that returns an [Issue] or null for valid values.
 * @return A [MutableValidated] that tracks issues according to [validate].
 */
fun <T> MutableValidated<T>.checkForIssue(validate: (T) -> Issue?): MutableValidated<T> = MutableValidationLens(this, validate)

/**
 * Adds a validation check to a [MutableReactive] instance, returning a [MutableValidated] that tracks issues.
 *
 * The [validate] function should return an [Issue] if the value is invalid, or null if valid.
 *
 * @param validate Function that returns an [Issue] or null for valid values.
 * @return A [MutableValidated] that tracks issues according to [validate].
 */
fun <T> MutableReactive<T>.checkForIssue(validate: (T) -> Issue?): MutableValidated<T> = MutableValidationLens(this.validated(), validate)


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
