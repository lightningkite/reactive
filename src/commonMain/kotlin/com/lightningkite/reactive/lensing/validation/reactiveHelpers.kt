package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.BaseListenable
import com.lightningkite.reactive.core.Mutable
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.Release
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.extensions.use
import kotlinx.coroutines.CoroutineScope

/**
 * Runs the provided validation condition reactively, reporting to a child of this [IssueTracking] node.
 * */
context(scope: CoroutineScope)
public fun IssueTracking.reportReactive(issue: ReactiveContext.() -> Issue?): IssueNode {
    val child = node.child(connect = false)
    scope.use(child)
    scope.reactive {
        child.report(issue())
    }
    return child
}

public fun <T> Validated<T>.auditReactive(validate: ReactiveContext.(T) -> Issue?): Validated<T> =
    object : Validated<T> by this, BaseListenable() {
        val source = this@auditReactive

        override val node: IssueNode = source.node.child(connect = false)

        override fun addListener(listener: () -> Unit): Release = super.addListener(listener)

        private val process = remember {
            node.report(validate(source()))
        }

        private var releaseProcess: Release? = null
        private var releaseSource: Release? = null

        override fun activate() {
            node.connect()
            releaseSource = source.addListener(::invokeAllListeners)
            releaseProcess = process.beginUse()
        }

        override fun deactivate() {
            node.disconnect()
            releaseSource?.invoke()
            releaseSource = null
            releaseProcess?.invoke()
            releaseProcess = null
        }
    }

public fun <T> MutableValidated<T>.auditReactive(validate: ReactiveContext.(T) -> Issue?): MutableValidated<T> =
    object : MutableValidated<T>, Mutable<T> by this, Validated<T> by (this as Validated<T>).auditReactive(validate) {}

public fun <T> MutableValidatedValue<T>.auditReactive(validate: ReactiveContext.(T) -> Issue?): MutableValidatedValue<T> {
    val auditor = (this as Validated<T>).auditReactive(validate)
    return object : MutableValidatedValue<T>, Validated<T> by auditor {
        override var value: T by this@auditReactive::value
        override val state: ReactiveState<T> get() = this@auditReactive.state
    }
}


public fun <T> MutableValidated<T>.validateReactive(
    setOnIssue: Boolean = true,
    validate: ReactiveContext.(T) -> String?
): MutableValidated<T> =
    auditReactive { value ->
        validate(value)?.let {
            Issue(it, setValue = setOnIssue)
        }
    }

public fun <T> MutableValidatedValue<T>.validateReactive(
    setOnIssue: Boolean = true,
    validate: ReactiveContext.(T) -> String?
): MutableValidatedValue<T> =
    auditReactive { value ->
        validate(value)?.let {
            Issue(it, setValue = setOnIssue)
        }
    }

public fun <T> MutableValidated<T>.assertReactive(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: ReactiveContext.(T) -> Boolean
): MutableValidated<T> =
    auditReactive { value ->
        if (condition(value)) null
        else Issue(summary, description, setOnIssue)
    }

public fun <T> MutableValidatedValue<T>.assertReactive(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: ReactiveContext.(T) -> Boolean
): MutableValidatedValue<T> =
    auditReactive { value ->
        if (condition(value)) null
        else Issue(summary, description, setOnIssue)
    }
