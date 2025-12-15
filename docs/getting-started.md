# Getting Started

This guide will help you get started with the Reactive library.

## Installation

Add Reactive to your Kotlin Multiplatform project by adding the dependency to your `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.lightningkite:reactive:<version>")
            }
        }
    }
}
```

For the latest version, check [Maven Central](https://central.sonatype.com/artifact/com.lightningkite/reactive).

## Your First Reactive Application

Let's start with a simple example that demonstrates the core concepts of Reactive:

```kotlin
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.context.*

fun main() {
    // Create a reactive signal - a mutable reactive value
    val counter = Signal(0)

    // Create a reactive scope that automatically tracks dependencies
    AppScope.reactiveScope {
        println("Counter value: ${counter()}")
    }

    // Changing the signal's value triggers the reactive scope
    counter.value = 1  // Prints: "Counter value: 1"
    counter.value = 2  // Prints: "Counter value: 2"
}
```

### What's Happening?

1. **Signal** - A `Signal` is the most basic reactive container. It holds a value and notifies listeners when the value changes.

2. **Reactive Scope** - The `reactiveScope` function creates a reactive context that automatically tracks which reactive values are accessed.

3. **Dependency Tracking** - When you call `counter()` inside the reactive scope, Reactive automatically registers it as a dependency. When the counter changes, the scope re-runs.

## Basic Example: Todo Counter

Here's a more practical example that counts incomplete todos:

```kotlin
data class Todo(val title: String, val completed: Boolean)

fun todoExample() {
    // Create a reactive list of todos
    val todos = ReactiveMutableList(
        listOf(
            Todo("Learn Reactive", false),
            Todo("Build an app", false),
            Todo("Profit", false)
        )
    )

    // Create a reactive computation that counts incomplete todos
    val incompleteCount = remember {
        todos().count { !it.completed }
    }

    // Display the count reactively
    AppScope.reactiveScope {
        println("Incomplete todos: ${incompleteCount()}")
    }

    // Mark a todo as complete
    todos[0] = Todo("Learn Reactive", true)
    // Prints: "Incomplete todos: 2"

    // Add a new todo
    todos.add(Todo("Write docs", false))
    // Prints: "Incomplete todos: 3"
}
```

### Key Concepts Introduced

- **ReactiveMutableList** - A list that notifies listeners when items are added, removed, or changed
- **remember** - Creates a reactive computation that caches its result and only recalculates when dependencies change
- **Lazy Evaluation** - The `remember` computation only runs when it has active listeners

## Understanding Reactive State

Reactive values have three possible states:

1. **Ready** - The value is available
2. **Loading** - The value is not yet ready (e.g., waiting for async operation)
3. **Error** - An exception occurred while computing the value

```kotlin
fun stateExample() {
    // LateInitSignal starts in loading state
    val asyncValue = LateInitSignal<String>()

    AppScope.reactiveScope {
        try {
            val value = asyncValue()  // Waits for value
            println("Got value: $value")
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }

    // Set the value later
    asyncValue.value = "Hello, Reactive!"
    // Prints: "Got value: Hello, Reactive!"
}
```

## Working with Async Operations

Reactive integrates seamlessly with Kotlin coroutines:

```kotlin
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay

fun asyncExample() {
    // Create a flow that emits values over time
    val timeFlow = flow {
        repeat(5) { i ->
            delay(1000)
            emit(i)
        }
    }

    // Use the flow in a reactive context
    AppScope.reactiveScope {
        val currentValue = timeFlow()
        println("Current value: $currentValue")
    }
    // Prints the current value each second as the flow emits
}
```

## Next Steps

Now that you understand the basics, continue learning:

- [Core Concepts](core-concepts.md) - Deep dive into reactive types and states
- [Reactive Context](reactive-context.md) - Learn about dependency tracking
- [Remember and Memoization](remember.md) - Master shared reactive computations
- [Collections](collections.md) - Work with reactive collections

## Common Patterns

### Derived State

```kotlin
val firstName = Signal("John")
val lastName = Signal("Doe")

val fullName = remember {
    "${firstName()} ${lastName()}"
}
```

### Conditional Dependencies

```kotlin
val showDetails = Signal(false)
val details = LateInitSignal<String>()

val display = remember {
    if (showDetails()) {
        details()  // Only depends on details when showDetails is true
    } else {
        "Hidden"
    }
}
```

### Side Effects

```kotlin
AppScope.reactiveScope {
    val value = mySignal()

    // Perform side effects
    println("Value changed to: $value")
    logToAnalytics(value)
    updateUI(value)
}
```

## Best Practices

1. **Use `remember` for expensive computations** - Share calculations across multiple listeners
2. **Keep reactive scopes small** - Break large scopes into smaller, focused ones
3. **Avoid side effects in `remember`** - Use `reactiveScope` for side effects
4. **Use appropriate signal types** - `Signal` for immediate values, `LateInitSignal` for async
5. **Clean up resources** - Reactive automatically handles cleanup when scopes are cancelled

## Troubleshooting

### My reactive scope doesn't update

Make sure you're calling the reactive value with `()`:

```kotlin
// ✗ Wrong - doesn't track dependency
reactiveScope {
    println(counter.value)
}

// ✓ Correct - tracks dependency
reactiveScope {
    println(counter())
}
```

### NotReadyException

This means you're trying to access a value that's still loading:

```kotlin
val late = LateInitSignal<String>()

reactiveScope {
    // This will wait until late has a value
    val value = late()
}

// Set the value
late.value = "Ready!"
```

### Memory leaks

Make sure your reactive scopes are tied to a `CoroutineScope` that gets cancelled:

```kotlin
class MyViewModel : CoroutineScope {
    private val job = Job()
    override val coroutineContext = job + Dispatchers.Main

    init {
        reactiveScope {
            // This will be cleaned up when job is cancelled
        }
    }

    fun dispose() {
        job.cancel()
    }
}
```
