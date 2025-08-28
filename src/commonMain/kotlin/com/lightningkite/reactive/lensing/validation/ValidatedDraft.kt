package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.Draft

/**
 * A [Draft] with a validation tree. Useful for buffering and validating user input at the same time.
 *
 * @see Draft
 * @see MutableValidated
 * */
interface ValidatedDraft<T> : Draft<T>, MutableValidated<T>

private class RootValidatedDraft<T>(val draft: Draft<T>, reportTo: IssueNode? = null) : ValidatedDraft<T>, Draft<T> by draft {
    override val node: IssueNode = IssueNode(reportTo)

    override fun <L> lens(get: (T) -> L, set: (L) -> T): MutableValidated<L> = ValidatedSetLens(this, get, set)
    override fun <L> lens(get: (T) -> L, modify: (T, L) -> T): MutableValidated<L> = ValidatedModifyLens(this, get, modify)
}

fun <T> Draft<T>.validated(reportTo: IssueNode? = null): ValidatedDraft<T> = RootValidatedDraft(this, reportTo)