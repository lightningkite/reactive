package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.core.RawReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveMutableList
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.Release
import com.lightningkite.reactive.core.ResourceUse
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
 * Note: When created, IssueNodes are not by-default connected to their parent's validation tree.
 * This is done to help manage dependencies, and avoid deadlocks in validation. If this wasn't done
 * there would be some cases where a validation lens would be discarded, but it's validation issues
 * would still propagate up the tree, even though there's no way to clear the issues. To connect an
 * IssueNode
 *
 * @property parent The parent node in the validation tree, or null if this is the root.
 */
public class IssueNode(public val parent: IssueNode? = null) : ResourceUse {
    private val nodeIssue = RawReactive<Issue?>(
        if (parent == null) ReactiveState(null) else ReactiveState.notActive
    )

    public fun reportRaw(issue: ReactiveState<Issue?>) { nodeIssue.state = issue }

    public fun report(issue: Issue?) { nodeIssue.state = ReactiveState(issue) }

    private val children = ReactiveMutableList<IssueNode>()

    /**
     * Creates a child of this [IssueNode], connecting it to the validation tree by default.
     *
     * @param connect If true (the default), the new node is connected immediately, as if by [connect].
     * Pass `false` to create the node detached, when you intend to manage its connection yourself — for
     * example, tying it to a [com.lightningkite.reactive.core.ResourceUse]-style lifecycle instead.
     *
     * Note: Calling this with `connect = true` is **NOT** safe to do from inside a [ReactiveContext],
     * unless you plan to manage the lifetime of the node manually. Doing so will create a new child every
     * time the context reruns, and will probably leave orphaned nodes that you are unable to clear the
     * issues on.
     *
     * Instead, create the child outside of the [ReactiveContext] (optionally with `connect = false` if you
     * want to control its connection separately) and report to it from inside any reactive code. The
     * [reportReactive] helper does exactly this, tying the child's connection to a [kotlinx.coroutines.CoroutineScope].
     * */
    public fun child(connect: Boolean = true): IssueNode = IssueNode(this).also { if (connect) it.connect() }

    private var connected = parent == null
    private var saveState: ReactiveState<Issue?> = ReactiveState(null)

    /**
     * Grafts this node and its children to its parent's validation tree.
     * This means that this node's issues will propagate to its parent.
     *
     * Useful for establishing validation dependencies once a set of data has become relevant.
     * */
    public fun connect() {
        if (connected || parent == null) return
        connected = true
        nodeIssue.state = saveState
        parent.children.add(this)
    }
    /**
     * Prunes this node and its children from its parent's validation tree.
     * This means that this node's issues will no longer propagate to its parent.
     *
     * Useful for removing validation dependencies on data that is no longer relevant.
     * */
    public fun disconnect() {
        if (!connected || parent == null) return
        connected = false
        saveState = nodeIssue.state
        nodeIssue.state = ReactiveState.notActive
        parent.children.remove(this)
    }

    override fun beginUse(): Release {
        connect()
        return ::disconnect
    }

    public val issues: Reactive<List<Issue>> = remember {
        listOfNotNull(nodeIssue()) + children().flatMap { it.issues() }
    }
}

/**
 * Represents a single validation issue reported on an [IssueNode].
 *
 * @property summary Short description of the issue, suitable for compact UI.
 * @property description Detailed description of the issue (defaults to [summary]).
 * @property setValue Controls whether a value that triggered this issue is still written through to the
 * underlying source. When `true` (the old "Warning" behavior), the new value is forwarded to the source
 * despite the issue, so the underlying data is updated and the issue is reported alongside it. When `false`
 * (the old "Invalid" behavior), the write is rejected and the source keeps its previous value — see
 * [MutableValidated.audit] and [MutableValidated.auditReactive] for the write-checking functions that use it.
 */
public data class Issue(
    val summary: String,
    val description: String = summary,
    val setValue: Boolean = true
) {
    @Deprecated("No longer needed, just use `Issue` by itself")
    public typealias Warning = Issue
    @Deprecated("No longer needed, just use `Issue` by itself")
    public typealias Invalid = Issue

    public companion object {
        @Deprecated("No longer needed, just use `Issue` by itself", ReplaceWith("Issue(summary, description, setValue = true)"))
        @Suppress("FunctionName")
        public fun Warning(summary: String, description: String = summary): Issue = Issue(summary, description, setValue = true)

        @Deprecated("No longer needed, just use `Issue` by itself", ReplaceWith("Issue(summary, description, setValue = false)"))
        @Suppress("FunctionName")
        public fun Invalid(summary: String, description: String = summary): Issue = Issue(summary, description, setValue = false)
    }
}