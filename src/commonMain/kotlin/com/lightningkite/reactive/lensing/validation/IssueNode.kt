package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveMutableList
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember

class IssueNode(parent: IssueNode?) {
    private val nodeIssue = Signal<Issue?>(null)

    fun report(issue: Issue?) { nodeIssue.value = issue }

    private val children = ReactiveMutableList<IssueNode>()
    fun childNode() = IssueNode(this).also { children.add(it) }
    fun removeChild(child: IssueNode) = children.remove(child)

    val issues : Reactive<List<Issue>> = remember {
        listOfNotNull(nodeIssue()) + children().flatMap { it.issues() }
    }
}

sealed interface Issue {
    val summary: String
    val description: String

    data class Warning(
        override val summary: String,
        override val description: String = summary
    ) : Issue

    data class Invalid(
        override val summary : String,
        override val description: String = summary
    ) : Issue
}