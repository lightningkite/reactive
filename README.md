# Reactive

[![Maven Central Version](https://img.shields.io/maven-central/v/com.lightningkite/reactive)](https://central.sonatype.com/artifact/com.lightningkite/reactive)
[![Nightly](https://img.shields.io/maven-metadata/v?strategy=latestProperty&label=lightningkite-maven-nightly&metadataUrl=https://lightningkite-maven.s3.us-west-2.amazonaws.com/com/lightningkite/reactive/maven-metadata.xml
)](https://lightningkite-maven.s3.us-west-2.amazonaws.com/com/lightningkite/reactive/maven-metadata.xml)
[![License](https://img.shields.io/github/license/lightningkite/reactive?style=flat-square)](https://www.apache.org/licenses/LICENSE-2.0)
[![CI Status](https://img.shields.io/github/actions/workflow/status/lightningkite/reactive/publishInternal.yml)](https://github.com/lightningkite/reactive/publishInternal.yml)
[![KDoc](https://img.shields.io/badge/docs-kdoc-blue)](https://lightningkite-maven.s3.us-west-2.amazonaws.com/com/lightningkite/reactive/docs/index.html)

![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?logo=kotlin&label=2.2.0)
![Android](https://img.shields.io/badge/platform-android-blue)
![JVM](https://img.shields.io/badge/platform-jvm-blue)
![JS](https://img.shields.io/badge/platform-js-blue)
![iOS](https://img.shields.io/badge/platform-ios-blue)

Kotlin Multiplatform reactivity tools.

## Overview

Reactive is a multiplatform Kotlin library for building reactive applications. It is inspired by concepts from Solid.js,
and provides a set of core abstractions and utilities for managing state, observing changes, and composing reactive data 
flow.

Some of the features of Reactive include:

 - Reactive data observation, management, and processing
 - Built-in support for loading and error states
 - Interoperability with kotlinx-coroutines
 - Reactive data "lensing"
 - Data validation and issue-tracking

The core of Reactive is the `Reactive<T>` interface, which represents a reactive value that can be observed for changes
in state. The state of a `Reactive` not only communicates data, but also loading and error states. `Reactive` data can 
be bound to a `ReactiveContext`, which handles all the observation, loading checks, and resource management for you. 

As of now there isn't any official documentation for Reactive, but a good introduction to Reactive in action can be found
[here](https://kiteui.cs.lightningkite.com/docs/reactive-tools).

## Examples

Simple example of reactive data:

```kotlin
val counter = Signal(0) // Signal is the most basic Reactive value. It's a container that notifies listeners when changed.

AppScope.reactive { 
    // ReactiveContexts are bound to kotlin CoroutineScopes
    // AppScope is a provided top-level scope that lives as long as the app is running.
    
    println("Count is now: ${counter()}") // Reactive data is bound to the scope using the `invoke()` operator.
}

fun count() {
    counter.value = 1 // prints "Count is now: 1"
    counter.value = 2 // prints "Count is now: 2"
}
```

Support for loading states:

```kotlin
val wait = LateInitSignal<String>() // LateInitSignal is like Signal, but starts in a loading state until set

AppScope.reactive {
    println(wait()) // context will stay loading until wait has a ready value
}

fun go() {
    wait.value = "Okay, stop loading now." // prints "Okay, stop loading now."
}
```

Interoperability with kotlinx-coroutines:

```kotlin
val data: Flow<Int> = flow {
    repeat(10) {
        delay(1000)
        emit(it)
    }
}

AppScope.reactive {
    println(data()) // prints "1", "2", ..., "9" delaying one second between each
}
```
