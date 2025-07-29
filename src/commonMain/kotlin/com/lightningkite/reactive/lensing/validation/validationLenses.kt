package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.extensions.onNextSuccess
import com.lightningkite.reactive.lensing.ModifyLens
import com.lightningkite.reactive.lensing.ModifyValueLens
import com.lightningkite.reactive.lensing.SetLens
import com.lightningkite.reactive.lensing.SetValueLens

class ValidatedSetLens<T, L>(
    name: String?,
    source: MutableValidated<T>,
    get: (T) -> L,
    set: (L) -> T
) : MutableValidated<L>, SetLens<T, L>(source, get, set) {
    override val node: IssueNode = source.node.childNode(name)
}

class ValidatedModifyLens<T, L>(
    name: String?,
    source: MutableValidated<T>,
    get: (T) -> L,
    modify: (T, L) -> T
) : MutableValidated<L>, ModifyLens<T, L>(source, get, modify) {
    override val node: IssueNode = source.node.childNode(name)
}

class ValidatedSetValueLens<T, L>(
    name: String?,
    source: MutableValidatedValue<T>,
    get: (T) -> L,
    set: (L) -> T
) : MutableValidatedValue<L>, SetValueLens<T, L>(source, get, set) {
    override val node: IssueNode = source.node.childNode(name)
}

class ValidatedModifyValueLens<T, L>(
    name: String?,
    source: MutableValidatedValue<T>,
    get: (T) -> L,
    modify: (T, L) -> T
) : MutableValidatedValue<L>, ModifyValueLens<T, L>(source, get, modify) {
    override val node: IssueNode = source.node.childNode(name)
}


// Named anonymous object
private class ValidationWrapper<T>(
    name: String?,
    val wrapped: MutableReactive<T>
) : MutableValidated<T>, Reactive<T> by wrapped {
    override val node: IssueNode = IssueNode(name)
    override suspend fun set(value: T) = wrapped.set(value)
}

private class ValidationLens<T>(
    name: String?,
    private val source: MutableValidated<T>,
    private val validate: (T) -> List<Issue>?
) : MutableValidated<T>, Reactive<T> by source {
    override val node: IssueNode = source.node.childNode(name)

    private fun check(value: T): Boolean {
        val issues = validate(value)
        return if (issues == null || issues.isEmpty()) {
            node.nodeIssues.clear()
            true
        }
        else {
            node.nodeIssues.addAll(issues)
            issues.none { it is Issue.Invalid }
        }
    }
    override suspend fun set(value: T) {
        if (check(value)) source.set(value)
    }
    init {
        source.onNextSuccess { check(it) }
    }
}

// Named anonymous object
private class ValidationValueWrapper<T>(
    name: String?,
    val wrapped: MutableReactiveValue<T>
) : MutableValidatedValue<T>, Listenable by wrapped {
    override val node: IssueNode = IssueNode(name)
    override var value: T by wrapped::value
}

private class ValidationValueLens<T>(
    name: String?,
    private val source: MutableValidatedValue<T>,
    private val validate: (T) -> List<Issue>?
) : MutableValidatedValue<T>, Listenable by source {
    override val node: IssueNode = source.node.childNode(name)

    private fun check(value: T): Boolean {
        val issues = validate(value)
        return if (issues == null || issues.isEmpty()) {
            node.nodeIssues.clear()
            true
        }
        else {
            node.nodeIssues.addAll(issues)
            issues.none { it is Issue.Invalid }
        }
    }
    override var value: T
        get() = source.value
        set(value) {
            if (check(value)) source.value = value
        }
    init {
        check(source.value)
    }
}

fun <T> MutableReactive<T>.validated(name: String? = null): MutableValidated<T> =
    this as? MutableValidated<T> ?: ValidationWrapper(name, this)

fun <T> MutableValidated<T>.checkForIssues(
    name: String?,
    validate: (T) -> List<Issue>?
): MutableValidated<T> = ValidationLens(name, this, validate)

fun <T> MutableReactive<T>.checkForIssues(
    name: String?,
    validate: (T) -> List<Issue>?
): MutableValidated<T> = ValidationLens(name, this.validated(), validate)


fun <T> MutableReactiveValue<T>.validated(name: String? = null): MutableValidatedValue<T> =
    this as? MutableValidatedValue<T> ?: ValidationValueWrapper(name, this)

fun <T> MutableValidatedValue<T>.checkForIssues(
    name: String?,
    validate: (T) -> List<Issue>?
): MutableValidatedValue<T> = ValidationValueLens(name, this, validate)

fun <T> MutableReactiveValue<T>.checkForIssues(
    name: String?,
    validate: (T) -> List<Issue>?
): MutableValidatedValue<T> = ValidationValueLens(name, this.validated(), validate)
