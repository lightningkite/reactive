package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.asDouble

fun <T> MutableValidated<T>.checkForIssue(
    name: String? = null,
    validate: (T) -> Issue?
) = checkForIssues(name) {
    validate(it)?.let(::listOf)
}

fun <T> MutableValidatedValue<T>.checkForIssue(
    name: String? = null,
    validate: (T) -> Issue?
) = checkForIssues(name) {
    validate(it)?.let(::listOf)
}

fun <T> MutableValidated<T>.validate(
    name: String? = null,
    setOnIssue: Boolean = true,
    validate: (T) -> String?
) = checkForIssue(name) { value ->
    validate(value)?.let {
        if (setOnIssue) Issue.Warning(it)
        else Issue.Invalid(it)
    }
}

fun <T> MutableValidatedValue<T>.validate(
    name: String? = null,
    setOnIssue: Boolean = true,
    validate: (T) -> String?
) = checkForIssue(name) { value ->
    validate(value)?.let {
        if (setOnIssue) Issue.Warning(it)
        else Issue.Invalid(it)
    }
}

fun <T> MutableValidated<T>.assert(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: (T) -> Boolean
) = checkForIssue {
    if (condition(it)) return@checkForIssue null

    if (setOnIssue) Issue.Warning(summary, description)
    else Issue.Invalid(summary, description)
}

fun <T> MutableValidatedValue<T>.assert(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: (T) -> Boolean
) = checkForIssue {
    if (condition(it)) return@checkForIssue null

    if (setOnIssue) Issue.Warning(summary, description)
    else Issue.Invalid(summary, description)
}

fun <T : Any> MutableValidated<T?>.validateNotNull(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it != null }

fun <T : Any> MutableValidatedValue<T?>.validateNotNull(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it != null }

fun MutableValidated<String>.validateNotBlank(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it.isNotBlank() }


fun MutableValidatedValue<String>.validateNotBlank(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it.isNotBlank() }




fun <T> MutableReactive<T>.checkForIssue(
    name: String? = null,
    validate: (T) -> Issue?
) = checkForIssues(name) {
    validate(it)?.let(::listOf)
}

fun <T> MutableReactiveValue<T>.checkForIssue(
    name: String? = null,
    validate: (T) -> Issue?
) = checkForIssues(name) {
    validate(it)?.let(::listOf)
}

fun <T> MutableReactive<T>.validate(
    name: String? = null,
    setOnIssue: Boolean = true,
    validate: (T) -> String?
) = checkForIssue(name) { value ->
    validate(value)?.let {
        if (setOnIssue) Issue.Warning(it)
        else Issue.Invalid(it)
    }
}

fun <T> MutableReactiveValue<T>.validate(
    name: String? = null,
    setOnIssue: Boolean = true,
    validate: (T) -> String?
) = checkForIssue(name) { value ->
    validate(value)?.let {
        if (setOnIssue) Issue.Warning(it)
        else Issue.Invalid(it)
    }
}

fun <T> MutableReactive<T>.assert(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: (T) -> Boolean
) = checkForIssue {
    if (condition(it)) return@checkForIssue null

    if (setOnIssue) Issue.Warning(summary, description)
    else Issue.Invalid(summary, description)
}

fun <T> MutableReactiveValue<T>.assert(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: (T) -> Boolean
) = checkForIssue {
    if (condition(it)) return@checkForIssue null

    if (setOnIssue) Issue.Warning(summary, description)
    else Issue.Invalid(summary, description)
}

fun <T : Any> MutableReactive<T?>.validateNotNull(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it != null }

fun <T : Any> MutableReactiveValue<T?>.validateNotNull(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it != null }

fun MutableReactive<String>.validateNotBlank(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it.isNotBlank() }

fun MutableReactiveValue<String>.validateNotBlank(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it.isNotBlank() }



data class Thingy(
    val x: Int,
    val y: Int?,
)
fun test() {
    val signal = Signal(Thingy(0, null)).validated()

    val x = signal.lens(
        get = { it.x },
        modify = { o, it -> o.copy(x = it) }
    )

    val input = x.validate {
        if (it <= 0) "Must be greater than 0"
        else if (it > 10) "Must be less than 10"
        else null
    }

    val y = signal
        .lens(
            get = { it.y },
            modify = { o, it -> o.copy(y = it) }
        )
        .asDouble()
        .assert("Must be empty or negative") {
            it == null || it < 0
        }
}