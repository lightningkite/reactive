package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue

/**
 * Shortcut for [audit] that reports [validate]'s return value as an [Issue] summary.
 *
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 * @param validate Function that returns a message describing the problem, or null if valid.
 */
public fun <T> MutableValidated<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
): MutableValidated<T> = audit { value ->
    validate(value)?.let {
        Issue(it, setValue = setOnIssue)
    }
}

/**
 * Shortcut for [audit] that reports [validate]'s return value as an [Issue] summary.
 *
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 * @param validate Function that returns a message describing the problem, or null if valid.
 */
public fun <T> MutableValidatedValue<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
): MutableValidatedValue<T> = audit { value ->
    validate(value)?.let {
        Issue(it, setValue = setOnIssue)
    }
}

/**
 * Shortcut for [audit] that reports [summary]/[description] as the [Issue] whenever [condition] fails.
 *
 * @param summary Short description of the issue.
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 * @param condition Function that returns true if valid, false if invalid.
 */
public fun <T> MutableValidated<T>.assert(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: (T) -> Boolean
): MutableValidated<T> = audit {
    if (condition(it)) return@audit null
    Issue(summary, description, setOnIssue)
}

/**
 * Shortcut for [audit] that reports [summary]/[description] as the [Issue] whenever [condition] fails.
 *
 * @param summary Short description of the issue.
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 * @param condition Function that returns true if valid, false if invalid.
 */
public fun <T> MutableValidatedValue<T>.assert(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: (T) -> Boolean
): MutableValidatedValue<T> = audit {
    if (condition(it)) return@audit null
    Issue(summary, description, setOnIssue)
}

/**
 * Shortcut for [assert] (and so [audit]) that reports an issue when the value is null.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 */
public fun <T : Any> MutableValidated<T?>.assertNotNull(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidated<T?> = assert(summary, description, setOnIssue) { it != null }

/**
 * Shortcut for [assert] (and so [audit]) that reports an issue when the value is null.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 */
public fun <T : Any> MutableValidatedValue<T?>.assertNotNull(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidatedValue<T?> = assert(summary, description, setOnIssue) { it != null }

/**
 * Shortcut for [assert] (and so [audit]) that reports an issue when the value is blank.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 */
public fun MutableValidated<String>.assertNotBlank(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidated<String> = assert(summary, description, setOnIssue) { it.isNotBlank() }

/**
 * Shortcut for [assert] (and so [audit]) that reports an issue when the value is blank.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 */
public fun MutableValidatedValue<String>.assertNotBlank(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidatedValue<String> = assert(summary, description, setOnIssue) { it.isNotBlank() }

/**
 * Shortcut for [audit] that reports [validate]'s return value as an [Issue] summary.
 *
 * As this receiver isn't already [Validated], [audit] first wraps it with [validated], establishing a new
 * root of a validation tree. See [audit] for details.
 *
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 * @param validate Function that returns a message describing the problem, or null if valid.
 */
public fun <T> MutableReactive<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
): MutableValidated<T> = audit { value ->
    validate(value)?.let {
        Issue(it, setValue = setOnIssue)
    }
}

/**
 * Shortcut for [audit] that reports [validate]'s return value as an [Issue] summary.
 *
 * As this receiver isn't already [ValidatedValue], [audit] first wraps it with [validated], establishing a new
 * root of a validation tree. See [audit] for details.
 *
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 * @param validate Function that returns a message describing the problem, or null if valid.
 */
public fun <T> MutableReactiveValue<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
): MutableValidatedValue<T> = audit { value ->
    validate(value)?.let {
        Issue(it, setValue = setOnIssue)
    }
}

/**
 * Shortcut for [audit] that reports [summary]/[description] as the [Issue] whenever [condition] fails.
 *
 * As this receiver isn't already [Validated], [audit] first wraps it with [validated], establishing a new
 * root of a validation tree. See [audit] for details.
 *
 * @param summary Short description of the issue.
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 * @param condition Function that returns true if valid, false if invalid.
 */
public fun <T> MutableReactive<T>.assert(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: (T) -> Boolean
): MutableValidated<T> = audit {
    if (condition(it)) return@audit null
    Issue(summary, description, setOnIssue)
}

/**
 * Shortcut for [audit] that reports [summary]/[description] as the [Issue] whenever [condition] fails.
 *
 * As this receiver isn't already [ValidatedValue], [audit] first wraps it with [validated], establishing a new
 * root of a validation tree. See [audit] for details.
 *
 * @param summary Short description of the issue.
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 * @param condition Function that returns true if valid, false if invalid.
 */
public fun <T> MutableReactiveValue<T>.assert(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: (T) -> Boolean
): MutableValidatedValue<T> = audit {
    if (condition(it)) return@audit null
    Issue(summary, description, setOnIssue)
}

/**
 * Shortcut for [assert] (and so [audit]) that reports an issue when the value is null.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 */
public fun <T : Any> MutableReactive<T?>.assertNotNull(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidated<T?> = assert(summary, description, setOnIssue) { it != null }

/**
 * Shortcut for [assert] (and so [audit]) that reports an issue when the value is null.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 */
public fun <T : Any> MutableReactiveValue<T?>.assertNotNull(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidatedValue<T?> = assert(summary, description, setOnIssue) { it != null }

/**
 * Shortcut for [assert] (and so [audit]) that reports an issue when the value is blank.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 */
public fun MutableReactive<String>.assertNotBlank(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidated<String> = assert(summary, description, setOnIssue) { it.isNotBlank() }

/**
 * Shortcut for [assert] (and so [audit]) that reports an issue when the value is blank.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue Used as [Issue.setValue] when an issue is reported.
 */
public fun MutableReactiveValue<String>.assertNotBlank(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidatedValue<String> = assert(summary, description, setOnIssue) { it.isNotBlank() }
