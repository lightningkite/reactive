package com.lightningkite.reactive.extensions

import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.Release
import kotlinx.coroutines.suspendCancellableCoroutine

internal class WaitForNotNull<T : Any>(val wraps: Reactive<T?>) : Reactive<T> {
    @Suppress("UNCHECKED_CAST")
    override val state: ReactiveState<T>
        get() = if(wraps.state.raw == null) ReactiveState.notReady else wraps.state as ReactiveState<T>

    override fun addListener(listener: () -> Unit): Release {
        return wraps.addListener(listener)
    }

    override fun hashCode(): Int = wraps.hashCode() + 1

    override fun equals(other: Any?): Boolean = other is WaitForNotNull<*> && this.wraps == other.wraps
}
val <T : Any> Reactive<T?>.waitForNotNull: Reactive<T> get() = WaitForNotNull(this)

val <T : Any> MutableReactive<T?>.waitForNotNull: MutableReactive<T> get() =
    object : MutableReactive<T>, Reactive<T> by (this as Reactive<T?>).waitForNotNull { // DO NOT REMOVE THE TYPECAST
        override suspend fun set(value: T) = this@waitForNotNull.set(value)
    }

suspend fun <T : Any> Reactive<T?>.awaitNotNull(): T {
    val basis = await()
    return basis ?: suspendCancellableCoroutine<T> {  }
}