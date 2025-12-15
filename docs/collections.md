# Reactive Collections

Reactive provides reactive versions of standard Kotlin collections that automatically notify listeners when their contents change.

## Table of Contents

- [ReactiveMutableList](#reactivemutablelist)
- [ReactiveMutableMap](#reactivemutablemap)
- [ReactiveMutableSet](#reactivemutableset)
- [Collection Operations](#collection-operations)
- [Performance Considerations](#performance-considerations)

## ReactiveMutableList

A list that notifies listeners when items are added, removed, or changed.

### Creating a ReactiveMutableList

```kotlin
// Empty list
val emptyList = ReactiveMutableList<Int>()

// From existing list
val numbers = ReactiveMutableList(listOf(1, 2, 3, 4, 5))

// With initial capacity
val buffered = ReactiveMutableList<String>(initialCapacity = 100)
```

### Basic Operations

```kotlin
val list = ReactiveMutableList<Int>()

reactiveScope {
    println("List: ${list()}")
}

list.add(1)           // Triggers update
list.add(2)           // Triggers update
list.addAll(listOf(3, 4, 5))  // Triggers update
list[0] = 10          // Triggers update
list.removeAt(0)      // Triggers update
list.clear()          // Triggers update
```

### List-Specific Operations

```kotlin
val list = ReactiveMutableList(listOf(1, 2, 3))

// Insert at index
list.add(1, 99)  // [1, 99, 2, 3]

// Add all at index
list.addAll(2, listOf(10, 11))  // [1, 99, 10, 11, 2, 3]

// Remove by value
list.remove(99)  // [1, 10, 11, 2, 3]

// Iterate and modify
list.iterator().apply {
    while (hasNext()) {
        if (next() % 2 == 0) {
            remove()
        }
    }
}
```

### Reactive Queries

```kotlin
val todos = ReactiveMutableList<Todo>()

val completed = remember {
    todos().filter { it.done }
}

val incomplete = remember {
    todos().filter { !it.done }
}

val count = remember {
    todos().size
}

reactiveScope {
    println("${incomplete().size} incomplete, ${completed().size} completed")
}
```

## ReactiveMutableMap

A map that notifies listeners when entries are added, removed, or changed.

### Creating a ReactiveMutableMap

```kotlin
// Empty map
val emptyMap = ReactiveMutableMap<String, Int>()

// From existing map
val scores = ReactiveMutableMap(mapOf("Alice" to 100, "Bob" to 95))

// With initial capacity
val cache = ReactiveMutableMap<String, Data>(initialCapacity = 1000)
```

### Basic Operations

```kotlin
val map = ReactiveMutableMap<String, Int>()

reactiveScope {
    println("Map: ${map()}")
}

map["key1"] = 100      // Triggers update
map.put("key2", 200)   // Triggers update
map.putAll(mapOf("key3" to 300, "key4" to 400))  // Triggers update
map.remove("key1")     // Triggers update
map.clear()            // Triggers update
```

### Map-Specific Operations

```kotlin
val userAges = ReactiveMutableMap<String, Int>()

// Put if absent
userAges.putIfAbsent("Alice", 25)

// Get or put
val age = userAges.getOrPut("Bob") { 30 }

// Compute
userAges.compute("Alice") { key, old ->
    (old ?: 0) + 1
}

// Compute if present
userAges.computeIfPresent("Bob") { key, value ->
    value + 1
}

// Compute if absent
userAges.computeIfAbsent("Charlie") { 35 }
```

### Reactive Map Queries

```kotlin
val inventory = ReactiveMutableMap<String, Int>()

val totalItems = remember {
    inventory().values.sum()
}

val itemCount = remember {
    inventory().size
}

val lowStock = remember {
    inventory().filter { it.value < 10 }
}

reactiveScope {
    println("Total items: ${totalItems()}")
    println("Low stock items: ${lowStock().keys}")
}
```

## ReactiveMutableSet

A set that notifies listeners when items are added or removed.

### Creating a ReactiveMutableSet

```kotlin
// Empty set
val emptySet = ReactiveMutableSet<String>()

// From existing set
val tags = ReactiveMutableSet(setOf("kotlin", "reactive", "multiplatform"))

// With initial capacity
val visited = ReactiveMutableSet<String>(initialCapacity = 1000)
```

### Basic Operations

```kotlin
val set = ReactiveMutableSet<String>()

reactiveScope {
    println("Set: ${set()}")
}

set.add("item1")         // Triggers update
set.addAll(setOf("item2", "item3"))  // Triggers update
set.remove("item1")      // Triggers update
set.clear()              // Triggers update
```

### Set-Specific Operations

```kotlin
val activeUsers = ReactiveMutableSet<String>()

// Add returns boolean (true if added, false if already present)
val added = activeUsers.add("user1")

// Remove returns boolean
val removed = activeUsers.remove("user1")

// Retain only
activeUsers.retainAll(setOf("user1", "user2", "user3"))

// Remove all
activeUsers.removeAll(setOf("user4", "user5"))
```

### Reactive Set Queries

```kotlin
val selectedItems = ReactiveMutableSet<String>()

val selectionCount = remember {
    selectedItems().size
}

val hasSelection = remember {
    selectedItems().isNotEmpty()
}

val isSelected = { id: String ->
    remember {
        id in selectedItems()
    }
}

reactiveScope {
    if (hasSelection()) {
        println("${selectionCount()} items selected")
    }
}
```

## Collection Operations

### Batch Updates

Minimize notifications by batching updates:

```kotlin
val list = ReactiveMutableList<Int>()

// Multiple updates = multiple notifications
list.add(1)
list.add(2)
list.add(3)

// Better: Single notification
list.addAll(listOf(1, 2, 3))
```

### Transformation Chains

```kotlin
val numbers = ReactiveMutableList(listOf(1, 2, 3, 4, 5))

val doubled = remember {
    numbers().map { it * 2 }
}

val sum = remember {
    doubled().sum()
}

val average = remember {
    val items = numbers()
    if (items.isEmpty()) 0.0 else items.average()
}

reactiveScope {
    println("Numbers: ${numbers()}")
    println("Doubled: ${doubled()}")
    println("Sum: ${sum()}")
    println("Average: ${average()}")
}
```

### Filtering and Searching

```kotlin
data class Item(val id: Int, val name: String, val active: Boolean)

val items = ReactiveMutableList<Item>()

val activeItems = remember {
    items().filter { it.active }
}

val itemById = { id: Int ->
    remember {
        items().find { it.id == id }
    }
}

val itemNames = remember {
    items().map { it.name }
}
```

### Sorting

```kotlin
val items = ReactiveMutableList(listOf(3, 1, 4, 1, 5, 9, 2, 6))

val sorted = remember {
    items().sorted()
}

val sortedDesc = remember {
    items().sortedDescending()
}

// Sort in place
items.sort()  // Triggers notification
```

### Grouping

```kotlin
data class Product(val category: String, val name: String, val price: Double)

val products = ReactiveMutableList<Product>()

val byCategory = remember {
    products().groupBy { it.category }
}

val avgPriceByCategory = remember {
    products()
        .groupBy { it.category }
        .mapValues { (_, items) -> items.map { it.price }.average() }
}
```

### Aggregations

```kotlin
val numbers = ReactiveMutableList(listOf(1, 2, 3, 4, 5))

val sum = remember { numbers().sum() }
val average = remember { numbers().average() }
val min = remember { numbers().minOrNull() }
val max = remember { numbers().maxOrNull() }

val statistics = remember {
    val nums = numbers()
    Statistics(
        count = nums.size,
        sum = nums.sum(),
        avg = nums.average(),
        min = nums.minOrNull() ?: 0,
        max = nums.maxOrNull() ?: 0
    )
}
```

## Performance Considerations

### Use Appropriate Collection Types

```kotlin
// ✓ Good - List for ordered data
val timeline = ReactiveMutableList<Event>()

// ✓ Good - Map for key-value lookups
val userCache = ReactiveMutableMap<String, User>()

// ✓ Good - Set for unique items
val selectedIds = ReactiveMutableSet<Int>()
```

### Batch Operations

```kotlin
val list = ReactiveMutableList<Int>()

// ✗ Bad - multiple notifications
for (i in 1..1000) {
    list.add(i)  // Triggers 1000 notifications!
}

// ✓ Good - single notification
list.addAll((1..1000).toList())
```

### Avoid Expensive Operations in Reactivity

```kotlin
val items = ReactiveMutableList<Item>()

// ✗ Bad - expensive operation runs on every change
reactiveScope {
    items().forEach { item ->
        expensiveOperation(item)  // Runs for ALL items every time
    }
}

// ✓ Good - use remember to cache expensive results
val processed = remember {
    items().map { expensiveOperation(it) }
}

reactiveScope {
    processed().forEach { display(it) }
}
```

### Clear When Replacing

```kotlin
val list = ReactiveMutableList<Int>()

// ✗ Bad - two notifications
list.clear()
list.addAll(newItems)

// ✓ Good - can't avoid two notifications, but be aware
// Consider if you need a clear() or can just replace
```

### Index Access

```kotlin
val list = ReactiveMutableList<String>()

// Efficient - direct index access
list[0] = "updated"

// Less efficient - search by value
list.remove("item")  // Has to search
```

## Testing Collections

```kotlin
@Test
fun testReactiveList() {
    val list = ReactiveMutableList<Int>()
    var notifications = 0

    list.addListener { notifications++ }

    list.add(1)
    assertEquals(1, notifications)
    assertEquals(listOf(1), list.value)

    list.addAll(listOf(2, 3))
    assertEquals(2, notifications)
    assertEquals(listOf(1, 2, 3), list.value)

    list.clear()
    assertEquals(3, notifications)
    assertEquals(emptyList(), list.value)
}

@Test
fun testReactiveMap() {
    val map = ReactiveMutableMap<String, Int>()
    var notifications = 0

    map.addListener { notifications++ }

    map["key"] = 1
    assertEquals(1, notifications)

    map.remove("key")
    assertEquals(2, notifications)
}
```

## Common Patterns

### Todo List

```kotlin
data class Todo(val id: Int, val title: String, val done: Boolean)

class TodoList {
    val items = ReactiveMutableList<Todo>()

    val all = remember { items() }
    val active = remember { items().filter { !it.done } }
    val completed = remember { items().filter { it.done } }

    fun add(title: String) {
        val id = items.value.maxOfOrNull { it.id }?.plus(1) ?: 1
        items.add(Todo(id, title, false))
    }

    fun toggle(id: Int) {
        val index = items.value.indexOfFirst { it.id == id }
        if (index >= 0) {
            val todo = items[index]
            items[index] = todo.copy(done = !todo.done)
        }
    }

    fun remove(id: Int) {
        items.removeAll { it.id == id }
    }
}
```

### Shopping Cart

```kotlin
data class CartItem(val productId: String, val quantity: Int, val price: Double)

class ShoppingCart {
    private val items = ReactiveMutableMap<String, CartItem>()

    val itemCount = remember { items().values.sumOf { it.quantity } }
    val total = remember { items().values.sumOf { it.price * it.quantity } }

    fun addItem(productId: String, price: Double, quantity: Int = 1) {
        items.compute(productId) { _, existing ->
            val currentQty = existing?.quantity ?: 0
            CartItem(productId, currentQty + quantity, price)
        }
    }

    fun removeItem(productId: String) {
        items.remove(productId)
    }

    fun updateQuantity(productId: String, quantity: Int) {
        if (quantity <= 0) {
            removeItem(productId)
        } else {
            items.computeIfPresent(productId) { _, item ->
                item.copy(quantity = quantity)
            }
        }
    }
}
```

### Selection Manager

```kotlin
class SelectionManager<T> {
    val selected = ReactiveMutableSet<T>()

    val count = remember { selected().size }
    val hasSelection = remember { selected().isNotEmpty() }

    fun toggle(item: T) {
        if (item in selected.value) {
            selected.remove(item)
        } else {
            selected.add(item)
        }
    }

    fun selectAll(items: List<T>) {
        selected.addAll(items)
    }

    fun clearSelection() {
        selected.clear()
    }

    fun isSelected(item: T) = remember {
        item in selected()
    }
}
```

## Next Steps

- [Lensing](lensing.md) - Collection lensing with LensByElement
- [Advanced Topics](advanced-topics.md) - Performance optimization
- [Validation](validation.md) - Validating collection items
