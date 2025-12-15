# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **Reactive**, a Kotlin Multiplatform library for building reactive applications. It provides core abstractions for managing state, observing changes, and composing reactive data flow, inspired by concepts from Solid.js. The library supports JVM, JS, iOS (x64, arm64, simulator), and Android platforms.

## Build Commands

### Testing
- `./gradlew jvmTest` - Run tests on JVM platform
- `./gradlew jsTest` - Run tests on JS platform (uses Karma + Chrome Headless + Firefox)
- `./gradlew jsBrowserTest` - Run all JS tests inside browser
- `./gradlew iosX64Test` - Run tests on iOS x64 simulator
- `./gradlew iosSimulatorArm64Test` - Run tests on iOS simulator (ARM64)
- `./gradlew allTests` - Run tests for all targets and create aggregated report
- `./gradlew check` - Run all checks

### Running a Single Test
Use IntelliJ/JetBrains run configurations to run individual test classes or methods. The codebase uses standard Kotlin test annotations.

### Building
- `./gradlew build` - Assemble and test the project
- `./gradlew assemble` - Assemble all outputs without testing
- `./gradlew clean` - Delete build directory

### Publishing
- `./gradlew publishToMavenLocal` - Publish to local Maven repository (useful for testing)
- `./gradlew publishToMavenCentral` - Publish to Maven Central
- `./gradlew publishAndReleaseToMavenCentral` - Publish and automatically trigger release

## Architecture Overview

### Core Package (`com.lightningkite.reactive.core`)

The foundation of the reactivity system:

- **ReactiveState<T>**: Value class wrapping a reactive value's state (ready, loading, error)
- **Reactive<T>**: Core interface for observable reactive values
- **Signal<T>**: The most basic mutable reactive container - notifies listeners when changed
- **LateInitSignal<T>**: Like Signal but starts in loading state until first value is set
- **RawReactive<T>**: Low-level reactive value that exposes its state for direct mutation

### Context Package (`com.lightningkite.reactive.context`)

Manages reactive computations and their dependencies:

- **ReactiveContext**: Environment for observing and reacting to Reactive changes
- **TypedReactiveContext<T>**: Implementation that tracks dependencies and automatically reruns calculations when dependencies change
- Uses thread-local `reactiveContext` variable to track the currently active context
- When `Reactive.invoke()` is called inside a context, that reactive value is registered as a dependency

### Core Reactive Types

- **remember()**: Creates a reactive value that automatically recalculates when dependencies change (lazy - only calculates when it has listeners)
- **rememberSuspending()**: Suspending version of remember for async calculations
- **Draft<T>**: Buffers changes to a MutableReactive for later commit/cancel (like a transaction)
- **ReactiveMutableList/Map/Set**: Reactive collections that notify on changes

### Lensing Package (`com.lightningkite.reactive.lensing`)

Provides "lensing" - transforming views of reactive data:

- **Lens<S, T, L>**: Read-only transformed view of a reactive value
- **SetLens<O, T>**: Bidirectional lens that can read and write through transformations
- Lenses are lazy and only activate when they have listeners

### Validation Package (`com.lightningkite.reactive.lensing.validation`)

Hierarchical validation system:

- **IssueNode**: Tree structure for tracking validation issues
- **Validated<T>**: Reactive value with associated validation state
- Issues propagate up the tree from children to parents

### Extensions Package (`com.lightningkite.reactive.extensions`)

Utility functions for common reactive patterns (debouncing, common lenses, helpers)

## Key Design Patterns

### Dependency Tracking
Reactive values accessed via `invoke()` inside a ReactiveContext are automatically tracked as dependencies. When any dependency changes, the context reruns its calculation.

```kotlin
val a = Signal(1)
val b = Signal(2)
reactive {
    println(a() + b())  // Both a and b are registered as dependencies
}
```

### Loading State Management
ReactiveState handles three states: ready (has value), loading (not ready), and error (exception). Contexts automatically handle loading states - if a reactive value is loading, the context waits.

### Lazy Evaluation
Many reactive constructs (like `remember`) are lazy - they don't calculate values until they have listeners. This improves performance by avoiding unnecessary calculations.

### Resource Management
Reactive contexts and lenses use activation/deactivation hooks to manage listeners and resources efficiently. Resources are tied to CoroutineScope lifetimes.

## Testing

Tests are in `src/commonTest/kotlin/com/lightningkite/reactive/`. Key test files:
- ReactivityTests.kt - Basic reactive behavior
- RememberTests.kt - Testing remember() function
- ValidationTests.kt - Validation system tests
- Tests for suspending versions of reactive constructs

## Important Notes

- The library uses `@OptIn(InternalReactiveApi::class)` for internal implementation details
- Kotlin compiler args include `-Xexpect-actual-classes` for multiplatform support
- Uses kotlinx-coroutines for async/suspending operations
- AppScope is a provided top-level scope that lives as long as the app is running
