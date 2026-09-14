package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.BaseReactive
import com.lightningkite.reactive.core.BaseReactiveValue
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.Release

private open class ValidatedLens<S : Validated<T>, T>(
    val source: S,
    val validate: (T) -> Issue?
) : Validated<T>, BaseReactive<T>(source.state) {
    protected val baseNode = IssueNode(parent = source.node)
    private var myListen: Release? = null

    private fun check(state: ReactiveState<T>) =
        state.onSuccess { value ->
            baseNode.report(validate(value))
        }

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

    private var listen = true
    protected inline fun withoutListening(block: () -> Unit) {
        try {
            listen = false
            block()
        } finally {
            listen = true
        }
    }

    override fun activate() {
        super.activate()
        baseNode.connect()
        // Subscribe before reading, so state produced by activating a lazy source isn't missed.
        myListen = source.addListener {
            if (listen) state = source.state.also(::check)
        }
        state = source.state.also(::check)
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
    private var myListen: Release? = null

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

    private var listen = true
    protected inline fun withoutListening(block: () -> Unit) {
        try {
            listen = false
            block()
        } finally {
            listen = true
        }
    }

    override fun activate() {
        super.activate()
        baseNode.connect()
        // Subscribe before reading, for the same reason as [ValidatedLens.activate].
        myListen = source.addListener {
            if (listen) value = source.value.also(::check)
        }
        value = source.value.also(::check)
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
        if (issue == null || issue.setValue) withoutListening { source.set(value) }
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
            if (issue == null || issue.setValue) withoutListening { source.value = value }
        }
}

/**
 * Adds a validation check to a [Validated] instance, without altering how it can be written to.
 *
 * The returned [Validated] creates a new [IssueNode] as a child of the source's [IssueTracking.node]. Every
 * time the source produces a new value, [validate] runs against it and the result is reported to that child
 * node, so its issues automatically propagate up through the tree (see [IssueTracking] for how propagation
 * works).
 *
 * ### Internal mechanics and considerations
 * - **Connection is lazy.** Like other lenses, the child node isn't connected to its parent until this
 *   [Validated] is activated (i.e. gains its first listener). Reading its node or current value before
 *   activation still runs [validate] once to compute the current issue, but that issue is not kept live and
 *   won't update automatically if the source changes again while inactive.
 * - **Re-validates on every source change.** Once activated, a listener is attached to the source *before*
 *   its state is read, so a value produced by activating a lazily-computed source is never missed.
 * - **This overload never suppresses writes**, because a plain [Validated] has no setter to suppress.
 *   [Issue.setValue] only comes into play once you're on a [MutableValidated]/[MutableValidatedValue] (see
 *   the overloads below).
 *
 * @param validate Function that returns an [Issue] or null for valid values.
 * @return A [Validated] that tracks issues according to [validate].
 */
public fun <T> Validated<T>.audit(validate: (T) -> Issue?): Validated<T> = ValidatedLens(this, validate)

/**
 * Adds a validation check to a [ValidatedValue] instance. See the [Validated.audit] overload above for the
 * full explanation of internal mechanics; this behaves identically but through direct value access instead
 * of reactive state.
 *
 * @param validate Function that returns an [Issue] or null for valid values.
 * @return A [ValidatedValue] that tracks issues according to [validate].
 */
public fun <T> ValidatedValue<T>.audit(validate: (T) -> Issue?): ValidatedValue<T> = ValidatedValueLens(this, validate)

/**
 * Adds a validation check to a [MutableValidated] instance.
 *
 * Builds on the read-only [Validated.audit] behavior above, but because the receiver is mutable,
 * [Issue.setValue] now controls whether writes reach the underlying source:
 * - The lensed value **always** reflects whatever was just written to it locally, valid or not, so that a UI
 *   bound to it can keep echoing back what the user entered.
 * - The write is only forwarded to the source when [validate] returns `null` or an [Issue] with
 *   `setValue = true`. When `setValue = false`, the source keeps its previous value, and only this lensed
 *   node's local state changes to the attempted value, alongside its issue.
 * - Forwarding to the source happens with listening to that same source temporarily disabled, so the
 *   resulting update on the source doesn't loop back and re-run [validate] a second time.
 *
 * As with the read-only overloads, the [IssueNode] this creates is a child of the source's node and only
 * connects to the tree once this [MutableValidated] is activated.
 *
 * @param validate Function that returns an [Issue] or null for valid values.
 * @return A [MutableValidated] that tracks issues according to [validate].
 */
public fun <T> MutableValidated<T>.audit(validate: (T) -> Issue?): MutableValidated<T> = MutableValidationLens(this, validate)

/**
 * Adds a validation check to a [MutableReactive] instance, returning a [MutableValidated] that tracks issues.
 *
 * As a plain [MutableReactive] has no existing validation tree, this first wraps the receiver with
 * [validated], creating a **new root** [IssueNode] — so issues reported here won't propagate any further
 * unless you pass a `reportTo` node yourself by calling [validated] before this. See [MutableValidated.audit]
 * above for the write-through and connection semantics that apply from that point on.
 *
 * @param validate Function that returns an [Issue] or null for valid values.
 * @return A [MutableValidated] that tracks issues according to [validate].
 */
public fun <T> MutableReactive<T>.audit(validate: (T) -> Issue?): MutableValidated<T> = MutableValidationLens(this.validated(), validate)


/**
 * Adds a validation check to a [MutableValidatedValue] instance. See [MutableValidated.audit] above for the
 * full explanation of write-through and connection semantics; this behaves identically but through direct
 * value access instead of reactive state.
 *
 * @param validate Function that returns an [Issue] or null for valid values.
 * @return A [MutableValidatedValue] that tracks issues according to [validate].
 */
public fun <T> MutableValidatedValue<T>.audit(validate: (T) -> Issue?): MutableValidatedValue<T> = ValidationValueLens(this, validate)

/**
 * Adds a validation check to a [MutableReactiveValue] instance, returning a [MutableValidatedValue] that
 * tracks issues.
 *
 * As a plain [MutableReactiveValue] has no existing validation tree, this first wraps the receiver with
 * [validated], creating a **new root** [IssueNode] — see [MutableReactive.audit] above for the same
 * consideration, and [MutableValidated.audit] for the write-through and connection semantics that apply from
 * that point on.
 *
 * @param validate Function that returns an [Issue] or null for valid values.
 * @return A [MutableValidatedValue] that tracks issues according to [validate].
 */
public fun <T> MutableReactiveValue<T>.audit(validate: (T) -> Issue?): MutableValidatedValue<T> = ValidationValueLens(this.validated(), validate)
