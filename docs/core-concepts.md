# Core Concepts

Understanding the core concepts of Reactive will help you build robust reactive applications.

## Table of Contents

- [Reactive Values](#reactive-values)
- [Reactive State](#reactive-state)
- [Signal Types](#signal-types)
- [Listenable Interface](#listenable-interface)
- [Mutable vs Immutable](#mutable-vs-immutable)
- [Value vs State](#value-vs-state)

## Reactive Values

The foundation of Reactive is the `Reactive<T>` interface, which represents a value that can be observed for changes.

```kotlin
interface Reactive<out T> : Listenable {
    val state: ReactiveState<T>
}
```

Key characteristics:

- **Observable** - You can add listeners to be notified of changes
- **Stateful** - Holds a `ReactiveState` that includes ready, loading, and error states
- **Read-only** - The base interface doesn't allow modification

### Accessing Reactive Values

In a reactive context, use the `invoke()` operator to access values:

```kotlin
val signal = Signal(42)

reactiveScope {
    val value = signal()  // Accesses value and tracks dependency
    println("Value is: $value")
}
```

Outside a reactive context, use the `state` property:

```kotlin
val signal = Signal(42)
val state = signal.state  // ReactiveState<Int>

state.handle(
    success = { println("Value: $it") },
    exception = { println("Error: $it") },
    notReady = { println("Loading...") }
)
```

## Reactive State

`ReactiveState<T>` is a value class that wraps three possible states:

```kotlin
sealed class ReactiveState<out T> {
    // Value is ready
    data class Ready<T>(val value: T) : ReactiveState<T>()

    // Value is loading (not ready)
    object NotReady : ReactiveState<Nothing>()

    // An error occurred
    data class Exception<T>(val exception: kotlin.Exception) : ReactiveState<T>()
}
```

### Working with ReactiveState

```kotlin
val state: ReactiveState<String> = // ...

// Pattern matching
val result = state.handle(
    success = { value -> "Got: $value" },
    exception = { error -> "Error: ${error.message}" },
    notReady = { "Loading..." }
)

// Checking state
if (state.ready) {
    // Value is available
}

if (state.success) {
    // Value is available and not an error
}

// Mapping values
val upperState = state.map { it.uppercase() }

// Getting the value (unsafe - throws if not ready)
val value = state.get()  // Deprecated - use handle() instead

// Getting the value safely
val valueOrNull = state.getOrNull()
```

### Creating ReactiveState

```kotlin
// From a value
val readyState = ReactiveState(42)

// Not ready
val loadingState = ReactiveState.notReady

// From an exception
val errorState = ReactiveState.exception<Int>(IOException("Network error"))

// Wrapping a value (useful for null)
val wrappedState = ReactiveState.wrap(null)

// From a computation
val computed = reactiveState {
    expensiveComputation()
}
```

## Signal Types

Reactive provides several signal types for different use cases:

### Signal<T>

The most basic mutable reactive container. Always has a value.

```kotlin
val counter = Signal(0)

counter.value = 1  // Set synchronously
counter valueSet 2  // Infix notation

reactiveScope {
    println(counter())  // Access value
}
```

**Use when:**
- You have an immediate initial value
- The value is always available
- Synchronous updates

### LateInitSignal<T>

Like Signal, but starts in a loading state until the first value is set.

```kotlin
val asyncData = LateInitSignal<String>()

reactiveScope {
    // This scope waits until asyncData has a value
    println(asyncData())
}

// Later, set the value
asyncData.value = "Data loaded!"
```

**Use when:**
- The initial value comes from an async operation
- You want consumers to wait until data is ready
- Loading state is important

### RawReactive<T>

Low-level reactive value that exposes its state for direct mutation.

```kotlin
val raw = RawReactive<Int>()

// Set state directly
raw.state = ReactiveState(42)
raw.state = ReactiveState.notReady
raw.state = ReactiveState.exception(IOException())

reactiveScope {
    raw.state.handle(
        success = { println("Value: $it") },
        exception = { println("Error: $it") },
        notReady = { println("Loading...") }
    )
}
```

**Use when:**
- You need full control over state transitions
- Implementing custom reactive types
- Managing complex async state machines

### Constant<T>

A reactive value that never changes.

```kotlin
val constant = Constant(42)

reactiveScope {
    println(constant())  // Always 42
}
```

**Use when:**
- You have a value that will never change
- You need a `Reactive<T>` interface but the value is static
- Testing or mocking

## Listenable Interface

`Listenable` is the base interface for objects that can notify listeners:

```kotlin
interface Listenable {
    fun addListener(listener: () -> Unit): () -> Unit
}
```

### Adding Listeners

```kotlin
val signal = Signal(0)

// Add a listener
val remove = signal.addListener {
    println("Signal changed!")
}

// Change the signal
signal.value = 1  // Prints: "Signal changed!"

// Remove the listener
remove()

// This won't trigger the listener
signal.value = 2
```

### Listener Lifecycle

Listeners are automatically managed:

1. **Activation** - When the first listener is added, the reactive value activates
2. **Deactivation** - When the last listener is removed, it deactivates
3. **Cleanup** - Resources are cleaned up on deactivation

```kotlin
class MyReactive : BaseReactive<Int>() {
    override fun activate() {
        println("First listener added - start work")
    }

    override fun deactivate() {
        println("Last listener removed - clean up")
    }
}
```

## Mutable vs Immutable

### MutableReactive<T>

Allows setting values asynchronously:

```kotlin
interface MutableReactive<T> : Reactive<T> {
    suspend fun set(value: T)
}
```

### MutableReactiveValue<T>

Allows both synchronous and asynchronous setting:

```kotlin
interface MutableReactiveValue<T> : MutableReactive<T>, ReactiveValue<T> {
    var value: T
}
```

Example:

```kotlin
val mutableSignal = Signal(0)

// Synchronous set
mutableSignal.value = 1

// Asynchronous set
launch {
    mutableSignal.set(2)
}

// Infix notation
mutableSignal valueSet 3
```

## Value vs State

### ReactiveValue<T>

Guarantees the value is always available (no loading or error states):

```kotlin
interface ReactiveValue<out T> : Reactive<T> {
    val value: T
}
```

This is useful for values that are always immediately available:

```kotlin
val signal = Signal(42)
val value: Int = signal.value  // Direct access, no state handling needed
```

### Reactive<T>

May be in loading or error state, requires state handling:

```kotlin
val reactive: Reactive<String> = // ...

reactive.state.handle(
    success = { value -> /* Use value */ },
    exception = { error -> /* Handle error */ },
    notReady = { /* Show loading */ }
)
```

## Type Hierarchy

```
Listenable
    └── Reactive<T>
            ├── ReactiveValue<T>
            │       └── MutableReactiveValue<T>
            └── MutableReactive<T>
                    └── MutableReactiveValue<T>
```

## Practical Examples

### Read-only Derived State

```kotlin
val firstName = Signal("John")
val lastName = Signal("Doe")

val fullName: Reactive<String> = remember {
    "${firstName()} ${lastName()}"
}

// fullName is read-only, but updates when firstName or lastName change
```

### Mutable Derived State with Lensing

```kotlin
data class User(val name: String, val email: String)

val user = Signal(User("John", "john@example.com"))

// Create a mutable lens focused on the name
val userName: MutableReactiveValue<String> = user.lens(
    get = { it.name },
    modify = { user, newName -> user.copy(name = newName) }
)

userName.value = "Jane"  // Updates the user signal
```

### Handling Async State

```kotlin
val apiCall = LateInitSignal<List<Item>>()

reactiveScope {
    apiCall.state.handle(
        success = { items ->
            displayItems(items)
        },
        exception = { error ->
            showError(error.message)
        },
        notReady = {
            showLoading()
        }
    )
}

// Later, from a coroutine
launch {
    try {
        val items = fetchItemsFromApi()
        apiCall.value = items
    } catch (e: Exception) {
        // Set error state - need to use raw reactive for this
    }
}
```

### Custom Reactive Type

```kotlin
class TimerReactive(private val intervalMs: Long) : BaseReactive<Long>() {
    private var job: Job? = null
    private var count = 0L

    override fun activate() {
        job = GlobalScope.launch {
            while (isActive) {
                state = ReactiveState(count++)
                delay(intervalMs)
            }
        }
    }

    override fun deactivate() {
        job?.cancel()
        count = 0
    }
}

// Usage
val timer = TimerReactive(1000)

reactiveScope {
    println("Tick: ${timer()}")
}
// Prints every second while the scope is active
```

## Best Practices

1. **Use Signal for simple values** - It's the most straightforward reactive container
2. **Use LateInitSignal for async data** - Makes loading state explicit
3. **Prefer ReactiveValue when possible** - Simpler API, no state handling needed
4. **Use RawReactive sparingly** - Only when you need low-level control
5. **Don't mix .value and ()** - Use `()` in reactive contexts, `.value` elsewhere
6. **Handle all states** - Use `handle()` to ensure you cover ready, loading, and error cases

## Next Steps

- [Reactive Context](reactive-context.md) - Learn about dependency tracking
- [Remember and Memoization](remember.md) - Share reactive computations
- [Collections](collections.md) - Work with reactive collections
