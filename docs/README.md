# Reactive Documentation

Welcome to the **Reactive** library documentation! Reactive is a Kotlin Multiplatform library for building reactive applications, inspired by concepts from Solid.js.

## Table of Contents

1. [Getting Started](getting-started.md) - Installation, basic concepts, and your first reactive application
2. [Core Concepts](core-concepts.md) - Understanding reactivity, states, and signals
3. [Reactive Context](reactive-context.md) - Dependency tracking and reactive computations
4. [Remember and Memoization](remember.md) - Shared reactive calculations
5. [Lensing](lensing.md) - Transforming and focusing on reactive data
6. [Validation](validation.md) - Hierarchical validation system
7. [Collections](collections.md) - Reactive lists, maps, and sets
8. [Advanced Topics](advanced-topics.md) - Suspending operations, performance optimization, and best practices
9. [API Reference](api-reference.md) - Complete API documentation
10. [Migration Guide](migration-guide.md) - Upgrading between versions

## Quick Links

- **[Installation](getting-started.md#installation)** - Add Reactive to your project
- **[Basic Example](getting-started.md#basic-example)** - See Reactive in action
- **[Common Patterns](advanced-topics.md#common-patterns)** - Frequently used patterns and idioms
- **[Troubleshooting](advanced-topics.md#troubleshooting)** - Common issues and solutions

## Platform Support

Reactive supports the following platforms:

- ✅ JVM (Java 8+)
- ✅ JavaScript (Browser and Node.js)
- ✅ iOS (x64, arm64, simulator arm64)
- ✅ Android

## Key Features

- **Automatic Dependency Tracking** - Reactive computations automatically track their dependencies and re-run when they change
- **Loading and Error States** - Built-in support for asynchronous operations with loading and error handling
- **Lazy Evaluation** - Computations only run when they have active listeners
- **Shared Calculations** - Multiple listeners automatically share the same computation
- **Lensing** - Transform and focus on specific parts of reactive data
- **Validation** - Hierarchical validation with automatic issue propagation
- **Coroutine Integration** - Seamless integration with kotlinx-coroutines

## Contributing

Issues and pull requests are welcome! Please see the main repository for contribution guidelines.

## License

Apache License 2.0
