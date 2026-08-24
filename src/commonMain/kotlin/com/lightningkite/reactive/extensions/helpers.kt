package com.lightningkite.reactive.extensions

import com.lightningkite.reactive.context.DependencyChangeListener
import com.lightningkite.reactive.context.DependencyTracker
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
import com.lightningkite.reactive.core.Release
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.*
import kotlinx.coroutines.launch
import kotlin.js.JsName
import kotlin.jvm.JvmName

@JsName("invokeAllSafeMutable")
@JvmName("invokeAllSafeMutable")
public fun MutableList<() -> Unit>.invokeAllSafe(): Unit = toList().invokeAllSafe()
public fun List<() -> Unit>.invokeAllSafe(): Unit = forEach {
    try {
        it()
    } catch (e: Exception) {
        if (e is CancellationException) return@forEach
        Reactive.reportException(e)
    }
}

public inline var <T> MutableValue<T>.value: T
    @Deprecated("This is syntax sugar for SETTING values. Retrieving will always throw an exception.", level = DeprecationLevel.ERROR)
    get() = throw IllegalStateException("Attempted to retrieve value for set-only property")
    @JvmName("setValue2")
    set(value) {
        valueSet(value)
    }

public operator fun Listenable.plus(other: Listenable): Listenable = object: Listenable {
    override fun addListener(listener: () -> Unit): Release {
        val a = this@plus.addListener(listener)
        val b = other.addListener(listener)
        return {
            a()
            b()
        }
    }
}

public fun <T> Reactive<T>.withWrite(action: suspend Reactive<T>.(T) -> Unit): MutableReactive<T> =
    object : MutableReactive<T>, Reactive<T> by this {
        override suspend fun set(value: T) {
            action(this@withWrite, value)
        }
    }

public fun <T> Reactive<T>.onNextSuccess(action: (T) -> Unit): Release? {
    // A successful state is one somebody is maintaining, so it needs no subscription at all.
    if (state.success) {
        state.onSuccess(action)
        return null
    }

    var release: Release? = null
    var acted = false
    fun perform(value: T) {
        if (acted) return
        release?.invoke()
        acted = true
        action(value)
    }
    release = addListener {
        state.onSuccess(::perform)
    }
    state.onSuccess(::perform)
    return release
}

public fun <T : Any> MutableReactive<T>.nullable(): MutableReactive<T?> =
    object : MutableReactive<T?>, Reactive<T?> by this {
        override suspend fun set(value: T?) {
            if (value != null) this@nullable.set(value)
        }
    }

public suspend infix fun <T> MutableReactive<T>.modify(action: suspend (T) -> T) {
    set(action(await()))
}

public suspend infix fun <T> MutableReactiveValue<T>.modify(action: suspend (T) -> T) {
    value = action(value)
}

public suspend fun MutableReactive<Boolean>.toggle() { set(!awaitOnce()) }
public fun MutableReactiveValue<Boolean>.toggle() { value = !value }

/**
 * Starts using this [ResourceUse] and tracks it as a dependency in future loops.
 * */
public fun DependencyTracker.use(resourceUse: ResourceUse) {
    if (existingDependency(resourceUse) == null) {
        registerDependency(resourceUse, resourceUse.beginUse())
    }
}

/**
 * Starts using this [ResourceUse], attaching its use to the lifecycle of
 * this [CoroutineScope].
 *
 * If this scope contains a [DependencyChangeListener] then the resource use
 * is attached as a dependency.
 * */
public fun CoroutineScope.use(resourceUse: ResourceUse) {
    coroutineContext[DependencyChangeListener.Key]?.let {
        it.use(resourceUse)
        return
    }
    // If there's no dependency tracker just attach it to the lifetime of the scope.
    resourceUse.beginUse().also(::onRemove)
}

public fun <T, WRITE : MutableReactive<T>> WRITE.interceptWrite(action: suspend WRITE.(T) -> Unit): MutableReactive<T> =
    object : MutableReactive<T>, Reactive<T> by this {
        override suspend fun set(value: T) {
            action(this@interceptWrite, value)
        }
    }

public fun <T> Reactive<Reactive<T>>.flatten(): Reactive<T> = remember { this@flatten()() }

public fun <T> Reactive<MutableReactive<T>>.flatten(): MutableReactive<T> =
    remember { this@flatten()() }.withWrite {
        // awaitOnce rather than reading state: if the outer reactive is lazy it has no value to
        // read unless something is listening, and the write would be silently dropped.
        this@flatten.awaitOnce().set(it)
    }

public fun <T> CoroutineScope.asyncReactive(action: suspend () -> T): Reactive<T> {
    val prop = LateInitSignal<T>()
    launch {
        prop.value = action()
    }
    return prop
}

@OptIn(ExperimentalCoroutinesApi::class)
public fun <T> Deferred<T>.toReactive(): Reactive<T> = object : BaseReactive<T>() {
    init {
        this@toReactive[Job]?.invokeOnCompletion {
            state = if (it == null) ReactiveState(getCompleted()) else ReactiveState.exception(it as? Exception ?: Exception("Must be exception, not throwable", it))
        }
    }
}

public suspend operator fun <R> (ReactiveContext.()->R).invoke(): R {
    return remember { this@invoke() }.awaitOnce()
}
public suspend operator fun <A, R> (ReactiveContext.(A)->R).invoke(a: A): R {
    return remember { this@invoke(a) }.awaitOnce()
}
public suspend operator fun <A, B, R> (ReactiveContext.(A, B)->R).invoke(a: A, b: B): R {
    return remember { this@invoke(a, b) }.awaitOnce()
}
public suspend operator fun <A, B, C, R> (ReactiveContext.(A, B, C)->R).invoke(a: A, b: B, c: C): R {
    return remember { this@invoke(a, b, c) }.awaitOnce()
}