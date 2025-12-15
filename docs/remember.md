# Remember and Memoization

The `remember` and `rememberSuspending` functions create reactive computations that cache their results and share them across multiple listeners.

## Table of Contents

- [What is Remember?](#what-is-remember)
- [Basic Usage](#basic-usage)
- [Remember vs ReactiveScope](#remember-vs-reactivescope)
- [Suspending Remember](#suspending-remember)
- [Mutable Remember](#mutable-remember)
- [Deactivation Delay](#deactivation-delay)
- [Advanced Patterns](#advanced-patterns)

## What is Remember?

`remember` creates a reactive value that:

1. **Caches results** - Calculates once and shares the result among listeners
2. **Tracks dependencies** - Automatically recalculates when dependencies change
3. **Is lazy** - Only calculates when it has active listeners
4. **Shares calculations** - Multiple listeners get the same computation

```kotlin
val expensive = remember {
    performExpensiveCalculation()
}

// Multiple listeners share the same calculation
reactiveScope { println("Listener 1: ${expensive()}") }
reactiveScope { println("Listener 2: ${expensive()}") }
reactiveScope { println("Listener 3: ${expensive()}") }
// Only calculates once!
```

## Basic Usage

### Creating a Remember

```kotlin
val firstName = Signal("John")
val lastName = Signal("Doe")

val fullName = remember {
    "${firstName()} ${lastName()}"
}

reactiveScope {
    println("Full name: ${fullName()}")
}

firstName.value = "Jane"  // fullName recalculates and scope re-runs
```

### Remember is Lazy

Remember doesn't calculate until it has listeners:

```kotlin
var calculationCount = 0

val expensive = remember {
    calculationCount++
    expensiveOperation()
}

println(calculationCount)  // 0 - hasn't calculated yet

// Add a listener
val remover = expensive.addListener { }
println(calculationCount)  // 1 - now it calculated

// Remove the listener
remover()

// Add another listener
expensive.addListener { }
println(calculationCount)  // 2 - recalculated
```

### Shared Calculations

Multiple listeners share the same calculation:

```kotlin
var hits = 0
val signal = Signal(1)

val shared = remember {
    hits++
    signal() * 2
}

reactiveScope { println("A: ${shared()}") }
reactiveScope { println("B: ${shared()}") }
reactiveScope { println("C: ${shared()}") }

println("Calculation hits: $hits")  // 1 - only calculated once

signal.value = 2
println("Calculation hits: $hits")  // 2 - recalculated once for all listeners
```

## Remember vs ReactiveScope

### Use `remember` for:
- **Derived state** - Values calculated from other reactive values
- **Expensive computations** - Operations you want to share across listeners
- **Pure functions** - Calculations without side effects

```kotlin
val items = ReactiveMutableList<Item>()

val totalPrice = remember {
    items().sumOf { it.price }
}
```

### Use `reactiveScope` for:
- **Side effects** - UI updates, logging, API calls
- **One-off reactions** - Actions that should happen when values change
- **Non-shared work** - Each scope does its own work

```kotlin
reactiveScope {
    val price = totalPrice()
    updatePriceDisplay(price)  // Side effect
    logPriceChange(price)      // Side effect
}
```

### Key Differences

| Feature | remember | reactiveScope |
|---------|----------|---------------|
| Returns a value | ✓ Reactive<T> | ✗ Unit |
| Shared across listeners | ✓ Yes | ✗ No |
| Lazy evaluation | ✓ Yes | ✗ No |
| Side effects | ✗ Avoid | ✓ Intended use |
| Result caching | ✓ Yes | ✗ No |

## Suspending Remember

For computations that require suspend functions, use `rememberSuspending`:

```kotlin
val userId = Signal("user123")

val userProfile = rememberSuspending {
    val id = userId()
    fetchUserProfile(id)  // suspend function
}

reactiveSuspending {
    userProfile.state().handle(
        success = { profile -> displayProfile(profile) },
        exception = { error -> showError(error) },
        notReady = { showLoading() }
    )
}
```

### Suspending vs Non-Suspending

```kotlin
// Non-suspending - for synchronous calculations
val sum = remember {
    a() + b()
}

// Suspending - for async calculations
val data = rememberSuspending {
    val id = userId()
    fetchDataFromApi(id)  // suspend function
}
```

### Parallel Async Operations

```kotlin
val result = rememberSuspending {
    val a = async { fetchA() }
    val b = async { fetchB() }
    val c = async { fetchC() }

    CombinedResult(a.await(), b.await(), c.await())
}
```

## Mutable Remember

For stateful computations that can also be modified, use `mutableRemember`:

```kotlin
val baseValue = Signal(0)

val adjustedValue = mutableRemember(
    initialValue = { baseValue() + 10 }
)

// Can read reactively
reactiveScope {
    println(adjustedValue())
}

// Can also set
adjustedValue.value = 50

// Reset to calculated value
baseValue.value = 20  // adjustedValue recalculates to 30
```

### Mutable Remember Suspending

```kotlin
val userId = Signal("user123")

val userCache = mutableRememberSuspending(
    initialValue = { fetchUser(userId()) }
)

// Read
reactiveSuspending {
    val user = userCache()
    displayUser(user)
}

// Write
launch {
    val updatedUser = updateUser(newData)
    userCache.value = updatedUser
}
```

## Deactivation Delay

Keep calculations alive for a short time after listeners are removed to avoid redundant recalculations:

```kotlin
val data = Remember(
    deactivationDelay = 100.milliseconds
) {
    expensiveCalculation()
}

val remover1 = data.addListener { }
remover1()  // Starts deactivation timer

// Within 100ms, add another listener
val remover2 = data.addListener { }
// Reuses existing calculation instead of recalculating!

remover2()
// After 100ms, the calculation shuts down
```

### Use Cases

1. **UI flickering** - Prevent recalculation when switching between screens quickly
2. **Tab switching** - Share state when rapidly switching tabs
3. **Dropdown menus** - Keep data ready when menu closes and reopens

```kotlin
val searchResults = Remember(
    deactivationDelay = 500.milliseconds
) {
    performSearch(query())
}

// User opens search dropdown - calculates results
// User closes dropdown - starts timer
// User reopens within 500ms - reuses cached results
```

## Advanced Patterns

### Cascading Remember

```kotlin
val baseData = Signal(listOf(1, 2, 3, 4, 5))

val filtered = remember {
    baseData().filter { it > 2 }
}

val doubled = remember {
    filtered().map { it * 2 }
}

val sum = remember {
    doubled().sum()
}

reactiveScope {
    println("Sum: ${sum()}")  // 24
}

baseData.value = listOf(1, 2, 3, 4, 5, 6)
// Only recalculates what changed
```

### Conditional Remember

```kotlin
val useAdvanced = Signal(false)
val simpleData = Signal(listOf(1, 2, 3))
val advancedData = LateInitSignal<List<Int>>()

val data = remember {
    if (useAdvanced()) {
        advancedData()
    } else {
        simpleData()
    }
}

// Dependencies change based on condition
useAdvanced.value = true  // Now depends on advancedData
```

### Remember with Multiple Dependencies

```kotlin
val a = Signal(1)
val b = Signal(2)
val c = Signal(3)
val operator = Signal<(Int, Int, Int) -> Int> { x, y, z -> x + y + z }

val result = remember {
    val op = operator()
    op(a(), b(), c())
}
```

### Caching Expensive Transformations

```kotlin
data class User(val id: String, val name: String, val email: String)

val users = ReactiveMutableList<User>()

val userIndex = remember {
    users().associateBy { it.id }  // Expensive for large lists
}

// Fast lookup
reactiveScope {
    val user = userIndex()["user123"]
}
```

### State Machine

```kotlin
sealed class State {
    object Idle : State()
    data class Loading(val progress: Int) : State()
    data class Success(val data: String) : State()
    data class Error(val message: String) : State()
}

val currentState = Signal<State>(State.Idle)

val statusMessage = remember {
    when (val state = currentState()) {
        is State.Idle -> "Ready"
        is State.Loading -> "Loading... ${state.progress}%"
        is State.Success -> "Success: ${state.data}"
        is State.Error -> "Error: ${state.message}"
    }
}
```

## Performance Considerations

### When to Use Remember

✅ **Good use cases:**
```kotlin
// Expensive calculation
val result = remember {
    complexAlgorithm(data())
}

// Multiple dependencies
val fullAddress = remember {
    "${street()} ${city()} ${state()} ${zip()}"
}

// Derived collections
val activeItems = remember {
    items().filter { it.active }
}
```

❌ **Avoid remember for:**
```kotlin
// Simple property access - no benefit
val bad1 = remember { signal() }

// Side effects - use reactiveScope instead
val bad2 = remember {
    println("This is a side effect")
    signal()
}

// One-time-use - overhead for no benefit
val bad3 = remember { UUID.randomUUID().toString() }
```

### Optimizing Remember

1. **Break down complex calculations:**
```kotlin
// Instead of one big remember
val bad = remember {
    expensivePart1(data()) + expensivePart2(data())
}

// Use cascading remember
val part1 = remember { expensivePart1(data()) }
val part2 = remember { expensivePart2(data()) }
val good = remember { part1() + part2() }
```

2. **Use deactivation delay for frequently toggled UI:**
```kotlin
val viewData = Remember(
    deactivationDelay = 200.milliseconds
) {
    prepareViewData()
}
```

3. **Minimize dependencies:**
```kotlin
val settings = Signal(AppSettings(...))

// Bad - depends on entire settings object
val bad = remember {
    settings().theme  // Recalculates when ANY setting changes
}

// Good - use a lens to depend only on theme
val theme = settings.lens({ it.theme }, { settings, theme -> settings.copy(theme = theme) })
val good = remember {
    theme()  // Only recalculates when theme changes
}
```

## Testing Remember

```kotlin
@Test
fun testRemember() {
    val signal = Signal(10)
    var calculationCount = 0

    val doubled = remember {
        calculationCount++
        signal() * 2
    }

    // Not calculated yet (lazy)
    assertEquals(0, calculationCount)

    // Add listener - triggers calculation
    val remover = doubled.addListener { }
    assertEquals(1, calculationCount)
    assertEquals(ReactiveState(20), doubled.state)

    // Change dependency
    signal.value = 15
    assertEquals(2, calculationCount)
    assertEquals(ReactiveState(30), doubled.state)

    // Remove listener - stops calculating
    remover()
    signal.value = 20
    assertEquals(2, calculationCount)  // Didn't recalculate
}
```

## Best Practices

1. **Keep remember pure** - No side effects, always return the same output for the same inputs
2. **Use remember for expensive operations** - If it's cheap, direct access is fine
3. **Cascade complex computations** - Break into smaller remember blocks
4. **Name your remember values** - Makes code more readable
5. **Consider deactivation delay** - For frequently toggled UI elements
6. **Test laziness** - Ensure calculations don't run unnecessarily
7. **Avoid remember for simple access** - `val x = remember { signal() }` is pointless

## Common Patterns

### Debounced Remember

```kotlin
fun <T> Reactive<T>.debounced(delay: Duration): Reactive<T> = remember {
    val debounced = LateInitSignal<T>()
    var job: Job? = null

    reactiveScope {
        val value = this@debounced()
        job?.cancel()
        job = launch {
            delay(delay)
            debounced.value = value
        }
    }

    debounced()
}
```

### Cached API Call

```kotlin
val apiCache = remember {
    val cacheKey = buildCacheKey()
    getFromCacheOrFetch(cacheKey)
}
```

### Computed Property

```kotlin
data class Rectangle(val width: Int, val height: Int)

val rect = Signal(Rectangle(10, 20))

val area = remember {
    val r = rect()
    r.width * r.height
}
```

## Next Steps

- [Lensing](lensing.md) - Transform and focus on reactive data
- [Collections](collections.md) - Work with reactive collections
- [Advanced Topics](advanced-topics.md) - Performance optimization and patterns
