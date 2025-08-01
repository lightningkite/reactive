package com.lightningkite.reactive.extensions

import com.lightningkite.reactive.context.CalculationContext
import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.context.onRemove
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.MutableValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.ResourceUse
import com.lightningkite.reactive.core.BaseReactive
import com.lightningkite.reactive.core.LateInitSignal
import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.*
import kotlinx.coroutines.launch
import kotlin.js.JsName
import kotlin.jvm.JvmName

@JsName("invokeAllSafeMutable")
@JvmName("invokeAllSafeMutable")
fun MutableList<() -> Unit>.invokeAllSafe() = toList().invokeAllSafe()
fun List<() -> Unit>.invokeAllSafe() = forEach {
    try {
        it()
    } catch (e: Exception) {
        if (e is CancellationException) return@forEach
        Reactive.reportException(e)
    }
}

var <T> MutableValue<T>.value: T
    @Deprecated("This is syntax sugar for SETTING values. Retrieving will always throw an exception.", level = DeprecationLevel.ERROR)
    get() = throw IllegalStateException("Attempted to retrieve value for set-only property")
    @JvmName("setValue2")
    set(value) {
        println("Setting outer value: $value")
        valueSet(value)
    }

operator fun Listenable.plus(other: Listenable): Listenable = object: Listenable {
    override fun addListener(listener: () -> Unit): () -> Unit {
        val a = this@plus.addListener(listener)
        val b = other.addListener(listener)
        return {
            a()
            b()
        }
    }
}

fun <T> Reactive<T>.withWrite(action: suspend Reactive<T>.(T) -> Unit): MutableReactive<T> =
    object : MutableReactive<T>, Reactive<T> by this {
        override suspend fun set(value: T) {
            action(this@withWrite, value)
        }
    }

fun <T> Reactive<T>.onNextSuccess(action: (T) -> Unit): (() -> Unit)? {
    if (state.success) {
        state.onSuccess(action)
        return null
    }

    var remover: (() -> Unit)? = null
    remover = addListener {
        state.onSuccess {
            action(it)
            remover?.invoke()
        }
    }
    return remover
}

fun <T : Any> MutableReactive<T>.nullable(): MutableReactive<T?> =
    object : MutableReactive<T?>, Reactive<T?> by this {
        override suspend fun set(value: T?) {
            if (value != null) this@nullable.set(value)
        }
    }

suspend infix fun <T> MutableReactive<T>.modify(action: suspend (T) -> T) {
    set(action(await()))
}

suspend infix fun <T> MutableReactiveValue<T>.modify(action: suspend (T) -> T) {
    value = action(value)
}

suspend fun MutableReactive<Boolean>.toggle() { set(!awaitOnce()) }
fun MutableReactiveValue<Boolean>.toggle() { value = !value }

fun CalculationContext.use(resourceUse: ResourceUse) {
    val x = resourceUse.beginUse()
    onRemove { x() }
}

fun <T, WRITE : MutableReactive<T>> WRITE.interceptWrite(action: suspend WRITE.(T) -> Unit): MutableReactive<T> =
    object : MutableReactive<T>, Reactive<T> by this {
        override suspend fun set(value: T) {
            action(this@interceptWrite, value)
        }
    }

fun <T> Reactive<Reactive<T>>.flatten(): Reactive<T> = remember { this@flatten()() }

fun <T> Reactive<MutableReactive<T>>.flatten(): MutableReactive<T> =
    remember { this@flatten()() }.withWrite {
        this@flatten.state.onSuccess { s -> s set it }
    }

fun <T> CoroutineScope.asyncReactive(action: suspend () -> T): Reactive<T> {
    val prop = LateInitSignal<T>()
    launch {
        prop.value = action()
    }
    return prop
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Deferred<T>.toReactive() = object : BaseReactive<T>() {
    init {
        this@toReactive[Job]?.invokeOnCompletion {
            state = if (it == null) ReactiveState(getCompleted()) else ReactiveState.exception(it as? Exception ?: Exception("Must be exception, not throwable", it))
        }
    }
}

suspend operator fun <R> (ReactiveContext.()->R).invoke(): R {
    return remember { this@invoke() }.awaitOnce()
}
suspend operator fun <A, R> (ReactiveContext.(A)->R).invoke(a: A): R {
    return remember { this@invoke(a) }.awaitOnce()
}
suspend operator fun <A, B, R> (ReactiveContext.(A, B)->R).invoke(a: A, b: B): R {
    return remember { this@invoke(a, b) }.awaitOnce()
}
suspend operator fun <A, B, C, R> (ReactiveContext.(A, B, C)->R).invoke(a: A, b: B, c: C): R {
    return remember { this@invoke(a, b, c) }.awaitOnce()
}