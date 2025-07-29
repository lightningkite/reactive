package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue

fun <T> MutableValidated<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
) = checkForIssue { value ->
    validate(value)?.let {
        if (setOnIssue) Issue.Warning(it)
        else Issue.Invalid(it)
    }
}

fun <T> MutableValidatedValue<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
) = checkForIssue { value ->
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



fun <T> MutableReactive<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
) = checkForIssue { value ->
    validate(value)?.let {
        if (setOnIssue) Issue.Warning(it)
        else Issue.Invalid(it)
    }
}

fun <T> MutableReactiveValue<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
) = checkForIssue { value ->
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
