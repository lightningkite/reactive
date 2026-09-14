package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.BaseListenable
import com.lightningkite.reactive.core.Mutable
import com.lightningkite.reactive.core.RawReactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.Release
import com.lightningkite.reactive.core.addAndRunListener
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.extensions.use
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reactively reports an issue to a child of this [IssueTracking] node, re-checking [issue] whenever a
 * reactive value it reads changes.
 *
 * This is the reactive counterpart to [IssueTracking.report]: [issue] is a block instead of a single value,
 * so it can be re-evaluated automatically as the data it depends on changes. It keeps reporting for as long
 * as the surrounding [scope] is alive.
 *
 * @param issue Reactive block that returns an [Issue] to report, or null to report nothing.
 * @return The child [IssueNode] this function reports to.
 */
context(scope: CoroutineScope)
public fun IssueTracking.reportReactive(issue: ReactiveContext.() -> Issue?): IssueNode {
    val child = node.child(connect = false)
    scope.use(child)
    scope.reactive {
        child.report(issue())
    }
    return child
}

/** Shared machinery behind every [auditReactive] overload: keeps [node]'s issue current by re-running
 * [validate], and (when [willUseCheck] is set) lets [MutableValidated.auditReactive]'s gated `set()` await a
 * result for a specific value via [check]. */
private class Auditor<T>(
    val source: Validated<T>,
    val willUseCheck: Boolean,
    val validate: ReactiveContext.(T) -> Issue?,
) : Validated<T>, BaseListenable() {
    override val node: IssueNode = source.node.child(connect = false)

    private val emit = RawReactive<Pair<T, CancellableContinuation<Issue?>?>>()

    private inline fun <V> CancellableContinuation<V>?.terminate(action: (CancellableContinuation<V>) -> Unit) {
        if (this != null && this.isActive) action(this)
    }

    private fun emit(state: ReactiveState<Pair<T, CancellableContinuation<Issue?>?>>) {
        if (willUseCheck) emit.state.onSuccess { prev ->
            prev.second.terminate { it.cancel() }
        }
        emit.state = state
    }

    override val state: ReactiveState<T> get() = emit.state.map { it.first }

    private val process = remember {
        val (value: T, cont: CancellableContinuation<Issue?>?) = emit()
        val result: Issue? = validate(value)
        cont.terminate { it.resume(result) }
        result
    }

    private var releaseProcess: Release? = null
    private var releaseSource: Release? = null

    override fun activate() {
        node.connect()
        releaseSource = source.addAndRunListener {
            emit(source.state.map { it to null })
            this@Auditor.invokeAllListeners()
        }
        releaseProcess = process.addAndRunListener {
            node.reportRaw(process.state)
        }
    }

    override fun deactivate() {
        node.disconnect()
        releaseSource?.invoke()
        releaseSource = null
        releaseProcess?.invoke()
        releaseProcess = null
    }

    suspend fun check(value: T): Issue? {
        require(willUseCheck) { "Set `willUseCheck = false` and then called `check`" }

        return if (releaseProcess == null) {
            // if nothing is listening to this node, but still trying to set it (for some reason) this prevents a deadlock
            val release = process.addAndRunListener {
                node.reportRaw(process.state)
            }
            try {
                suspendCancellableCoroutine {
                    emit(ReactiveState(value to it))
                }
            } finally {
                release.invoke()
            }
        } else suspendCancellableCoroutine {
            emit(ReactiveState(value to it))
        }
    }
}

/**
 * Adds a reactive validation check to a [Validated] instance.
 *
 * This is the reactive counterpart to [Validated.audit]: [validate] is a [ReactiveContext] block, so it can
 * read other reactive values too, letting it express checks that depend on more than just this value (e.g.
 * "must be less than some other field").
 *
 * @param validate Reactive block that returns an [Issue] or null for the current value.
 * @return A [Validated] that tracks issues according to [validate].
 */
public fun <T> Validated<T>.auditReactive(validate: ReactiveContext.(T) -> Issue?): Validated<T> =
    Auditor(this, willUseCheck = false, validate)

/**
 * Adds a reactive validation check to a [MutableValidated] instance, gating `set()` on the result.
 *
 * This is the reactive counterpart to [MutableValidated.audit]. Since [validate] may need to read other
 * reactive values before it can answer, it can't be checked synchronously the way [audit]'s plain function
 * can — so `set(value)` **suspends** instead, waiting for [validate] to produce a result before deciding
 * whether to write:
 * - If the result is `null` or has `setValue = true`, the value is written through, same as [audit].
 * - If the result has `setValue = false`, the write is rejected, same as [audit] — the underlying source
 *   keeps its old value, though the rejected value is still reflected back locally (so a UI bound to it can
 *   keep showing what was entered).
 *
 * The issue is still reported either way, whether or not the write went through.
 *
 * Pass `checkForSetOnIssue = false` to skip this altogether: `set()` then always writes straight through,
 * and the issue just gets tracked reactively in the background.
 *
 * @param checkForSetOnIssue Whether `set()` waits on [validate] and can reject the write (default `true`),
 * or always writes straight through (`false`).
 * @param validate Reactive block that returns an [Issue] or null for the current value.
 * @return A [MutableValidated] that tracks issues according to [validate] and gates writes accordingly.
 */
public fun <T> MutableValidated<T>.auditReactive(
    checkForSetOnIssue: Boolean = true,
    validate: ReactiveContext.(T) -> Issue?
): MutableValidated<T> {
    val auditor = Auditor(this, willUseCheck = checkForSetOnIssue, validate)
    return object : MutableValidated<T>, Validated<T> by auditor {
        override suspend fun set(value: T) {
            if (checkForSetOnIssue) {
                val result = auditor.check(value)
                if (result?.setValue == false) return
            }
            this@auditReactive.set(value)
        }
    }
}

/**
 * Shortcut for [auditReactive] that reports [validate]'s return value as an [Issue] summary.
 *
 * @param setOnIssue Whether a reported issue still allows the value to be written (default `true`), or
 * rejects the write — see [MutableValidated.auditReactive].
 * @param validate Reactive block that returns a message describing the problem, or null if valid.
 */
public fun <T> MutableValidated<T>.validateReactive(
    setOnIssue: Boolean = true,
    validate: ReactiveContext.(T) -> String?
): MutableValidated<T> =
    auditReactive(checkForSetOnIssue = !setOnIssue) { value ->
        validate(value)?.let {
            Issue(it, setValue = setOnIssue)
        }
    }

/**
 * Shortcut for [auditReactive] that reports [summary]/[description] as the [Issue] whenever [condition] fails.
 *
 * @param summary Short description of the issue.
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue Whether a failed [condition] still allows the value to be written (default `true`), or
 * rejects the write — see [MutableValidated.auditReactive].
 * @param condition Reactive block that returns true if valid, false if invalid.
 */
public fun <T> MutableValidated<T>.assertReactive(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: ReactiveContext.(T) -> Boolean
): MutableValidated<T> =
    auditReactive(checkForSetOnIssue = !setOnIssue) { value ->
        if (condition(value)) null
        else Issue(summary, description, setOnIssue)
    }
