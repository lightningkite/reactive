package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveMutableList
import com.lightningkite.reactive.core.remember

class IssueNode(val name: String?) {
    val nodeIssues = ReactiveMutableList<Issue>()

    private val children = ReactiveMutableList<IssueNode>()
    fun childNode(name: String?) = IssueNode(name).also { children.add(it) }
    fun removeChild(child: IssueNode) = children.remove(child)

    val issues : Reactive<Map<IssueNode, List<Issue>>> = remember {
        buildMap {
            nodeIssues()
                .takeUnless { it.isEmpty() }
                ?.let {
                    put(this@IssueNode, it)
                }

            for (child in children()) {
                putAll(child.issues())
            }
        }
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

fun Map<IssueNode, List<Issue>>.joinToString(transform: (Issue)->String = { it.summary }) = buildString {
    val (named, unnamed) = entries.partition { it.key.name != null }
    if (named.isNotEmpty()) named.forEach { (node, issues) ->
        append("${node.name} : ")
        when (issues.size) {
            0 -> append("No Issues")    // This shouldn't happen
            1 -> append(transform(issues[0]))
            else -> {
                append("Multiple Issues")
                issues.joinTo(this, "\n\t- ", prefix = "\n\t- ", transform = transform)
            }
        }
        append('\n')
    }
    if (unnamed.isEmpty()) return@buildString
    if (named.isNotEmpty()) appendLine("\nUnnamed Issues")
    unnamed.flatMap { it.value }.joinTo(this, "\n\t- ", prefix = "\n\t- ", transform = transform)
}