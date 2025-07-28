package com.lightningkite.reactive

import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import kotlinx.coroutines.suspendCancellableCoroutine

internal class WaitForNotNull<T : Any>(val wraps: Reactive<T?>) : Reactive<T> {

    @Suppress("UNCHECKED_CAST")
    override val state: ReactiveState<T>
        get() = if(wraps.state.raw == null) ReactiveState.notReady else wraps.state as ReactiveState<T>

    override fun addListener(listener: () -> Unit): () -> Unit {
        return wraps.addListener(listener)
    }

    override fun hashCode(): Int = wraps.hashCode() + 1

    override fun equals(other: Any?): Boolean = other is WaitForNotNull<*> && this.wraps == other.wraps
}
val <T : Any> Reactive<T?>.waitForNotNull: Reactive<T> get() = WaitForNotNull(this)

suspend fun <T : Any> Reactive<T?>.awaitNotNull(): T {
    val basis = await()
    return if (basis == null) suspendCancellableCoroutine<T> {  }
    else basis
}