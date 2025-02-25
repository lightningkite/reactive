package com.lightningkite.readable

import kotlinx.coroutines.suspendCancellableCoroutine

internal class WaitForNotNull<T : Any>(val wraps: Readable<T?>) : Readable<T> {

    @Suppress("UNCHECKED_CAST")
    override val state: ReadableState<T>
        get() = if(wraps.state.raw == null) ReadableState.notReady else wraps.state as ReadableState<T>

    override fun addListener(listener: () -> Unit): () -> Unit {
        return wraps.addListener(listener)
    }

    override fun hashCode(): Int = wraps.hashCode() + 1

    override fun equals(other: Any?): Boolean = other is WaitForNotNull<*> && this.wraps == other.wraps
}
val <T : Any> Readable<T?>.waitForNotNull: Readable<T> get() = WaitForNotNull(this)

suspend fun <T : Any> Readable<T?>.awaitNotNull(): T {
    val basis = await()
    if (basis == null) return suspendCancellableCoroutine<T> {  }
    else return basis
}