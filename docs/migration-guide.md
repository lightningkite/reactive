# Migration Guide

This guide helps you migrate between versions of Reactive and from other reactive frameworks.

## Table of Contents

- [From Other Frameworks](#from-other-frameworks)
- [Version Upgrades](#version-upgrades)
- [Common Migration Patterns](#common-migration-patterns)

## From Other Frameworks

### From RxJava/RxKotlin

#### Observables to Reactive

```kotlin
// RxJava
val observable = Observable.just(1, 2, 3)
observable.subscribe { value ->
    println(value)
}

// Reactive
val flow = flowOf(1, 2, 3)
reactiveScope {
    println(flow())
}
```

#### BehaviorSubject to Signal

```kotlin
// RxJava
val subject = BehaviorSubject.createDefault(0)
subject.onNext(1)
subject.subscribe { value ->
    println(value)
}

// Reactive
val signal = Signal(0)
signal.value = 1
reactiveScope {
    println(signal())
}
```

#### Operators

```kotlin
// RxJava
observable
    .map { it * 2 }
    .filter { it > 5 }
    .subscribe { println(it) }

// Reactive
val transformed = remember {
    source()
        .map { it * 2 }
        .filter { it > 5 }
}
reactiveScope {
    println(transformed())
}
```

### From Kotlin Flow

#### Flow to Reactive

```kotlin
// Flow
val flow: Flow<Int> = flowOf(1, 2, 3)
flow.collect { value ->
    println(value)
}

// Reactive - use Flow directly
reactiveScope {
    val current = flow()
    println(current)
}
```

#### StateFlow to Signal

```kotlin
// StateFlow
val stateFlow = MutableStateFlow(0)
stateFlow.value = 1

// Reactive
val signal = Signal(0)
signal.value = 1
```

#### SharedFlow

```kotlin
// SharedFlow
val sharedFlow = MutableSharedFlow<Int>()
launch {
    sharedFlow.emit(1)
}

// Reactive
val signal = Signal(0)
signal.value = 1  // Synchronous
```

### From LiveData (Android)

#### LiveData to Signal

```kotlin
// LiveData
class ViewModel : ViewModel() {
    private val _data = MutableLiveData<String>()
    val data: LiveData<String> = _data

    fun load() {
        _data.value = "loaded"
    }
}

// Reactive
class ViewModel {
    val data = LateInitSignal<String>()

    suspend fun load() {
        data.value = "loaded"
    }
}
```

#### Transformations

```kotlin
// LiveData
val transformed = Transformations.map(liveData) { it.uppercase() }
val switched = Transformations.switchMap(liveData) { value ->
    repository.load(value)
}

// Reactive
val transformed = remember {
    reactive().uppercase()
}
val switched = remember {
    repository.load(reactive())
}
```

#### Observe in UI

```kotlin
// LiveData
liveData.observe(lifecycleOwner) { value ->
    updateUI(value)
}

// Reactive
lifecycleOwner.lifecycleScope.reactiveScope {
    val value = reactive()
    updateUI(value)
}
```

### From Solid.js

Reactive is inspired by Solid.js, so the concepts are very similar:

```javascript
// Solid.js
const [count, setCount] = createSignal(0)
const doubled = createMemo(() => count() * 2)

createEffect(() => {
  console.log(doubled())
})
```

```kotlin
// Reactive
val count = Signal(0)
val doubled = remember { count() * 2 }

reactiveScope {
    println(doubled())
}
```

## Version Upgrades

### Version 5.x

Current stable version. No migrations needed if you're starting fresh.

### Future Versions

When new versions are released, breaking changes and migration steps will be documented here.

## Common Migration Patterns

### Callback-Based APIs

```kotlin
// Before
interface DataCallback {
    fun onData(data: Data)
    fun onError(error: Exception)
}

fun loadData(callback: DataCallback)

// After
suspend fun loadData(): Data

val data = LateInitSignal<Data>()
launch {
    try {
        data.value = loadData()
    } catch (e: Exception) {
        // Handle error
    }
}
```

### Observer Pattern

```kotlin
// Before
interface Observer<T> {
    fun onChanged(value: T)
}

class Observable<T> {
    private val observers = mutableListOf<Observer<T>>()

    fun addObserver(observer: Observer<T>) {
        observers.add(observer)
    }

    fun removeObserver(observer: Observer<T>) {
        observers.remove(observer)
    }

    fun notifyObservers(value: T) {
        observers.forEach { it.onChanged(value) }
    }
}

// After
val signal = Signal<T>(initialValue)

reactiveScope {
    val value = signal()
    // React to changes
}
```

### Property Delegates

```kotlin
// Before
class ViewModel {
    var name by observable("") { _, old, new ->
        if (old != new) notifyChange()
    }
}

// After
class ViewModel {
    val name = Signal("")
}
```

### Computed Properties

```kotlin
// Before
class Model {
    var width = 0
    var height = 0

    val area: Int
        get() = width * height  // Recalculates every time
}

// After
class Model {
    val width = Signal(0)
    val height = Signal(0)

    val area = remember {
        width() * height()  // Cached, only recalculates when dependencies change
    }
}
```

### Event Bus

```kotlin
// Before
object EventBus {
    private val listeners = mutableMapOf<String, MutableList<(Any) -> Unit>>()

    fun subscribe(event: String, listener: (Any) -> Unit) {
        listeners.getOrPut(event) { mutableListOf() }.add(listener)
    }

    fun post(event: String, data: Any) {
        listeners[event]?.forEach { it(data) }
    }
}

// After
object Events {
    val userLoggedIn = Signal<User?>(null)
    val dataUpdated = Signal<Data?>(null)
}

// Subscribe
reactiveScope {
    Events.userLoggedIn.awaitNotNull()?.let { user ->
        handleLogin(user)
    }
}

// Post
Events.userLoggedIn.value = user
```

### Data Binding

```kotlin
// Before - manual binding
editText.addTextChangedListener { text ->
    viewModel.updateName(text.toString())
}
viewModel.name.observe(this) { name ->
    if (editText.text.toString() != name) {
        editText.setText(name)
    }
}

// After - reactive lens
val name = viewModel.name  // MutableReactiveValue<String>

reactiveScope {
    if (editText.text.toString() != name()) {
        editText.setText(name())
    }
}

editText.addTextChangedListener { text ->
    name.value = text.toString()
}
```

### State Management

```kotlin
// Before - manual state management
class Store {
    private var state: State = State()
    private val listeners = mutableListOf<(State) -> Unit>()

    fun getState() = state

    fun setState(newState: State) {
        state = newState
        listeners.forEach { it(state) }
    }

    fun subscribe(listener: (State) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }
}

// After - reactive state
class Store {
    val state = Signal(State())
}

reactiveScope {
    val currentState = store.state()
    // React to state changes
}
```

### Form Validation

```kotlin
// Before
class FormValidator {
    fun validate(email: String): String? {
        return if (email.contains("@")) null else "Invalid email"
    }
}

val emailError = formValidator.validate(email.text)
if (emailError != null) {
    showError(emailError)
}

// After
val email = Signal("")
val emailIssues = IssueNode()

emailIssues.report(remember {
    if (email().contains("@")) null
    else Issue("Invalid email", Severity.ERROR)
})

reactiveScope {
    emailIssues.issues().forEach { showError(it.message) }
}
```

### Async Operations

```kotlin
// Before - callbacks
fun loadUser(id: String, callback: (User?, Error?) -> Unit) {
    GlobalScope.launch {
        try {
            val user = api.fetchUser(id)
            callback(user, null)
        } catch (e: Exception) {
            callback(null, Error(e))
        }
    }
}

// After - reactive
val user = LateInitSignal<User>()

suspend fun loadUser(id: String) {
    try {
        user.value = api.fetchUser(id)
    } catch (e: Exception) {
        // Handle error
    }
}

reactiveScope {
    user.state().handle(
        success = { displayUser(it) },
        exception = { showError(it) },
        notReady = { showLoading() }
    )
}
```

## Best Practices for Migration

1. **Start Small** - Migrate one feature at a time
2. **Test Thoroughly** - Ensure behavior matches before and after
3. **Keep Both** - Run old and new code side-by-side during transition
4. **Use Wrappers** - Create adapters between old and new APIs
5. **Document Changes** - Note differences for your team
6. **Gradual Rollout** - Deploy incrementally, monitor for issues

## Compatibility Wrappers

### LiveData Wrapper

```kotlin
fun <T> LiveData<T>.asReactive(
    lifecycleOwner: LifecycleOwner
): Reactive<T> {
    val signal = LateInitSignal<T>()

    observe(lifecycleOwner) { value ->
        if (value != null) {
            signal.value = value
        }
    }

    return signal
}
```

### Flow Wrapper

Flow is already supported natively:

```kotlin
val flow: Flow<Int> = flowOf(1, 2, 3)
reactiveScope {
    val current = flow()  // Just works!
}
```

### RxJava Wrapper

```kotlin
fun <T> Observable<T>.asReactive(scope: CoroutineScope): Reactive<T> {
    val signal = LateInitSignal<T>()

    scope.launch {
        collect { value ->
            signal.value = value
        }
    }

    return signal
}
```

## Troubleshooting Migration

### Different Behavior

If migrated code behaves differently:

1. Check if you're calling reactive values with `()`
2. Ensure reactive scopes are tied to correct lifecycle
3. Verify async operations are handled properly
4. Check that dependencies are being tracked

### Performance Issues

If you notice performance problems:

1. Use `remember` for expensive calculations
2. Batch collection updates
3. Add deactivation delays for frequently toggled UI
4. Profile to find unnecessary recalculations

### Memory Leaks

If you have memory leaks:

1. Ensure reactive scopes are cancelled
2. Check that listeners are properly removed
3. Verify coroutine scopes are tied to lifecycle
4. Use weak references if needed for long-lived observers

## Getting Help

- Check the [documentation](README.md)
- Review [examples](getting-started.md)
- Search [GitHub issues](https://github.com/lightningkite/reactive/issues)
- Ask in discussions

## Contributing Migration Examples

If you've successfully migrated from another framework, consider contributing your examples to help others!
