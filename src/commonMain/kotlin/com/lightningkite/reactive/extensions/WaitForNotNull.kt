package com.lightningkite.reactive.extensions

import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.Release

internal open class WaitForNotNull<T : Any>(val wraps: Reactive<T?>) : Reactive<T> {
    // A null value is notReady - a value is there, it just isn't one we can use yet. Every other
    // state, notActive included, passes through as the wrapped reactive reported it.
    @Suppress("UNCHECKED_CAST")
    override val state: ReactiveState<T>
        get() = if(wraps.state.raw == null) ReactiveState.notReady else wraps.state as ReactiveState<T>

    override fun addListener(listener: () -> Unit): Release {
        return wraps.addListener(listener)
    }

    override fun hashCode(): Int = wraps.hashCode() + 1

    override fun equals(other: Any?): Boolean = other is WaitForNotNull<*> && this.wraps == other.wraps
}
// A named class rather than an object expression delegating to WaitForNotNull: dependency tracking
// compares dependencies with equals, and Kotlin's interface delegation doesn't cover equals/hashCode,
// so a delegating object would compare by identity and never be recognized as an existing dependency.
internal class MutableWaitForNotNull<T : Any>(private val source: MutableReactive<T?>) :
    WaitForNotNull<T>(source), MutableReactive<T> {
    override suspend fun set(value: T): Unit = source.set(value)
}

public val <T : Any> Reactive<T?>.waitForNotNull: Reactive<T> get() = WaitForNotNull(this)

public val <T : Any> MutableReactive<T?>.waitForNotNull: MutableReactive<T> get() = MutableWaitForNotNull(this)

public suspend fun <T : Any> Reactive<T?>.awaitNotNull(): T = waitForNotNull.await()