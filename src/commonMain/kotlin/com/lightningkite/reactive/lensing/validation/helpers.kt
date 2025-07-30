package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue

/**
 * Adds a validation check to this [MutableValidated] instance.
 *
 * The [validate] function should return a string describing the issue if the value is invalid, or null if valid.
 * If an issue is found, it will be reported as either a [Issue.Warning] or [Issue.Invalid] depending on [setOnIssue].
 *
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 * @param validate Function that returns a string issue message or null for valid values.
 */
fun <T> MutableValidated<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
) = checkForIssue { value ->
    validate(value)?.let {
        if (setOnIssue) Issue.Warning(it)
        else Issue.Invalid(it)
    }
}

/**
 * Adds a validation check to this [MutableValidatedValue] instance.
 *
 * The [validate] function should return a string describing the issue if the value is invalid, or null if valid.
 * If an issue is found, it will be reported as either a [Issue.Warning] or [Issue.Invalid] depending on [setOnIssue].
 *
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 * @param validate Function that returns a string issue message or null for valid values.
 */
fun <T> MutableValidatedValue<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
) = checkForIssue { value ->
    validate(value)?.let {
        if (setOnIssue) Issue.Warning(it)
        else Issue.Invalid(it)
    }
}

/**
 * Asserts a condition on this [MutableValidated] instance and reports an issue if the condition fails.
 *
 * @param summary Short description of the issue.
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 * @param condition Function that returns true if valid, false if invalid.
 */
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

/**
 * Asserts a condition on this [MutableValidatedValue] instance and reports an issue if the condition fails.
 *
 * @param summary Short description of the issue.
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 * @param condition Function that returns true if valid, false if invalid.
 */
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

/**
 * Validates that the value of this [MutableValidated] is not null.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank.").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 */
fun <T : Any> MutableValidated<T?>.validateNotNull(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it != null }

/**
 * Validates that the value of this [MutableValidatedValue] is not null.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank.").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 */
fun <T : Any> MutableValidatedValue<T?>.validateNotNull(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it != null }

/**
 * Validates that the value of this [MutableValidated] is not blank (for String values).
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank.").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 */
fun MutableValidated<String>.validateNotBlank(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it.isNotBlank() }

/**
 * Validates that the value of this [MutableValidatedValue] is not blank (for String values).
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank.").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 */
fun MutableValidatedValue<String>.validateNotBlank(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it.isNotBlank() }

/**
 * Adds a validation check to this [MutableReactive] instance.
 *
 * The [validate] function should return a string describing the issue if the value is invalid, or null if valid.
 * If an issue is found, it will be reported as either a [Issue.Warning] or [Issue.Invalid] depending on [setOnIssue].
 *
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 * @param validate Function that returns a string issue message or null for valid values.
 */
fun <T> MutableReactive<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
) = checkForIssue { value ->
    validate(value)?.let {
        if (setOnIssue) Issue.Warning(it)
        else Issue.Invalid(it)
    }
}

/**
 * Adds a validation check to this [MutableReactiveValue] instance.
 *
 * The [validate] function should return a string describing the issue if the value is invalid, or null if valid.
 * If an issue is found, it will be reported as either a [Issue.Warning] or [Issue.Invalid] depending on [setOnIssue].
 *
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 * @param validate Function that returns a string issue message or null for valid values.
 */
fun <T> MutableReactiveValue<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
) = checkForIssue { value ->
    validate(value)?.let {
        if (setOnIssue) Issue.Warning(it)
        else Issue.Invalid(it)
    }
}

/**
 * Asserts a condition on this [MutableReactive] instance and reports an issue if the condition fails.
 *
 * @param summary Short description of the issue.
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 * @param condition Function that returns true if valid, false if invalid.
 */
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

/**
 * Asserts a condition on this [MutableReactiveValue] instance and reports an issue if the condition fails.
 *
 * @param summary Short description of the issue.
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 * @param condition Function that returns true if valid, false if invalid.
 */
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

/**
 * Validates that the value of this [MutableReactive] is not null.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank.").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 */
fun <T : Any> MutableReactive<T?>.validateNotNull(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it != null }

/**
 * Validates that the value of this [MutableReactiveValue] is not null.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank.").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 */
fun <T : Any> MutableReactiveValue<T?>.validateNotNull(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it != null }

/**
 * Validates that the value of this [MutableReactive] is not blank (for String values).
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank.").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 */
fun MutableReactive<String>.validateNotBlank(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it.isNotBlank() }

/**
 * Validates that the value of this [MutableReactiveValue] is not blank (for String values).
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank.").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue If true, issues are reported as [Issue.Warning]; if false, as [Issue.Invalid].
 */
fun MutableReactiveValue<String>.validateNotBlank(
    summary: String = "Cannot be blank.",
    description: String = summary,
    setOnIssue: Boolean = true
) = assert(summary, description, setOnIssue) { it.isNotBlank() }
