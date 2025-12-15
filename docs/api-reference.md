# API Reference

Complete API documentation for the Reactive library.

## Core Interfaces

### Reactive<T>

Base interface for all reactive values.

```kotlin
interface Reactive<out T> : Listenable {
    val state: ReactiveState<T>
}
```

**Methods:** Inherited from `Listenable`

### ReactiveValue<T>

A reactive value that is always available (never loading or error).

```kotlin
interface ReactiveValue<out T> : Reactive<T> {
    val value: T
}
```

### MutableReactive<T>

A reactive value that can be set asynchronously.

```kotlin
interface MutableReactive<T> : Reactive<T> {
    suspend fun set(value: T)
}
```

### MutableReactiveValue<T>

A reactive value that can be set synchronously or asynchronously.

```kotlin
interface MutableReactiveValue<T> : MutableReactive<T>, ReactiveValue<T> {
    var value: T
}
```

## Core Classes

### Signal<T>

**Constructor:** `Signal(initialValue: T)`

**Properties:**
- `value: T` - Get or set the current value

**Example:**
```kotlin
val counter = Signal(0)
counter.value = 1
```

### LateInitSignal<T>

**Constructor:** `LateInitSignal<T>()`

**Properties:**
- `value: T` - Get or set the current value

**Methods:**
- `unset()` - Reset to loading state

**Example:**
```kotlin
val data = LateInitSignal<String>()
data.value = "loaded"
data.unset()  // Back to loading
```

### RawReactive<T>

**Constructor:** `RawReactive<T>(initialState: ReactiveState<T> = ReactiveState.notReady)`

**Properties:**
- `state: ReactiveState<T>` - Get or set the reactive state

**Example:**
```kotlin
val raw = RawReactive<Int>()
raw.state = ReactiveState(42)
raw.state = ReactiveState.notReady
```

### Constant<T>

**Constructor:** `Constant(value: T)`

**Properties:**
- `value: T` - The constant value (read-only)

**Example:**
```kotlin
val pi = Constant(3.14159)
```

## ReactiveState<T>

### Properties

- `ready: Boolean` - True if state has a value
- `success: Boolean` - True if state has a value and no error
- `exception: Exception?` - The exception if state is error

### Methods

- `handle(success, exception, notReady)` - Pattern match on state
- `map(mapper)` - Transform the value
- `getOrNull()` - Get value or null
- `get()` - Get value or throw (deprecated)

### Companion Methods

- `ReactiveState.notReady` - Create not-ready state
- `ReactiveState.exception<T>(e)` - Create error state
- `ReactiveState.wrap<T>(value)` - Wrap a value (useful for null)

## Functions

### remember

```kotlin
fun <T> remember(
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    useLastWhileLoading: Boolean = false,
    deactivationDelay: Duration? = null,
    action: ReactiveContext.() -> T
): Reactive<T>
```

**Parameters:**
- `coroutineContext` - Context for running calculations
- `useLastWhileLoading` - Keep previous value while recalculating
- `deactivationDelay` - Delay before shutting down after last listener removed
- `action` - Calculation block

**Returns:** A reactive value that updates when dependencies change

### rememberSuspending

```kotlin
fun <T> rememberSuspending(
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    useLastWhileLoading: Boolean = false,
    deactivationDelay: Duration? = null,
    action: suspend CalculationContext.() -> T
): Reactive<T>
```

**Parameters:** Same as `remember`

**Returns:** A reactive value for suspending calculations

### mutableRemember

```kotlin
fun <T> mutableRemember(
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    useLastWhileLoading: Boolean = false,
    deactivationDelay: Duration? = null,
    initialValue: ReactiveContext.() -> T
): MutableReactiveValue<T>
```

**Returns:** A mutable reactive value

### mutableRememberSuspending

```kotlin
fun <T> mutableRememberSuspending(
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    useLastWhileLoading: Boolean = false,
    deactivationDelay: Duration? = null,
    initialValue: suspend CalculationContext.() -> T
): MutableReactiveValue<T>
```

**Returns:** A mutable reactive value for suspending calculations

### reactiveScope

```kotlin
fun CoroutineScope.reactiveScope(
    action: ReactiveContext.() -> Unit
)
```

**Parameters:**
- `action` - Block to execute reactively

Creates a reactive scope for side effects.

### reactiveSuspending

```kotlin
fun CoroutineScope.reactiveSuspending(
    action: suspend CalculationContext.() -> Unit
)
```

**Parameters:**
- `action` - Suspending block to execute reactively

Creates a suspending reactive scope.

## Reactive Context

### ReactiveContext

**Type:** `typealias ReactiveContext = TypedReactiveContext<*>`

### TypedReactiveContext<T>

**Constructor:**
```kotlin
TypedReactiveContext(
    scope: CalculationContext,
    useLastWhileLoading: Boolean = false,
    reportTo: RawReactive<T> = RawReactive(),
    action: TypedReactiveContext<T>.() -> T
)
```

**Methods:**
- `startCalculation()` - Start the reactive calculation
- `cancel()` - Cancel the context and clean up

**Operators:**
- `Reactive<R>.invoke(): R` - Access value and track dependency
- `Reactive<R>.once(): R` - Access value once
- `Reactive<R>.state(): ReactiveState<R>` - Get state
- `Reactive<R?>.awaitNotNull(): R` - Wait for non-null
- `Flow<T>.invoke(): T` - Use Flow value
- `Deferred<T>.invoke(): T` - Use Deferred value
- `async(action): T` - Execute async operation

## Collections

### ReactiveMutableList<E>

**Constructor:**
- `ReactiveMutableList()` - Empty list
- `ReactiveMutableList(initial: List<E>)` - From existing list

**Implements:** `MutableList<E>`, `MutableReactiveValue<List<E>>`

