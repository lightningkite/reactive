# Lensing

Lensing allows you to create focused, transformed views of reactive data. Think of a lens as a "zoom" into a specific part of your data structure, with the ability to read and write through that focus.

## Table of Contents

- [What is Lensing?](#what-is-lensing)
- [Basic Lensing](#basic-lensing)
- [Lens Types](#lens-types)
- [Bidirectional Lenses](#bidirectional-lenses)
- [Lens Composition](#lens-composition)
- [Collection Lensing](#collection-lensing)
- [Advanced Patterns](#advanced-patterns)

## What is Lensing?

A lens provides a transformed view of a reactive value:

```kotlin
data class User(val name: String, val email: String, val age: Int)

val user = Signal(User("John", "john@example.com", 30))

// Create a lens focused on the name
val userName = user.lens(
    get = { it.name },
    modify = { user, newName -> user.copy(name = newName) }
)

// Read through the lens
println(userName.value)  // "John"

// Write through the lens
userName.value = "Jane"  // Updates the entire user object
println(user.value)  // User("Jane", "john@example.com", 30)
```

### Benefits

- **Encapsulation** - Hide the structure of your data
- **Reusability** - Create reusable property accessors
- **Type safety** - Compiler-checked transformations
- **Composability** - Combine lenses to access nested data
- **Two-way binding** - Read and write through the lens

## Basic Lensing

### Read-Only Lens

Transform a reactive value without allowing writes:

```kotlin
val temperature = Signal(20.0)  // Celsius

val fahrenheit: Reactive<Double> = temperature.lens { celsius ->
    celsius * 9/5 + 32
}

reactiveScope {
    println("${temperature()}°C = ${fahrenheit()}°F")
}

temperature.value = 25.0
// Prints: "25.0°C = 77.0°F"
```

### Creating a Lens Manually

```kotlin
val source = Signal(10)

val doubled = Lens(source) { it * 2 }

reactiveScope {
    println(doubled())  // 20
}

source.value = 15  // doubled is now 30
```

## Lens Types

### SetLens

A bidirectional lens that uses a setter function:

```kotlin
val number = Signal(10)

val stringValue = SetLens(
    source = number,
    get = { it.toString() },
    set = { it.toIntOrNull() ?: 0 }
)

println(stringValue.value)  // "10"

stringValue.value = "25"
println(number.value)  // 25
```

### ModifyLens

A bidirectional lens that modifies based on both old and new values:

```kotlin
data class Counter(val count: Int, val lastUpdate: Long)

val counter = Signal(Counter(0, System.currentTimeMillis()))

val count = ModifyLens(
    source = counter,
    get = { it.count },
    modify = { old, newCount ->
        old.copy(count = newCount, lastUpdate = System.currentTimeMillis())
    }
)

count.value = 5  // Updates count and timestamp
```

### SetValueLens and ModifyValueLens

Versions for `MutableReactiveValue`:

```kotlin
val user = Signal(User("John", "john@example.com", 30))

val name: MutableReactiveValue<String> = SetValueLens(
    user,
    { it.name },
    { user.copy(name = it) }
)

// Can use as a property
reactiveScope {
    println(name.value)
}

name.value = "Jane"
```

## Bidirectional Lenses

### Property Lenses

Access properties of data classes:

```kotlin
data class Person(val name: String, val age: Int, val email: String)

val person = Signal(Person("Alice", 25, "alice@example.com"))

val name = person.lens(
    get = { it.name },
    modify = { person, name -> person.copy(name = name) }
)

val age = person.lens(
    get = { it.age },
    modify = { person, age -> person.copy(age = age) }
)

// Use the lenses
name.value = "Bob"
age.value = 26
```

### Type Conversion Lenses

Convert between types:

```kotlin
val stringValue = Signal("42")

val intValue = stringValue.lens(
    get = { it.toIntOrNull() ?: 0 },
    set = { it.toString() }
)

println(intValue.value)  // 42
intValue.value = 100
println(stringValue.value)  // "100"
```

### Nullable Lenses

Handle nullable values:

```kotlin
val nullable = Signal<String?>(null)

val nonNull = nullable.lens(
    get = { it ?: "" },
    set = { if (it.isEmpty()) null else it }
)

println(nonNull.value)  // ""
nonNull.value = "Hello"
println(nullable.value)  // "Hello"
```

## Lens Composition

### Nested Property Access

```kotlin
data class Address(val street: String, val city: String)
data class Person(val name: String, val address: Address)

val person = Signal(Person("John", Address("Main St", "NYC")))

// Lens to address
val address = person.lens(
    { it.address },
    { person, addr -> person.copy(address = addr) }
)

// Lens from address to city
val city = address.lens(
    { it.city },
    { addr, city -> addr.copy(city = city) }
)

// Access nested property
println(city.value)  // "NYC"
city.value = "Boston"
println(person.value.address.city)  // "Boston"
```

### Chaining Lenses

```kotlin
val person = Signal(Person("John", Address("Main St", "NYC")))

val city = person
    .lens({ it.address }, { p, a -> p.copy(address = a) })
    .lens({ it.city }, { a, c -> a.copy(city = c) })

city.value = "LA"
```

## Collection Lensing

### LensByElement

Create lenses for individual elements in a collection:

```kotlin
val items = ReactiveMutableList(listOf(1, 2, 3, 4, 5))

val lensedItems = LensByElement(
    source = items,
    getId = { it },  // Use value as ID
    transform = { element -> element.lens { it * 2 } }
)

reactiveScope {
    lensedItems().forEach { element ->
        println(element())  // 2, 4, 6, 8, 10
    }
}
```

### Per-Element Transformation

```kotlin
data class TodoItem(val id: Int, val title: String, val done: Boolean)

val todos = ReactiveMutableList(
    listOf(
        TodoItem(1, "Learn Reactive", false),
        TodoItem(2, "Build app", false)
    )
)

val todoLenses = LensByElement(
    source = todos,
    getId = { it.id },
    transform = { element ->
        // Each element gets a lens for the 'done' field
        element.lens(
            { it.done },
            { todo, done -> todo.copy(done = done) }
        )
    }
)

// Toggle first todo
reactiveScope {
    val firstDone = todoLenses()[0]
    firstDone.value = !firstDone.value
}
```

### Map/Filter Through Lenses

```kotlin
val numbers = ReactiveMutableList(listOf(1, 2, 3, 4, 5))

val doubled = numbers.lens { list ->
    list.map { it * 2 }
}

val evens = numbers.lens { list ->
    list.filter { it % 2 == 0 }
}

reactiveScope {
    println("Doubled: ${doubled()}")
    println("Evens: ${evens()}")
}
```

## Advanced Patterns

### Validation Lens

```kotlin
val email = Signal("")

val validatedEmail = email.lens(
    get = { it },
    modify = { old, new ->
        if (new.contains("@")) new else old
    }
)

validatedEmail.value = "invalid"  // Rejected
println(email.value)  // ""

validatedEmail.value = "valid@example.com"  // Accepted
println(email.value)  // "valid@example.com"
```

### Clamping Lens

```kotlin
val value = Signal(50)

val clamped = value.lens(
    get = { it },
    modify = { _, new -> new.coerceIn(0, 100) }
)

clamped.value = 150  // Clamped to 100
println(value.value)  // 100

clamped.value = -10  // Clamped to 0
println(value.value)  // 0
```

### Format/Parse Lens

```kotlin
val timestamp = Signal(System.currentTimeMillis())

val formatted = timestamp.lens(
    get = { SimpleDateFormat("yyyy-MM-dd").format(Date(it)) },
    set = { SimpleDateFormat("yyyy-MM-dd").parse(it)?.time ?: 0L }
)

println(formatted.value)  // "2025-11-06"
formatted.value = "2025-12-25"
```

### Optional Lens

```kotlin
data class Config(val settings: Map<String, String>)

val config = Signal(Config(mapOf("theme" -> "dark")))

fun optionalSetting(key: String) = config.lens(
    get = { it.settings[key] },
    modify = { cfg, value ->
        val newSettings = if (value == null) {
            cfg.settings - key
        } else {
            cfg.settings + (key to value)
        }
        cfg.copy(settings = newSettings)
    }
)

val theme = optionalSetting("theme")
println(theme.value)  // "dark"
```

### Derived Lens

```kotlin
data class Rectangle(val width: Int, val height: Int)

val rect = Signal(Rectangle(10, 20))

val area: Reactive<Int> = rect.lens { it.width * it.height }
val perimeter: Reactive<Int> = rect.lens { 2 * (it.width + it.height) }

reactiveScope {
    println("Area: ${area()}, Perimeter: ${perimeter()}")
}
```

### Lens with Undo

```kotlin
class UndoableSignal<T>(initial: T) {
    private val current = Signal(initial)
    private val history = mutableListOf<T>()

    val value = current.lens(
        get = { it },
        modify = { old, new ->
            history.add(old)
            new
        }
    )

    fun undo() {
        if (history.isNotEmpty()) {
            current.value = history.removeLast()
        }
    }
}
```

## Performance Considerations

### Lazy Lenses

Lenses are lazy - they only activate when they have listeners:

```kotlin
var transformCount = 0

val source = Signal(10)
val lens = Lens(source) {
    transformCount++
    it * 2
}

println(transformCount)  // 0 - not activated yet

val remover = lens.addListener { }
println(transformCount)  // 1 - now activated

remover()
```

### Lens Caching

Lenses cache their transformed values:

```kotlin
var expensiveCallCount = 0

val source = Signal(listOf(1, 2, 3))
val lens = Lens(source) {
    expensiveCallCount++
    it.map { expensiveOperation(it) }
}

reactiveScope { lens() }
reactiveScope { lens() }
reactiveScope { lens() }

// Only calls expensive operation once
```

### Avoiding Unnecessary Lenses

```kotlin
// ✗ Bad - unnecessary lens for simple access
val bad = signal.lens { it }

// ✓ Good - just use the signal directly
val good = signal
```

## Testing Lenses

```kotlin
@Test
fun testPropertyLens() {
    data class Person(val name: String, val age: Int)
    val person = Signal(Person("John", 30))

    val name = person.lens(
        { it.name },
        { p, n -> p.copy(name = n) }
    )

    // Read
    assertEquals("John", name.value)

    // Write
    name.value = "Jane"
    assertEquals("Jane", person.value.name)
    assertEquals(30, person.value.age)
}

@Test
fun testLensReactivity() {
    val source = Signal(10)
    val doubled = Lens(source) { it * 2 }

    var updates = 0
    doubled.addListener { updates++ }

    source.value = 20
    assertEquals(1, updates)
    assertEquals(40, doubled.state.getOrNull())
}
```

## Best Practices

1. **Use lenses for property access** - Especially in data classes
2. **Keep transformations pure** - No side effects in get/set/modify
3. **Name lenses descriptively** - `userName` not `lens1`
4. **Compose lenses for nested access** - Don't create deep lens chains manually
5. **Consider read-only lenses** - If you don't need writes
6. **Test bidirectional behavior** - Ensure get/set/modify are consistent
7. **Use appropriate lens type** - SetLens vs ModifyLens depending on needs

## Common Pitfalls

### Inconsistent Get/Set

```kotlin
// ✗ Bad - get and set aren't inverses
val bad = signal.lens(
    get = { it * 2 },
    set = { it }  // Should be { it / 2 }
)

// ✓ Good - consistent
val good = signal.lens(
    get = { it * 2 },
    set = { it / 2 }
)
```

### Mutating in Get

```kotlin
val list = Signal(mutableListOf(1, 2, 3))

// ✗ Bad - mutating in get
val bad = list.lens {
    it.add(4)  // Side effect!
    it
}

// ✓ Good - return new list
val good = list.lens {
    it + 4
}
```

### Forgetting to Activate

```kotlin
val lens = source.lens { it * 2 }

// ✗ Bad - accessing .value when not activated
val value = lens.value  // May not be updated

// ✓ Good - access through reactive context or add listener
reactiveScope {
    val value = lens()
}
```

## Next Steps

- [Validation](validation.md) - Hierarchical validation with lenses
- [Collections](collections.md) - Reactive collections
- [Advanced Topics](advanced-topics.md) - Complex lensing patterns
