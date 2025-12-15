# Advanced Topics

This guide covers advanced patterns, performance optimization, and best practices for the Reactive library.

## Table of Contents

- [Performance Optimization](#performance-optimization)
- [Common Patterns](#common-patterns)
- [Debugging](#debugging)
- [Troubleshooting](#troubleshooting)
- [Best Practices](#best-practices)
- [Migration Strategies](#migration-strategies)

## Performance Optimization

### Minimize Reactive Scopes

```kotlin
// ✗ Bad - one big scope
reactiveScope {
    val a = signalA()
    val b = signalB()
    val c = signalC()
    updateA(a)
    updateB(b)
    updateC(c)
}

// ✓ Good - separate scopes for independent updates
reactiveScope {
    updateA(signalA())
}
reactiveScope {
    updateB(signalB())
}
reactiveScope {
    updateC(signalC())
}
```

### Use remember for Expensive Calculations

```kotlin
val data = Signal(largeDataset)

// ✗ Bad - recalculates for every scope
reactiveScope {
    val processed = data().map { expensiveTransform(it) }
    displayA(processed)
}
reactiveScope {
    val processed = data().map { expensiveTransform(it) }
    displayB(processed)
}

// ✓ Good - calculate once, share result
val processed = remember {
    data().map { expensiveTransform(it) }
}
reactiveScope {
    displayA(processed())
}
reactiveScope {
    displayB(processed())
}
```

### Deactivation Delay for UI

```kotlin
// Prevent recalculation when rapidly switching screens
val screenData = Remember(
    deactivationDelay = 300.milliseconds
) {
    loadScreenData()
}
```

### Batch Collection Updates

```kotlin
val list = ReactiveMutableList<Int>()

// ✗ Bad - 1000 notifications
repeat(1000) { list.add(it) }

// ✓ Good - 1 notification
list.addAll((0 until 1000).toList())
```

### Lazy Lenses

```kotlin
// Lenses only calculate when they have listeners
val userEmail = user.lens({ it.email }, { u, e -> u.copy(email = e) })

// Not calculated yet
// ...

// Now it activates
reactiveScope {
    println(userEmail())
}
```

### useLastWhileLoading

```kotlin
// Prevent flickering during recalculation
val data = Remember(
    useLastWhileLoading = true
) {
    fetchData()
}

// Shows old value while new value loads
```

## Common Patterns

### Debouncing

```kotlin
fun <T> Reactive<T>.debounce(delay: Duration): Reactive<T> = remember {
    val output = LateInitSignal<T>()
    var job: Job? = null

    reactiveScope {
        val value = this@debounce()
        job?.cancel()
        job = launch {
            delay(delay)
            output.value = value
        }
    }

    output()
}

// Usage
val searchQuery = Signal("")
val debouncedQuery = searchQuery.debounce(300.milliseconds)
```

### Throttling

```kotlin
fun <T> Reactive<T>.throttle(period: Duration): Reactive<T> = remember {
    val output = LateInitSignal<T>()
    var lastEmit = 0L

    reactiveScope {
        val value = this@throttle()
        val now = System.currentTimeMillis()
        if (now - lastEmit >= period.inWholeMilliseconds) {
            output.value = value
            lastEmit = now
        }
    }

    output()
}
```

### Computed Properties

```kotlin
data class Rectangle(val width: Double, val height: Double)

val rect = Signal(Rectangle(10.0, 20.0))

val area = remember { rect().width * rect().height }
val perimeter = remember { 2 * (rect().width + rect().height) }
val diagonal = remember {
    val r = rect()
    sqrt(r.width * r.width + r.height * r.height)
}
```

### State Machines

```kotlin
sealed class State {
    object Idle : State()
    object Loading : State()
    data class Success(val data: String) : State()
    data class Error(val message: String) : State()
}

class StateMachine {
    val state = Signal<State>(State.Idle)

    val isLoading = remember { state() is State.Loading }
    val isSuccess = remember { state() is State.Success }
    val isError = remember { state() is State.Error }

    val data = remember {
        (state() as? State.Success)?.data
    }

    suspend fun load() {
        state.value = State.Loading
        try {
            val result = fetchData()
            state.value = State.Success(result)
        } catch (e: Exception) {
            state.value = State.Error(e.message ?: "Unknown error")
        }
    }
}
```

### Caching

```kotlin
class CachedLoader<K, V>(
    private val loader: suspend (K) -> V
) {
    private val cache = ReactiveMutableMap<K, V>()

    fun get(key: K): Reactive<V> = remember {
        val cached = cache().get(key)
        if (cached != null) {
            cached
        } else {
            val loaded = rememberSuspending {
                loader(key).also { cache[key] = it }
            }
            loaded()
        }
    }

    fun invalidate(key: K) {
        cache.remove(key)
    }

    fun invalidateAll() {
        cache.clear()
    }
}
```

### Undo/Redo

```kotlin
class UndoableSignal<T>(initial: T) {
    private val history = mutableListOf<T>()
    private val future = mutableListOf<T>()
    private val current = Signal(initial)

    val value = current.lens(
        get = { it },
        modify = { old, new ->
            if (old != new) {
                history.add(old)
                future.clear()
            }
            new
        }
    )

    val canUndo = remember { history.isNotEmpty() }
    val canRedo = remember { future.isNotEmpty() }

    fun undo() {
        if (history.isNotEmpty()) {
            val previous = history.removeLast()
            future.add(current.value)
            current.value = previous
        }
    }

    fun redo() {
        if (future.isNotEmpty()) {
            val next = future.removeLast()
            history.add(current.value)
            current.value = next
        }
    }
}
```

### Pagination

```kotlin
class PaginatedList<T>(
    private val pageSize: Int,
    private val loader: suspend (page: Int) -> List<T>
) {
    val currentPage = Signal(0)
    val items = ReactiveMutableList<T>()

    val hasMore = Signal(true)
    val isLoading = Signal(false)

    suspend fun loadNext() {
        if (!hasMore.value || isLoading.value) return

        isLoading.value = true
        try {
            val newItems = loader(currentPage.value)
            items.addAll(newItems)
            currentPage.value++
            hasMore.value = newItems.size == pageSize
        } finally {
            isLoading.value = false
        }
    }

    fun reset() {
        items.clear()
        currentPage.value = 0
        hasMore.value = true
    }
}
```

## Debugging

### Logging Reactive Changes

```kotlin
fun <T> Reactive<T>.logged(tag: String): Reactive<T> = remember {
    reactiveScope {
        val value = this@logged()
        println("[$tag] Changed to: $value")
        value
    }
    this@logged()
}

// Usage
val signal = Signal(0).logged("MySignal")
```

### Counting Calculations

```kotlin
var calculationCount = 0

val result = remember {
    calculationCount++
    println("Calculation #$calculationCount")
    expensiveOperation()
}
```

### Tracking Dependencies

```kotlin
class DebugContext<T>(
    scope: CalculationContext,
    action: ReactiveContext.() -> T
) : TypedReactiveContext<T>(scope, action = action) {
    override fun <R> Reactive<R>.invoke(): R {
        println("Dependency accessed: $this")
        return super.invoke()
    }
}
```

### State Inspection

```kotlin
reactiveScope {
    mySignal.state().handle(
        success = { println("Ready: $it") },
        exception = { println("Error: $it") },
        notReady = { println("Loading...") }
    )
}
```

## Troubleshooting

### My scope doesn't update

**Problem:** Reactive scope not re-running when signal changes.

**Solution:** Make sure you're calling the signal with `()`:

```kotlin
// ✗ Wrong
reactiveScope {
    println(signal.value)  // Doesn't track dependency
}

// ✓ Correct
reactiveScope {
    println(signal())  // Tracks dependency
}
```

### NotReadyException

**Problem:** Getting `NotReadyException` when accessing a value.

**Solution:** The reactive value is in loading state. Handle it:

```kotlin
// ✗ Wrong
reactiveScope {
    val value = lateInitSignal()  // Throws if not ready
}

// ✓ Correct
reactiveScope {
    lateInitSignal.state().handle(
        success = { value -> /* Use value */ },
        exception = { error -> /* Handle error */ },
        notReady = { /* Show loading */ }
    )
}
```

### Infinite loop

**Problem:** Reactive scope causes infinite recalculation.

**Solution:** Don't modify dependencies inside the scope:

```kotlin
// ✗ Wrong - infinite loop
reactiveScope {
    val value = counter()
    counter.value = value + 1  // Triggers scope again!
}

// ✓ Correct - only update when needed
reactiveScope {
    val value = counter()
    if (shouldUpdate(value)) {
        launch {  // Async update breaks the loop
            counter.value = newValue
        }
    }
}
```

### Memory leaks

**Problem:** Reactive scopes not being cleaned up.

**Solution:** Tie scopes to a `CoroutineScope` and cancel it:

```kotlin
class MyViewModel {
    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    init {
        scope.reactiveScope {
            // Will be cleaned up when scope is cancelled
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
```

### Remember not recalculating

**Problem:** `remember` not updating when it should.

**Solution:** Make sure it has listeners:

```kotlin
val result = remember {
    expensiveOperation()
}

// Must have a listener to activate
reactiveScope {
    println(result())
}
```

### Values not sharing

**Problem:** Multiple scopes not sharing a `remember` result.

**Solution:** Create the `remember` outside the scopes:

```kotlin
// ✗ Wrong - each scope creates its own remember
reactiveScope {
    val value = remember { expensive() }  // New remember
}
reactiveScope {
    val value = remember { expensive() }  // Another new remember
}

// ✓ Correct - share the remember
val shared = remember { expensive() }

reactiveScope {
    val value = shared()
}
reactiveScope {
    val value = shared()
}
```

## Best Practices

### Separation of Concerns

```kotlin
// ✓ Good - clear separation
val data = remember { processData(raw()) }  // Computation
reactiveScope { updateUI(data()) }          // Side effect
```

### Immutability

```kotlin
// ✓ Good - use immutable data structures
data class User(val name: String, val email: String)

val user = Signal(User("John", "john@example.com"))

// Modify by copying
user.value = user.value.copy(email = "new@example.com")
```

### Explicit Types

```kotlin
// ✓ Good - explicit types for clarity
val users: Reactive<List<User>> = remember {
    fetchUsers()
}
```

### Error Handling

```kotlin
// ✓ Good - handle all states
reactiveScope {
    data.state().handle(
        success = { value -> displayData(value) },
        exception = { error -> showError(error.message) },
        notReady = { showLoading() }
    )
}
```

### Testing

```kotlin
// ✓ Good - test reactive behavior
@Test
fun testReactivity() {
    val signal = Signal(0)
    var updates = 0

    val scope = TestScope()
    scope.reactiveScope {
        signal()
        updates++
    }

    assertEquals(1, updates)

    signal.value = 1
    assertEquals(2, updates)

    scope.cancel()
}
```

## Migration Strategies

### From Direct Properties

```kotlin
// Before
class ViewModel {
    var count = 0
        set(value) {
            field = value
            notifyListeners()
        }
}

// After
class ViewModel {
    val count = Signal(0)
}
```

### From LiveData (Android)

```kotlin
// Before
class ViewModel : ViewModel() {
    private val _data = MutableLiveData<String>()
    val data: LiveData<String> = _data

    fun load() {
        _data.value = "loaded"
    }
}

// After
class ViewModel {
    val data = LateInitSignal<String>()

    suspend fun load() {
        data.value = "loaded"
    }
}
```

### From Flow

```kotlin
// Before
val flow: Flow<Int> = flowOf(1, 2, 3, 4, 5)

// After - use Flow directly in reactive contexts
reactiveScope {
    val current = flow()
    println(current)
}
```

### Gradual Migration

1. **Start with new features** - Use Reactive for new code
2. **Wrap existing code** - Create Reactive wrappers for old APIs
3. **Migrate incrementally** - Convert one module at a time
4. **Test thoroughly** - Ensure behavior matches

```kotlin
// Wrapper for legacy code
fun legacyDataAsReactive(): Reactive<Data> {
    val signal = LateInitSignal<Data>()

    legacyAPI.addListener { data ->
        signal.value = data
    }

    return signal
}
```

## Performance Monitoring

### Measure Calculation Time

```kotlin
fun <T> remember(
    name: String,
    action: ReactiveContext.() -> T
): Reactive<T> {
    return remember {
        val start = System.currentTimeMillis()
        val result = action()
        val duration = System.currentTimeMillis() - start
        if (duration > 16) {  // Longer than one frame
            println("[$name] took ${duration}ms")
        }
        result
    }
}
```

### Count Active Scopes

```kotlin
object ReactiveStats {
    var activeScopes = 0
    var totalScopes = 0

    fun trackScope(scope: CoroutineScope): CoroutineScope {
        activeScopes++
        totalScopes++
        scope.coroutineContext[Job]?.invokeOnCompletion {
            activeScopes--
        }
        return scope
    }
}
```

## Next Steps

- Review the [API Reference](api-reference.md) for complete documentation
- Check [Getting Started](getting-started.md) for basic examples
- Explore [Core Concepts](core-concepts.md) for deeper understanding
