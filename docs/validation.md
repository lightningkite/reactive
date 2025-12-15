# Validation

Reactive provides a hierarchical validation system that allows you to track validation issues across your data structures, with automatic propagation from children to parents.

## Table of Contents

- [Validation Overview](#validation-overview)
- [IssueNode](#issuenode)
- [Validated Types](#validated-types)
- [Validation Lenses](#validation-lenses)
- [Hierarchical Validation](#hierarchical-validation)
- [Common Patterns](#common-patterns)

## Validation Overview

The validation system provides:

- **Hierarchical structure** - Issues propagate from children to parents
- **Reactive issues** - Validation updates automatically when data changes
- **Type-safe** - Issues are associated with specific data
- **Composable** - Build complex validation from simple rules

```kotlin
data class Issue(val message: String, val severity: Severity)

enum class Severity { ERROR, WARNING, INFO }
```

## IssueNode

`IssueNode` represents a node in the validation tree. Each node can:

1. Report its own issue
2. Have child nodes
3. Propagate child issues to its parent

```kotlin
val root = IssueNode()
val child1 = IssueNode(parent = root)
val child2 = IssueNode(parent = root)

// Report an issue on child1
child1.report(Constant(Issue("Error in child1", Severity.ERROR)))

// Access all issues (includes child issues)
reactiveScope {
    root.issues().forEach { issue ->
        println(issue.message)
    }
}
```

### Creating IssueNodes

```kotlin
// Root node (no parent)
val rootNode = IssueNode()

// Child node
val childNode = IssueNode(parent = rootNode)

// Grandchild node
val grandchildNode = IssueNode(parent = childNode)
```

### Reporting Issues

```kotlin
val node = IssueNode()

// Report a static issue
node.report(Constant(Issue("Required field", Severity.ERROR)))

// Report a reactive issue
val value = Signal("")
node.report(remember {
    if (value().isEmpty()) {
        Issue("Field cannot be empty", Severity.ERROR)
    } else {
        null
    }
})
```

### Accessing Issues

```kotlin
val node = IssueNode()

reactiveScope {
    val allIssues = node.issues()
    println("Total issues: ${allIssues.size}")

    allIssues.forEach { issue ->
        println("[${issue.severity}] ${issue.message}")
    }
}
```

## Validated Types

### Validated<T>

A reactive value with associated validation:

```kotlin
interface Validated<out T> : Reactive<T> {
    val issues: Reactive<List<Issue>>
}
```

### MutableValidated<T>

A mutable validated reactive value:

```kotlin
val email = MutableValidated(
    value = Signal(""),
    issues = IssueNode()
)

// Set up validation
email.issues.report(remember {
    val value = email()
    if (!value.contains("@")) {
        Issue("Invalid email", Severity.ERROR)
    } else {
        null
    }
})

// Use it
reactiveScope {
    println("Email: ${email()}")
    email.issues().forEach { issue ->
        println("Issue: ${issue.message}")
    }
}
```

## Validation Lenses

Create validated lenses for properties:

```kotlin
data class User(val name: String, val email: String, val age: Int)

val userIssues = IssueNode()
val user = Signal(User("", "", 0))

val name = user.validatedLens(
    issues = IssueNode(parent = userIssues),
    get = { it.name },
    modify = { user, name -> user.copy(name = name) }
).apply {
    issues.report(remember {
        if (value().isEmpty()) {
            Issue("Name is required", Severity.ERROR)
        } else {
            null
        }
    })
}

val email = user.validatedLens(
    issues = IssueNode(parent = userIssues),
    get = { it.email },
    modify = { user, email -> user.copy(email = email) }
).apply {
    issues.report(remember {
        val value = value()
        when {
            value.isEmpty() -> Issue("Email is required", Severity.ERROR)
            !value.contains("@") -> Issue("Invalid email format", Severity.ERROR)
            else -> null
        }
    })
}

// Access all validation issues
reactiveScope {
    userIssues.issues().forEach { issue ->
        println(issue.message)
    }
}
```

## Hierarchical Validation

### Parent-Child Relationships

Issues from child nodes automatically propagate to parent nodes:

```kotlin
val formIssues = IssueNode()

// Personal info section
val personalInfoIssues = IssueNode(parent = formIssues)
val nameIssues = IssueNode(parent = personalInfoIssues)
val emailIssues = IssueNode(parent = personalInfoIssues)

// Address section
val addressIssues = IssueNode(parent = formIssues)
val streetIssues = IssueNode(parent = addressIssues)
val cityIssues = IssueNode(parent = addressIssues)

// Report issues
nameIssues.report(Constant(Issue("Name required", Severity.ERROR)))
streetIssues.report(Constant(Issue("Street required", Severity.ERROR)))

// Access all form issues
reactiveScope {
    val allIssues = formIssues.issues()
    println("Total form issues: ${allIssues.size}")  // 2
}

// Access section issues
reactiveScope {
    val personalIssues = personalInfoIssues.issues()
    println("Personal info issues: ${personalIssues.size}")  // 1

    val addrIssues = addressIssues.issues()
    println("Address issues: ${addrIssues.size}")  // 1
}
```

### Connecting and Disconnecting

```kotlin
val parent = IssueNode()
val child = IssueNode()  // Not connected initially

child.report(Constant(Issue("Child issue", Severity.ERROR)))

// Parent doesn't see the issue yet
println(parent.issues().size)  // 0

// Connect child to parent
val disconnect = child.connect(parent)

// Now parent sees the issue
println(parent.issues().size)  // 1

// Disconnect
disconnect()

// Parent no longer sees the issue
println(parent.issues().size)  // 0
```

## Common Patterns

### Form Validation

```kotlin
data class RegistrationForm(
    val username: String,
    val email: String,
    val password: String,
    val confirmPassword: String
)

class RegistrationValidator(form: Signal<RegistrationForm>) {
    val issues = IssueNode()

    val username = form.validatedLens(
        issues = IssueNode(parent = issues),
        get = { it.username },
        modify = { f, v -> f.copy(username = v) }
    ).apply {
        issues.report(remember {
            val value = value()
            when {
                value.isEmpty() -> Issue("Username required", Severity.ERROR)
                value.length < 3 -> Issue("Username too short", Severity.ERROR)
                !value.matches(Regex("[a-zA-Z0-9_]+")) ->
                    Issue("Invalid username format", Severity.ERROR)
                else -> null
            }
        })
    }

    val email = form.validatedLens(
        issues = IssueNode(parent = issues),
        get = { it.email },
        modify = { f, v -> f.copy(email = v) }
    ).apply {
        issues.report(remember {
            val value = value()
            when {
                value.isEmpty() -> Issue("Email required", Severity.ERROR)
                !value.contains("@") -> Issue("Invalid email", Severity.ERROR)
                else -> null
            }
        })
    }

    val password = form.validatedLens(
        issues = IssueNode(parent = issues),
        get = { it.password },
        modify = { f, v -> f.copy(password = v) }
    ).apply {
        issues.report(remember {
            val value = value()
            when {
                value.isEmpty() -> Issue("Password required", Severity.ERROR)
                value.length < 8 -> Issue("Password too short", Severity.ERROR)
                else -> null
            }
        })
    }

    val confirmPassword = form.validatedLens(
        issues = IssueNode(parent = issues),
        get = { it.confirmPassword },
        modify = { f, v -> f.copy(confirmPassword = v) }
    ).apply {
        issues.report(remember {
            if (value() != password.value) {
                Issue("Passwords don't match", Severity.ERROR)
            } else {
                null
            }
        })
    }

    val isValid = remember {
        issues.issues().none { it.severity == Severity.ERROR }
    }
}

// Usage
val form = Signal(RegistrationForm("", "", "", ""))
val validator = RegistrationValidator(form)

reactiveScope {
    if (validator.isValid()) {
        submitButton.enabled = true
    } else {
        validator.issues.issues().forEach { issue ->
            showError(issue.message)
        }
    }
}
```

### Collection Validation

```kotlin
data class TodoItem(val title: String, val done: Boolean)

val todos = ReactiveMutableList<TodoItem>()
val todosIssues = IssueNode()

val todoValidators = todos.map { todo ->
    val todoNode = IssueNode(parent = todosIssues)

    todoNode.report(remember {
        if (todo.title.isEmpty()) {
            Issue("Todo title required", Severity.ERROR)
        } else {
            null
        }
    })

    todoNode
}

// Check if all todos are valid
val allValid = remember {
    todosIssues.issues().isEmpty()
}
```

### Conditional Validation

```kotlin
val requiresAddress = Signal(false)
val address = Signal("")

val addressIssues = IssueNode()

addressIssues.report(remember {
    if (requiresAddress() && address().isEmpty()) {
        Issue("Address is required", Severity.ERROR)
    } else {
        null
    }
})

// Toggle requirement
requiresAddress.value = true  // Now validates address
```

### Severity-Based Display

```kotlin
reactiveScope {
    val issues = formIssues.issues()

    val errors = issues.filter { it.severity == Severity.ERROR }
    val warnings = issues.filter { it.severity == Severity.WARNING }
    val infos = issues.filter { it.severity == Severity.INFO }

    errors.forEach { showError(it.message) }
    warnings.forEach { showWarning(it.message) }
    infos.forEach { showInfo(it.message) }
}
```

### Async Validation

```kotlin
val username = Signal("")
val usernameIssues = IssueNode()

usernameIssues.report(rememberSuspending {
    val name = username()
    if (name.isEmpty()) {
        Issue("Username required", Severity.ERROR)
    } else {
        // Check if username is available
        val available = checkUsernameAvailability(name)
        if (!available) {
            Issue("Username taken", Severity.ERROR)
        } else {
            null
        }
    }
})
```

### Validated Draft

Combine validation with Draft for form editing:

```kotlin
data class UserProfile(val name: String, val bio: String)

val published = Signal(UserProfile("John", "Developer"))
val publishedIssues = IssueNode()

val draft = ValidatedDraft(
    published = published,
    issues = publishedIssues
)

draft.issues.report(remember {
    if (draft.value().name.isEmpty()) {
        Issue("Name required", Severity.ERROR)
    } else {
        null
    }
})

// Check if can publish
val canPublish = remember {
    draft.changesMade() &&
    draft.issues.issues().none { it.severity == Severity.ERROR }
}

// Publish if valid
if (canPublish()) {
    launch {
        draft.publish()
    }
} else {
    draft.cancel()
}
```

## Best Practices

1. **Create hierarchies** - Match your data structure
2. **Use specific messages** - Clear, actionable error messages
3. **Separate concerns** - One validation per IssueNode when possible
4. **Handle all severities** - ERROR, WARNING, and INFO appropriately
5. **Test validation** - Ensure rules trigger correctly
6. **Clean up nodes** - Disconnect when no longer needed
7. **Reactive validation** - Use `remember` for dynamic rules

## Testing Validation

```kotlin
@Test
fun testValidation() {
    val issues = IssueNode()
    val value = Signal("")

    issues.report(remember {
        if (value().isEmpty()) {
            Issue("Required", Severity.ERROR)
        } else {
            null
        }
    })

    // Initially has error
    assertEquals(1, issues.issues().size)

    // Fix the error
    value.value = "Valid"
    assertEquals(0, issues.issues().size)
}

@Test
fun testHierarchy() {
    val parent = IssueNode()
    val child = IssueNode(parent = parent)

    child.report(Constant(Issue("Child error", Severity.ERROR)))

    assertEquals(1, parent.issues().size)
    assertEquals(1, child.issues().size)
}
```

## Next Steps

- [Collections](collections.md) - Reactive collections
- [Advanced Topics](advanced-topics.md) - Complex validation patterns
- [Lensing](lensing.md) - Lenses with validation
