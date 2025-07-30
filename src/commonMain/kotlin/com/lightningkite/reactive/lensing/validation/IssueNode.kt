package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveMutableList
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember

/**
 * Represents a node in a validation tree, responsible for tracking validation issues for a specific part of a data structure.
 *
 * Each node can report its own issue and have child nodes, forming a tree structure. Issues from child nodes are propagated up to their parent.
 * The [issues] property provides a reactive list of all issues for this node and its descendants.
 *
 * Example tree structure:
 * ```
 * root
 * ├── child1
 * │   ├── grandchild1
 * │   └── grandchild2
 * └── child2
 *     └── grandchild3
 * ```
 *
 * Reporting an issue on any node will cause that issue to be included in the parent's [issues] list.
 * Removing a child node will remove its issues from the parent's [issues] list.
 *
 * @property parent The parent node in the validation tree, or null if this is the root.
 */
class IssueNode(val parent: IssueNode?) {
    private val nodeIssue = Signal<Issue?>(null)

    fun report(issue: Issue?) { nodeIssue.value = issue }

    private val children = ReactiveMutableList<IssueNode>()
    fun child() = IssueNode(this).also { children.add(it) }
    fun removeChild(child: IssueNode) = children.remove(child)

    val issues : Reactive<List<Issue>> = remember {
        listOfNotNull(nodeIssue()) + children().flatMap { it.issues() }
    }
}

/**
 * Represents a validation issue, which can be either a warning or an invalid state.
 */
sealed interface Issue {
    val summary: String
    val description: String

    /**
     * Represents a warning issue. Does not necessarily prevent usage, but should be addressed.
     *
     * Values that result in an [Issue.Warning] being reported will still be used.
     */
    data class Warning(
        override val summary: String,
        override val description: String = summary
    ) : Issue

    /**
     * Represents an invalid issue. Indicates a state that must be corrected before proceeding.
     *
     * Values that result in an [Issue.Invalid] being reported will be **discarded**.
     * I.e., if a lensed child of a [MutableValidated] reports [Issue.Invalid] on a value, it will not modify its parent.
     */
    data class Invalid(
        override val summary : String,
        override val description: String = summary
    ) : Issue
}