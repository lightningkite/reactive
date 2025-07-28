package com.lightningkite.signal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

@Deprecated("Only exists to not break imports", level = DeprecationLevel.ERROR)
fun <T> Nothing.bind(): Nothing = TODO()

// Naming deprecations

@Deprecated("Use Reactive", ReplaceWith("Reactive", "com.lightningkite.signal"))
typealias Readable<T> = Reactive<T>

@Deprecated("Use MutableReactive", ReplaceWith("MutableReactive", "com.lightningkite.signal"))
typealias Writable<T> = MutableReactive<T>

@Deprecated("Use Remember", ReplaceWith("Remember", "com.lightningkite.signal"))
typealias SharedReadable<T> = Remember<T>

@Deprecated("Use ReactiveValue", ReplaceWith("ReactiveValue", "com.lightningkite.signal"))
typealias ImmediateReadable<T> = ReactiveValue<T>

@Deprecated("Use MutableReactiveValue", ReplaceWith("MutableReactiveValue", "com.lightningkite.signal"))
typealias ImmediateWritable<T> = MutableReactiveValue<T>

@Deprecated("Use MutableReactiveValue", ReplaceWith("MutableReactiveValue", "com.lightningkite.signal"))
typealias Property<T> = MutableReactiveValue<T>

@Deprecated("Use MutableRemember", ReplaceWith("MutableRemember", "com.lightningkite.signal"))
typealias LazyProperty<T> = MutableRemember<T>

@Deprecated("Use DebounceReactive", ReplaceWith("DebounceReactive", "com.lightningkite.signal"))
typealias DebounceReadable<T> = DebounceReactive<T>

@Deprecated("Use InternalSignalWrapper", ReplaceWith("InternalSignalWrapper", "com.lightningkite.signal"))
typealias InternalReadableWrapper<T> = InternalReactiveWrapper<T>

@Deprecated("Use RawReactive", ReplaceWith("RawReactive", "com.lightningkite.signal"))
typealias RawReadable<T> = RawReactive<T>

@Deprecated("Use ReactiveState", ReplaceWith("ReactiveState", "com.lightningkite.signal"))
typealias ReadableState<T> = ReactiveState<T>

@Deprecated("Use SignalEmitter", ReplaceWith("SignalEmitter", "com.lightningkite.signal"))
typealias ReadableEmitter<T> = Emitter<T>

@Deprecated("Use MutableValue", ReplaceWith("MutableValue", "com.lightningkite.signal"))
typealias ImmediateWriteOnly<T> = MutableValue<T>

@Deprecated("Use BaseReactiveValue", ReplaceWith("BaseReactiveValue", "com.lightningkite.signal"))
typealias BaseImmediateReadable<T> = BaseReactiveValue<T>

@Deprecated("Use BaseReactive", ReplaceWith("BaseReactive", "com.lightningkite.signal"))
typealias BaseReadable<T> = BaseReactive<T>

@Deprecated("Use BaseReactive", ReplaceWith("BaseReactive", "com.lightningkite.signal"))
typealias BaseWritable<T> = BaseReactive<T>

@Deprecated("Use BaseReactiveValue", ReplaceWith("BaseReactiveValue", "com.lightningkite.signal"))
typealias BaseReadWrite<T> = BaseReactiveValue<T>

@Deprecated("Use LateInitReactiveValue", ReplaceWith("LateInitReactiveValue", "com.lightningkite.signal"))
typealias LateInitProperty<T> = LateInitSignal<T>

@Deprecated("Use remember", ReplaceWith("remember", "com.lightningkite.signal"))
fun <T> shared(coroutineContext: CoroutineContext = Dispatchers.Unconfined, useLastWhileLoading: Boolean = false, action: ReactiveContext.() -> T): Reactive<T> = remember(coroutineContext, useLastWhileLoading, action)

@Deprecated("Use rememberProcess", ReplaceWith("rememberProcess", "com.lightningkite.signal"))
fun <T> sharedProcess(scope: CoroutineScope = AppScope, emitter: suspend Emitter<T>.() -> Unit): Reactive<T> = reactiveProcess(scope, emitter)

@Deprecated("Use reactiveState", ReplaceWith("reactiveState", "com.lightningkite.signal"))
inline fun <T> readableState(action: () -> T): ReactiveState<T> = reactiveState(action)

@Deprecated("Use toReactiveState", ReplaceWith("toReactiveState", "com.lightningkite.signal"))
inline fun <T> Result<T>.toReadableState(): ReactiveState<T> = toReactiveState()
