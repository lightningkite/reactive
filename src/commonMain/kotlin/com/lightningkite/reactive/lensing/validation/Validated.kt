package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveValue

/**
 * Interface for objects that track validation issues using an [IssueNode].
 * Provides access to the current list of [Issue] and allows reporting new issues.
 *
 * Issue reporting is done through the [report] method.
 *
 * Validation issues are tracked in a branching structure using nodes and child nodes.
 * Each node in the tree is responsible for tracking its own issues, and propagating the
 * issues from its children.
 *
 * For example, if we have the validation tree below
 *
 * ```
 * root
 * ├── A
 * │   ├── A1
 * │   └── A2
 * └── B
 *     └── B1
 * ```
 *
 * If 'A1' reports an issue, then that issue will be propagated up to 'A' and 'root'.
 *
 * If 'root' reports an issue, then only 'root' will see that issue. Issues are only propagated up.
 *
 * Clearing issues doesn't impact ancestors or children. If 'root', 'A', and 'A1' all have issues,
 * and 'A' clears it's issue, the issues in 'root' and 'A1' will be unaffected.
 *
 * @see Issue
 */
interface IssueTracking {
    val node: IssueNode
}

/**
 * Returns the current list of issues, this includes issues from this node and any child nodes.
 */
val IssueTracking.issues get() = node.issues

/**
 * Reports a new issue to this node.
 *
 * Note: Reporting or clearing issues in this node does not affect issues in child nodes.
 *
 * @param issue The issue to report, or null to clear this node's issue.
 */
fun IssueTracking.report(issue: Issue?) = node.report(issue)

/**
 * Represents a validated reactive value. See [IssueTracking] for more details about
 * how validation is represented.
 *
 * @see IssueTracking
 * @see Reactive
 */
interface Validated<T> : IssueTracking, Reactive<T>

/**
 * Represents a validated reactive value with direct value access. See [IssueTracking] for more details about
 * how validation is represented.
 *
 * @see IssueTracking
 * @see ReactiveValue
 */
interface ValidatedValue<T> : IssueTracking, ReactiveValue<T>, Validated<T>

/**
 * Represents a mutable validated reactive value.
 *
 * Lensing a [MutableValidated] creates a child of this node in the validation tree.
 * See [IssueTracking] for more details about validation trees.
 */
interface MutableValidated<T> : IssueTracking, MutableReactive<T>, Validated<T> {
    /**
     * Creates a transforming lens for type conversion.
     * Returns a [MutableValidated] for the sub-value, preserving validation.
     *
     * Lensing creates a child node in the validation tree. Any issues reported on the lensed value
     * will be tracked as a child of this node, and propagated up the tree. See [IssueTracking] for details.
     */
    override fun <L> lens(
        get: (T) -> L,
        set: (L) -> T
    ): MutableValidated<L> = ValidatedSetLens(this, get, set)

    /**
     * Creates a lens for a sub-value using a getter and a modifier function.
     * Returns a [MutableValidated] for the sub-value, preserving validation.
     *
     * Lensing creates a child node in the validation tree. Any issues reported on the lensed value
     * will be tracked as a child of this node, and propagated up the tree. See [IssueTracking] for details.
     */
    override fun <L> lens(
        get: (T) -> L,
        modify: (T, L) -> T
    ): MutableValidated<L> = ValidatedModifyLens(this, get, modify)
}

/**
 * Represents a mutable validated reactive value with direct value access.
 *
 * Lensing creates a child node in the validation tree, so any issues reported on the lensed
 * value will be tracked as a child of this node and propagated up the tree. See [IssueTracking] for details.
 *
 * @see IssueTracking
 * @see MutableReactiveValue
 */
interface MutableValidatedValue<T> : IssueTracking, MutableReactiveValue<T>, ValidatedValue<T>, MutableValidated<T> {
    /**
     * Creates a transforming lens for type conversion.
     * Returns a [MutableValidatedValue] for the sub-value, preserving validation.
     *
     * Lensing creates a child node in the validation tree. Any issues reported on the lensed value
     * will be tracked as a child of this node, and propagated up the tree. See [IssueTracking] for details.
     */
    override fun <L> lens(
        get: (T) -> L,
        set: (L) -> T
    ): MutableValidatedValue<L> = ValidatedSetValueLens(this, get, set)

    /**
     * Creates a lens for a sub-value using a getter and a modifier function.
     * Returns a [MutableValidatedValue] for the sub-value, preserving validation.
     *
     * Lensing creates a child node in the validation tree. Any issues reported on the lensed value
     * will be tracked as a child of this node, and propagated up the tree. See [IssueTracking] for details.
     */
    override fun <L> lens(
        get: (T) -> L,
        modify: (T, L) -> T
    ): MutableValidatedValue<L> = ValidatedModifyValueLens(this, get, modify)
}