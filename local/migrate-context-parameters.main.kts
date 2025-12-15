#!/usr/bin/env kotlin

/**
 * Migration script for converting ReactiveContext receiver types to context parameters.
 *
 * This script automates the transformation of function signatures from:
 *   action: ReactiveContext.() -> T
 * to:
 *   action: context(ReactiveContext) () -> T
 *
 * Usage: Run this script to create .migrated files for review
 */

import java.io.File

val projectRoot = File(".").canonicalFile

// Files to transform
val filesToTransform = listOf(
    "src/commonMain/kotlin/com/lightningkite/reactive/core/Remember.kt",
    "src/commonMain/kotlin/com/lightningkite/reactive/core/RememberSuspending.kt",
    "src/commonMain/kotlin/com/lightningkite/reactive/core/MutableRemember.kt",
    "src/commonMain/kotlin/com/lightningkite/reactive/core/Draft.kt",
    "src/commonMain/kotlin/com/lightningkite/reactive/context/ReactiveContext.kt",
    "src/commonMain/kotlin/com/lightningkite/reactive/context/CoroutineScopeHelpers.kt",
    "src/commonMain/kotlin/com/lightningkite/reactive/lensing/validation/helpers.kt",
    "src/commonMain/kotlin/com/lightningkite/reactive/lensing/validation/IssueNode.kt",
    "src/commonMain/kotlin/com/lightningkite/reactive/extensions/helpers.kt"
)

// Pattern 1: Simple lambda parameters without value parameters
// Matches: paramName: ReactiveContext.() -> ReturnType
// Captures: (optional modifiers) (param name) (return type)
val simplePattern = Regex(
    """((?:(?:cross)?inline\s+)?(?:suspend\s+)?)(\w+):\s*ReactiveContext\.\(\)\s*->\s*([^,\)]+)"""
)

// Pattern 2: Lambda parameters with value parameters
// Matches: paramName: ReactiveContext.(T) -> ReturnType
// Captures: (optional modifiers) (param name) (value params) (return type)
val withArgsPattern = Regex(
    """((?:(?:cross)?inline\s+)?(?:suspend\s+)?)(\w+):\s*ReactiveContext\.\(([^)]+)\)\s*->\s*([^,\)]+)"""
)

// Pattern 3: val/var declarations with ReactiveContext receiver
// Matches: val action: TypedReactiveContext<T>.() -> T
val valPattern = Regex(
    """((?:private\s+)?(?:val|var)\s+)(\w+):\s*TypedReactiveContext<([^>]+)>\.\(\)\s*->\s*([^,\)\{]+)"""
)

fun transformLine(line: String): String {
    var result = line

    // Transform val/var declarations first (more specific)
    result = valPattern.replace(result) { match ->
        val prefix = match.groupValues[1]  // "val " or "private val "
        val paramName = match.groupValues[2]
        val genericType = match.groupValues[3]
        val returnType = match.groupValues[4].trim()
        "$prefix$paramName: context(ReactiveContext) () -> $returnType"
    }

    // Transform lambda parameters with value parameters
    result = withArgsPattern.replace(result) { match ->
        val modifiers = match.groupValues[1]
        val paramName = match.groupValues[2]
        val valueParams = match.groupValues[3]
        val returnType = match.groupValues[4].trim()
        "$modifiers$paramName: context(ReactiveContext) ($valueParams) -> $returnType"
    }

    // Transform simple lambda parameters
    result = simplePattern.replace(result) { match ->
        val modifiers = match.groupValues[1]
        val paramName = match.groupValues[2]
        val returnType = match.groupValues[3].trim()
        "$modifiers$paramName: context(ReactiveContext) () -> $returnType"
    }

    return result
}

fun processFile(relativePath: String): Boolean {
    val file = File(projectRoot, relativePath)
    if (!file.exists()) {
        println("⚠️  File not found: $relativePath")
        return false
    }

    val lines = file.readLines()
    val transformedLines = lines.map { transformLine(it) }

    // Check if any changes were made
    val hasChanges = lines.zip(transformedLines).any { (original, transformed) ->
        original != transformed
    }

    if (!hasChanges) {
        println("✓ No changes needed: $relativePath")
        return false
    }

    // Write to .migrated file
    val migratedFile = File(file.parentFile, "${file.name}.migrated")
    migratedFile.writeText(transformedLines.joinToString("\n"))

    println("✓ Transformed: $relativePath")
    println("  Output: ${migratedFile.relativeTo(projectRoot)}")

    // Show diff summary
    val changes = lines.zip(transformedLines)
        .withIndex()
        .filter { (_, pair) -> pair.first != pair.second }

    println("  Changes: ${changes.size} line(s)")
    changes.take(5).forEach { (index, pair) ->
        println("    Line ${index + 1}:")
        println("      - ${pair.first.trim()}")
        println("      + ${pair.second.trim()}")
    }
    if (changes.size > 5) {
        println("    ... and ${changes.size - 5} more")
    }
    println()

    return true
}

fun main() {
    println("=" .repeat(60))
    println("ReactiveContext Migration Script")
    println("Converting receiver types to context parameters")
    println("=" .repeat(60))
    println()

    var totalTransformed = 0

    filesToTransform.forEach { path ->
        if (processFile(path)) {
            totalTransformed++
        }
    }

    println()
    println("=" .repeat(60))
    println("Summary:")
    println("  Files transformed: $totalTransformed / ${filesToTransform.size}")
    println()
    println("Next steps:")
    println("  1. Review the .migrated files")
    println("  2. Apply changes manually or use:")
    println("     find . -name '*.migrated' -exec sh -c 'mv \"\$1\" \"\${1%.migrated}\"' _ {} \\;")
    println("  3. Continue with manual migration of call sites")
    println("=" .repeat(60))
}

main()
