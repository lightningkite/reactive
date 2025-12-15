# Reactive Context

Reactive contexts are the core mechanism for automatic dependency tracking and reactive computation in the Reactive library.

## Table of Contents

- [What is a Reactive Context?](#what-is-a-reactive-context)
- [Creating Reactive Contexts](#creating-reactive-contexts)
- [Dependency Tracking](#dependency-tracking)
- [Context Operators](#context-operators)
- [Lifecycle Management](#lifecycle-management)
- [Suspending Contexts](#suspending-contexts)
- [Advanced Usage](#advanced-usage)

## What is a Reactive Context?

A `ReactiveContext` is an environment that:

1. **Tracks dependencies** - Automatically records which reactive values are accessed
2. **Reruns on changes** - When any dependency changes, the context re-executes
3. **Manages resources** - Handles listener registration/cleanup automatically
4. **Reports state** - Can be treated as a reactive value itself

```kotlin
reactiveScope {
    // This is a reactive context
    // Any reactive values accessed here are automatically tracked
    val a = signalA()
    val b = signalB()
    println(a + b)
}
// When signalA or signalB changes, this block re-runs
```

## Creating Reactive Contexts

### reactiveScope

Creates a reactive scope for side effects:

```kotlin
val counter = Signal(0)

AppScope.reactiveScope {
    println("Counter: ${counter()}")
    updateUI(counter())
}

counter.value = 1  // Triggers the scope to re-run
```

**Use for:**
- UI updates
- Logging
- Side effects that should run when dependencies change

### reactiveSuspending

Creates a suspending reactive scope:

```kotlin
import com.lightningkite.reactive.context.reactiveSuspending

AppScope.reactiveSuspending {
    val data = asyncSignal()
    saveToDatabase(data)
}
```

**Use for:**
- Async side effects
- Operations requiring suspend functions

### Custom Reactive Contexts

For more control, create a `TypedReactiveContext`:

```kotlin
val context = TypedReactiveContext(AppScope) {
    signalA() + signalB()
}

context.startCalculation()

// Access the result
context.state.handle(
    success = { println("Result: $it") },
    exception = { println("Error: $it") },
    notReady = { println("Loading...") }
)
```

## Dependency Tracking

### How It Works

When you call a reactive value inside a context using `()`, it's automatically registered as a dependency:

```kotlin
val a = Signal(1)
val b = Signal(2)
val c = Signal(3)

reactiveScope {
    val sum = a() + b()  // a and b are dependencies
    println("Sum: $sum")  // c is NOT a dependency
}

a.value = 10  // Triggers re-run
b.value = 20  // Triggers re-run
c.value = 30  // Does NOT trigger re-run
```

### Conditional Dependencies

Dependencies can change based on runtime conditions:

```kotlin
val showDetails = Signal(false)
val details = LateInitSignal<String>()

reactiveScope {
    if (showDetails()) {
        // details is only a dependency when showDetails is true
        println(details())
    } else {
        println("Details hidden")
    }
}
```

### Manual Dependency Registration

Use `rerunOn` to manually register dependencies:

```kotlin
reactiveScope {
    rerunOn(myListenable)  // Re-run when myListenable fires

    // Do work...
}
```

## Context Operators

### invoke()

Access a reactive value and track it as a dependency:

```kotlin
reactiveScope {
    val value = mySignal()  // Tracks dependency
}
```

### once()

Access a value once without keeping it as an ongoing dependency:

```kotlin
reactiveScope {
    val initialValue = mySignal.once()  // Gets value but won't track future changes

    // Use initialValue...
}
```

### state()

Access the ReactiveState without unwrapping:

```kotlin
reactiveScope {
    val state = mySignal.state()  // Tracks dependency, returns ReactiveState

    state.handle(
        success = { /* ... */ },
        exception = { /* ... */ },
        notReady = { /* ... */ }
    )
}
```

### awaitNotNull()

Wait for a nullable value to become non-null:

```kotlin
val nullableSignal = Signal<String?>(null)

reactiveScope {
    val value = nullableSignal.awaitNotNull()  // Waits until non-null
    println("Got: $value")
}

nullableSignal.value = "Hello"  // Scope runs now
```

## Lifecycle Management

### Activation and Deactivation

Reactive contexts activate when they start and deactivate when they're cancelled:

```kotlin
val scope = TypedReactiveContext(AppScope) {
    println("Calculating...")
    expensiveOperation()
}

scope.startCalculation()  // Activates
scope.cancel()  // Deactivates and cleans up listeners
```

### Resource Cleanup

Use `onRemove` to register cleanup callbacks:

```kotlin
reactiveScope {
    val resource = acquireResource()

    onRemove {
        resource.release()
    }

    // Use resource...
}
```

### Scope Lifetime

Reactive scopes are tied to a `CoroutineScope`:

```kotlin
class MyViewModel {
    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    init {
        scope.reactiveScope {
            // This will be cleaned up when scope is cancelled
        }
    }

    fun onCleared() {
        scope.cancel()  // Cancels all reactive scopes
    }
}
```

## Suspending Contexts

### ReactiveContextSuspending

For computations that need suspend functions:

```kotlin
val result = ReactiveContextSuspending(AppScope) {
    val data = fetchDataFromApi()  // suspend function
    processData(data)
}
```

### async Operator

Execute async operations within a reactive context:

```kotlin
reactiveScope {
    val result = async {
        delay(1000)
        fetchData()
    }

    println("Result: $result")
}
```

### Deferred.invoke()

Use Deferred values in reactive contexts:

```kotlin
val deferred: Deferred<String> = GlobalScope.async {
    delay(1000)
    "Complete"
}

reactiveScope {
    val value = deferred()  // Waits for completion
    println(value)
}
```

### Flow.invoke()

Use Kotlin Flow in reactive contexts:

```kotlin
val flow = flow {
    repeat(5) { i ->
        delay(1000)
        emit(i)
    }
}

reactiveScope {
    val current = flow()  // Gets current emitted value
    println("Current: $current")
}
```

**Note:** For `StateFlow`, the current value is returned immediately. For other flows, it throws `ReactiveLoading` until the first value is emitted.

## Advanced Usage

### useLastWhileLoading

Keep the previous value while recalculating:

```kotlin
val context = TypedReactiveContext(
    scope = AppScope,
    useLastWhileLoading = true
) {
    expensiveCalculation()
}
```

This prevents flickering to a loading state during recalculation.

### Nested Contexts

Reactive contexts can be nested:

```kotlin
val outer = remember {
    val a = signalA()

    val inner = remember {
        signalB() * 2
    }

    a + inner()
}
```

### Thread Safety

Reactive contexts use `onThread` to ensure calculations run on the correct dispatcher:

```kotlin
val context = TypedReactiveContext(
    scope = CoroutineScope(Dispatchers.Main)
) {
    // This runs on Main dispatcher
    updateUI()
}
```

### Dependency Blocks

Internally, contexts use dependency blocks to track which dependencies are still in use:

```kotlin
fun startCalculation() {
    dependencyBlockStart()  // Clear used dependencies list

    // Execute calculation

    dependencyBlockEnd()  // Remove unused dependencies
}
```

This ensures that dependencies that are no longer accessed are properly removed.

### Custom Context Behavior

Extend `DependencyTracker` for custom dependency management:

```kotlin
class MyContext : DependencyTracker() {
    fun calculate() {
        dependencyBlockStart()

        // Register dependencies
        val dep1 = existingDependency(signal1) ?: signal1.also {
            registerDependency(it, signal1.addListener(::recalculate))
        }

        // Use dependencies...

        dependencyBlockEnd()
    }

    fun recalculate() {
        calculate()
    }
}
```

## Practical Examples

### Form Validation

```kotlin
val email = Signal("")
val password = Signal("")

val isValid = remember {
    email().contains("@") && password().length >= 8
}

reactiveScope {
    val valid = isValid()
    submitButton.enabled = valid
}
```

### Derived Data

```kotlin
val items = ReactiveMutableList<Item>()

val totalPrice = remember {
    items().sumOf { it.price }
}

val itemCount = remember {
    items().size
}

reactiveScope {
    println("${itemCount()} items, total: $${totalPrice()}")
}
```

### Conditional Async Loading

```kotlin
val userId = Signal<String?>(null)
val userDetails = LateInitSignal<User>()

reactiveScope {
    val id = userId.awaitNotNull()

    // Load user details
    launch {
        userDetails.value = fetchUser(id)
    }
}

reactiveScope {
    userDetails.state().handle(
        success = { user -> displayUser(user) },
        exception = { error -> showError(error) },
        notReady = { showLoading() }
    )
}
```

### Debounced Search

```kotlin
val searchQuery = Signal("")
val searchResults = LateInitSignal<List<Result>>()

val debouncedQuery = searchQuery.debounce(300.milliseconds)

reactiveScope {
    val query = debouncedQuery()
    if (query.isNotEmpty()) {
        launch {
            searchResults.value = search(query)
        }
    }
}
```

## Best Practices

1. **Keep contexts focused** - Each context should have a single responsibility
2. **Avoid side effects in remember** - Use `reactiveScope` for side effects
3. **Use appropriate operators** - `once()` for initial values, `()` for tracked dependencies
4. **Clean up resources** - Use `onRemove` for cleanup
5. **Handle loading states** - Don't assume values are always ready
6. **Tie scopes to lifecycle** - Cancel scopes when they're no longer needed
7. **Be careful with expensive operations** - Contexts re-run on every dependency change
8. **Use useLastWhileLoading judiciously** - Prevents loading flicker but may show stale data

## Common Pitfalls

### Forgetting to call ()

```kotlin
// ✗ Wrong - doesn't track dependency
reactiveScope {
    println(signal.value)
}

// ✓ Correct - tracks dependency
reactiveScope {
    println(signal())
}
```

### Infinite Loops

```kotlin
// ✗ Wrong - creates infinite loop
reactiveScope {
    val value = signal()
    signal.value = value + 1  // Triggers scope again
}

// ✓ Correct - only update when needed
reactiveScope {
    val value = signal()
    if (shouldUpdate(value)) {
        launch {
            signal.value = newValue
        }
    }
}
```

### Not handling loading states

```kotlin
// ✗ Wrong - may throw NotReadyException
reactiveScope {
    val value = lateInitSignal()
    println(value)
}

// ✓ Correct - handle loading state
reactiveScope {
    lateInitSignal.state().handle(
        success = { println(it) },
        exception = { println("Error: $it") },
        notReady = { println("Loading...") }
    )
}
```

## Next Steps

- [Remember and Memoization](remember.md) - Learn about shared reactive computations
- [Lensing](lensing.md) - Transform and focus reactive data
- [Advanced Topics](advanced-topics.md) - Performance optimization and patterns
