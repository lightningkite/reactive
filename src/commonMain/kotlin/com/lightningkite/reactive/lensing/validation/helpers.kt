package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.Release
import com.lightningkite.reactive.extensions.use
import kotlinx.coroutines.CoroutineScope

/**
 * Adds a validation check to this [MutableValidated] instance.
 *
 * The [validate] function should return a string describing the issue if the value is invalid, or null if valid.
 * If an issue is found, it will be reported as either a [Issue.Warning] or [Issue.Invalid] depending on [setOnIssue].
 *
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 * @param validate Function that returns a string issue message or null for valid values.
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
 * Adds a validation check to this [MutableValidatedValue] instance.
 *
 * The [validate] function should return a string describing the issue if the value is invalid, or null if valid.
 * If an issue is found, it will be reported as either a [Issue.Warning] or [Issue.Invalid] depending on [setOnIssue].
 *
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 * @param validate Function that returns a string issue message or null for valid values.
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
 * Asserts a condition on this [MutableValidated] instance and reports an issue if the condition fails.
 *
 * @param summary Short description of the issue.
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 * @param condition Function that returns true if valid, false if invalid.
 */
public fun <T> MutableValidated<T>.assert(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: (T) -> Boolean
): MutableValidated<T> = audit {
    if (condition(it)) return@audit null

    if (setOnIssue) Issue.Warning(summary, description)
    else Issue.Invalid(summary, description)
}

/**
 * Asserts a condition on this [MutableValidatedValue] instance and reports an issue if the condition fails.
 *
 * @param summary Short description of the issue.
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 * @param condition Function that returns true if valid, false if invalid.
 */
public fun <T> MutableValidatedValue<T>.assert(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: (T) -> Boolean
): MutableValidatedValue<T> = audit {
    if (condition(it)) return@audit null

    if (setOnIssue) Issue.Warning(summary, description)
    else Issue.Invalid(summary, description)
}

/**
 * Validates that the value of this [MutableValidated] is not null.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 */
public fun <T : Any> MutableValidated<T?>.assertNotNull(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidated<T?> = assert(summary, description, setOnIssue) { it != null }

/**
 * Validates that the value of this [MutableValidatedValue] is not null.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 */
public fun <T : Any> MutableValidatedValue<T?>.assertNotNull(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidatedValue<T?> = assert(summary, description, setOnIssue) { it != null }

/**
 * Validates that the value of this [MutableValidated] is not blank.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 */
public fun MutableValidated<String>.assertNotBlank(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidated<String> = assert(summary, description, setOnIssue) { it.isNotBlank() }

/**
 * Validates that the value of this [MutableValidatedValue] is not blank.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 */
public fun MutableValidatedValue<String>.assertNotBlank(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidatedValue<String> = assert(summary, description, setOnIssue) { it.isNotBlank() }

/**
 * Adds a validation check to this [MutableReactive] instance.
 *
 * The [validate] function should return a string describing the issue if the value is invalid, or null if valid.
 * If an issue is found, it will be reported as either a [Issue.Warning] or [Issue.Invalid] depending on [setOnIssue].
 *
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 * @param validate Function that returns a string issue message or null for valid values.
 */
public fun <T> MutableReactive<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
): MutableValidated<T> = audit { value ->
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
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 * @param validate Function that returns a string issue message or null for valid values.
 */
public fun <T> MutableReactiveValue<T>.validate(
    setOnIssue: Boolean = true,
    validate: (T) -> String?
): MutableValidatedValue<T> = audit { value ->
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
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 * @param condition Function that returns true if valid, false if invalid.
 */
public fun <T> MutableReactive<T>.assert(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: (T) -> Boolean
): MutableValidated<T> = audit {
    if (condition(it)) return@audit null

    if (setOnIssue) Issue.Warning(summary, description)
    else Issue.Invalid(summary, description)
}

/**
 * Asserts a condition on this [MutableReactiveValue] instance and reports an issue if the condition fails.
 *
 * @param summary Short description of the issue.
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 * @param condition Function that returns true if valid, false if invalid.
 */
public fun <T> MutableReactiveValue<T>.assert(
    summary: String,
    description: String = summary,
    setOnIssue: Boolean = true,
    condition: (T) -> Boolean
): MutableValidatedValue<T> = audit {
    if (condition(it)) return@audit null

    if (setOnIssue) Issue.Warning(summary, description)
    else Issue.Invalid(summary, description)
}

/**
 * Validates that the value of this [MutableReactive] is not null.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 */
public fun <T : Any> MutableReactive<T?>.assertNotNull(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidated<T?> = assert(summary, description, setOnIssue) { it != null }

/**
 * Validates that the value of this [MutableReactiveValue] is not null.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 */
public fun <T : Any> MutableReactiveValue<T?>.assertNotNull(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidatedValue<T?> = assert(summary, description, setOnIssue) { it != null }

/**
 * Validates that the value of this [MutableReactive] is not blank.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 */
public fun MutableReactive<String>.assertNotBlank(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidated<String> = assert(summary, description, setOnIssue) { it.isNotBlank() }

/**
 * Validates that the value of this [MutableReactiveValue] is not blank.
 *
 * @param summary Short description of the issue (defaults to "Cannot be blank").
 * @param description Detailed description of the issue (defaults to [summary]).
 * @param setOnIssue The setting used for [Issue.setValue] when issues are reported.
 */
public fun MutableReactiveValue<String>.assertNotBlank(
    summary: String = "Cannot be blank",
    description: String = summary,
    setOnIssue: Boolean = true
): MutableValidatedValue<String> = assert(summary, description, setOnIssue) { it.isNotBlank() }