**All standard List operations trigger notifications.**

### ReactiveMutableMap<K, V>

**Constructor:**
- `ReactiveMutableMap()` - Empty map
- `ReactiveMutableMap(initial: Map<K, V>)` - From existing map

**Implements:** `MutableMap<K, V>`, `MutableReactiveValue<Map<K, V>>`

**All standard Map operations trigger notifications.**

### ReactiveMutableSet<E>

**Constructor:**
- `ReactiveMutableSet()` - Empty set
- `ReactiveMutableSet(initial: Set<E>)` - From existing set

**Implements:** `MutableSet<E>`, `MutableReactiveValue<Set<E>>`

**All standard Set operations trigger notifications.**

## Lensing

### Lens<S, T, L>

**Constructor:**
```kotlin
Lens(
    source: S,
    get: (T) -> L
)
```

**Type Parameters:**
- `S` - Source reactive type
- `T` - Source value type
- `L` - Lens value type

### SetLens<O, T>

**Constructor:**
```kotlin
SetLens(
    source: MutableReactive<O>,
    get: (O) -> T,
    set: (T) -> O
)
```

**Bidirectional lens using setter function.**

### ModifyLens<O, T>

**Constructor:**
```kotlin
ModifyLens(
    source: MutableReactive<O>,
    get: (O) -> T,
    modify: (O, T) -> O
)
```

**Bidirectional lens using modifier function.**

### Extension Functions

```kotlin
fun <T, L> MutableReactive<T>.lens(
    get: (T) -> L,
    set: (L) -> T
): MutableReactive<L>

fun <T, L> MutableReactive<T>.lens(
    get: (T) -> L,
    modify: (T, L) -> T
): MutableReactive<L>
```

## Validation

### IssueNode

**Constructor:**
- `IssueNode(parent: IssueNode? = null)`

**Properties:**
- `issues: Reactive<List<Issue>>` - All issues including children

**Methods:**
- `report(issue: Reactive<Issue?>)` - Report an issue
- `connect(parent: IssueNode): () -> Unit` - Connect to parent
- `addChild(child: IssueNode): () -> Unit` - Add child node

### Validated<T>

**Interface:**
```kotlin
interface Validated<out T> : Reactive<T> {
    val issues: Reactive<List<Issue>>
}
```

### MutableValidated<T>

**Interface:**
```kotlin
interface MutableValidated<T> : Validated<T>, MutableReactive<T>
```

### Issue

**Data Class:**
```kotlin
data class Issue(
    val message: String,
    val severity: Severity
)
```

### Severity

**Enum:**
```kotlin
enum class Severity {
    ERROR,
    WARNING,
    INFO
}
```

## Draft

### Draft<T>

**Interface:**
```kotlin
interface Draft<T> : ReactiveWithMutableValue<T> {
    val published: MutableReactive<T>
    val changesMade: Reactive<Boolean>
    suspend fun publish(): T
    fun cancel()
}
```

**Methods:**
- `publish()` - Save changes to published value
- `cancel()` - Discard changes and revert

## Extension Functions

### Debounce

```kotlin
fun <T> Reactive<T>.debounce(delay: Duration): Reactive<T>
```

### Common Lenses

```kotlin
fun <T> Reactive<T?>.asNonNull(default: T): Reactive<T>
fun <T> MutableReactive<T?>.asNonNull(default: T): MutableReactive<T>
```

## AppScope

**Global CoroutineScope that lives as long as the app is running.**

```kotlin
object AppScope : CoroutineScope {
    override val coroutineContext: CoroutineContext
}
```

## Exception Handling

### Reactive.reportException

```kotlin
var Reactive.Companion.reportException: (Throwable) -> Unit
```

**Set this to customize exception reporting:**

```kotlin
Reactive.reportException = { throwable ->
    logger.error("Reactive exception", throwable)
}
```

## Platform-Specific

### Android

No Android-specific APIs. Use standard coroutine dispatchers:

```kotlin
val viewModel = remember(Dispatchers.Main) {
    // Runs on main thread
}
```

### iOS

No iOS-specific APIs. Use appropriate dispatchers.

### JavaScript

No JS-specific APIs.

### JVM

No JVM-specific APIs.

## Type Aliases

```kotlin
typealias ReactiveContext = TypedReactiveContext<*>
typealias CalculationContext = CoroutineScope
```

## Annotations

### @InternalReactiveApi

Marks internal APIs that should not be used by library consumers.

```kotlin
@RequiresOptIn
annotation class InternalReactiveApi
```

## Complete Example

```kotlin
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.context.*
import com.lightningkite.reactive.lensing.*
import com.lightningkite.reactive.validation.*

data class User(val name: String, val email: String, val age: Int)

class UserViewModel {
    private val userIssues = IssueNode()
    val user = Signal(User("", "", 0))

    val name = user.lens(
        { it.name },
        { u, n -> u.copy(name = n) }
    )

    val email = user.lens(
        { it.email },
        { u, e -> u.copy(email = e) }
    )

    val age = user.lens(
        { it.age },
        { u, a -> u.copy(age = a) }
    )

    val isValid = remember {
        userIssues.issues().none { it.severity == Severity.ERROR }
    }

    init {
        // Validation
        IssueNode(parent = userIssues).apply {
            report(remember {
                if (name().isEmpty()) {
                    Issue("Name required", Severity.ERROR)
                } else null
            })
        }

        IssueNode(parent = userIssues).apply {
            report(remember {
                if (!email().contains("@")) {
                    Issue("Invalid email", Severity.ERROR)
                } else null
            })
        }

        IssueNode(parent = userIssues).apply {
            report(remember {
                if (age() < 0) {
                    Issue("Invalid age", Severity.ERROR)
                } else null
            })
        }
    }
}
```
