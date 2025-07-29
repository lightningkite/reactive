package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveValue

interface IssueTracking {
    val node: IssueNode
    val issues: Reactive<Map<IssueNode, List<Issue>>> get() = node.issues
}

interface Validated<T> : IssueTracking, Reactive<T>
interface ValidatedValue<T> : IssueTracking, ReactiveValue<T>

interface MutableValidated<T> : IssueTracking, MutableReactive<T>, Validated<T> {
    override fun <L> lens(
        get: (T) -> L,
        set: (L) -> T
    ): MutableValidated<L> = ValidatedSetLens(null, this, get, set)

    override fun <L> lens(
        get: (T) -> L,
        modify: (T, L) -> T
    ): MutableValidated<L> = ValidatedModifyLens(null, this, get, modify)
}
interface MutableValidatedValue<T> : IssueTracking, MutableReactiveValue<T>, ValidatedValue<T> {
    override fun <L> lens(
        get: (T) -> L,
        set: (L) -> T
    ): MutableValidatedValue<L> = ValidatedSetValueLens(null, this, get, set)

    override fun <L> lens(
        get: (T) -> L,
        modify: (T, L) -> T
    ): MutableValidatedValue<L> = ValidatedModifyValueLens(null, this, get, modify)
}