package com.lightningkite.signal

import kotlinx.coroutines.suspendCancellableCoroutine

internal class WaitForNotNull<T : Any>(val wraps: Signal<T?>) : Signal<T> {

    @Suppress("UNCHECKED_CAST")
    override val state: SignalState<T>
        get() = if(wraps.state.raw == null) SignalState.notReady else wraps.state as SignalState<T>

    override fun addListener(listener: () -> Unit): () -> Unit {
        return wraps.addListener(listener)
    }

    override fun hashCode(): Int = wraps.hashCode() + 1

    override fun equals(other: Any?): Boolean = other is WaitForNotNull<*> && this.wraps == other.wraps
}
val <T : Any> Signal<T?>.waitForNotNull: Signal<T> get() = WaitForNotNull(this)

suspend fun <T : Any> Signal<T?>.awaitNotNull(): T {
    val basis = await()
    return if (basis == null) suspendCancellableCoroutine<T> {  }
    else basis
}