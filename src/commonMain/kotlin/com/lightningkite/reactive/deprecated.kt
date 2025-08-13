package com.lightningkite.readable

import com.lightningkite.reactive.context.CalculationContext
import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.onRemove
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.BaseReactive
import com.lightningkite.reactive.core.BaseReactiveValue
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Emitter
import com.lightningkite.reactive.core.InternalReactiveWrapper
import com.lightningkite.reactive.core.LateInitSignal
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.MutableRemember
import com.lightningkite.reactive.core.MutableValue
import com.lightningkite.reactive.core.MutableWithReactiveValue
import com.lightningkite.reactive.core.RawReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.ReactiveValue
import com.lightningkite.reactive.core.ReactiveWithMutableValue
import com.lightningkite.reactive.core.Remember
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.reactiveProcess
import com.lightningkite.reactive.core.reactiveState
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.toReactiveState
import com.lightningkite.reactive.extensions.DebounceReactive
import com.lightningkite.reactive.extensions.debounce
import com.lightningkite.reactive.extensions.modify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmName
import kotlin.time.Duration


@Deprecated("Only exists to not break imports", level = DeprecationLevel.ERROR)
fun <T> Nothing.bind(): Nothing = TODO()

// Naming deprecations

@Deprecated("Use Reactive", ReplaceWith("Reactive", "com.lightningkite.reactive.core"))
typealias Readable<T> = Reactive<T>

@Deprecated("Use MutableReactive", ReplaceWith("MutableReactive", "com.lightningkite.reactive.core"))
typealias Writable<T> = MutableReactive<T>

@Deprecated("Use Remember", ReplaceWith("Remember", "com.lightningkite.reactive.core"))
typealias SharedReadable<T> = Remember<T>

@Deprecated("Use ReactiveValue", ReplaceWith("ReactiveValue", "com.lightningkite.reactive.core"))
typealias ImmediateReadable<T> = ReactiveValue<T>

@Deprecated("Use MutableReactiveValue", ReplaceWith("MutableReactiveValue", "com.lightningkite.reactive.core"))
typealias ImmediateWritable<T> = MutableReactiveValue<T>

@Deprecated("Use ReactiveWithMutableValue", ReplaceWith("ReactiveWithMutableValue", "com.lightningkite.reactive.core"))
typealias ReadableWithImmediateWrite<T> = ReactiveWithMutableValue<T>

@Deprecated("Use MutableWithReactiveValue", ReplaceWith("MutableWithReactiveValue", "com.lightningkite.reactive.core"))
typealias ImmediateReadableWithWrite<T> = MutableWithReactiveValue<T>

@Deprecated("Use Signal", ReplaceWith("Signal", "com.lightningkite.reactive.core"))
typealias Property<T> = Signal<T>

@Deprecated("Use MutableRemember", ReplaceWith("MutableRemember", "com.lightningkite.reactive.core"))
typealias LazyProperty<T> = MutableRemember<T>

@Deprecated("Use DebounceReactive", ReplaceWith("DebounceReactive", "com.lightningkite.reactive.extensions"))
typealias DebounceReadable<T> = DebounceReactive<T>

@Deprecated("Use InternalSignalWrapper", ReplaceWith("InternalSignalWrapper", "com.lightningkite.reactive.core"))
typealias InternalReadableWrapper<T> = InternalReactiveWrapper<T>

@Deprecated("Use RawReactive", ReplaceWith("RawReactive", "com.lightningkite.reactive.core"))
typealias RawReadable<T> = RawReactive<T>

@Deprecated("Use ReactiveState", ReplaceWith("ReactiveState", "com.lightningkite.reactive.core"))
typealias ReadableState<T> = ReactiveState<T>

@Deprecated("Use SignalEmitter", ReplaceWith("SignalEmitter", "com.lightningkite.reactive.core"))
typealias ReadableEmitter<T> = Emitter<T>

@Deprecated("Use MutableValue", ReplaceWith("MutableValue", "com.lightningkite.reactive.core"))
typealias ImmediateWriteOnly<T> = MutableValue<T>

@Deprecated("Use BaseReactiveValue", ReplaceWith("BaseReactiveValue", "com.lightningkite.reactive.core"))
typealias BaseImmediateReadable<T> = BaseReactiveValue<T>

@Deprecated("Use BaseReactive", ReplaceWith("BaseReactive", "com.lightningkite.reactive.core"))
typealias BaseReadable<T> = BaseReactive<T>

@Deprecated("Use BaseReactive", ReplaceWith("BaseReactive", "com.lightningkite.reactive.core"))
typealias BaseWritable<T> = BaseReactive<T>

@Deprecated("Use BaseReactiveValue", ReplaceWith("BaseReactiveValue", "com.lightningkite.reactive.core"))
typealias BaseReadWrite<T> = BaseReactiveValue<T>

@Deprecated("Use LateInitReactiveValue", ReplaceWith("LateInitReactiveValue", "com.lightningkite.reactive.core"))
typealias LateInitProperty<T> = LateInitSignal<T>

@Deprecated("Use remember", ReplaceWith("remember", "com.lightningkite.reactive.core"))
fun <T> shared(coroutineContext: CoroutineContext = Dispatchers.Unconfined, useLastWhileLoading: Boolean = false, action: ReactiveContext.() -> T): Reactive<T> =
    remember(coroutineContext, useLastWhileLoading, null, action)

@Deprecated("Use reactiveProcess", ReplaceWith("reactiveProcess", "com.lightningkite.reactive.core"))
fun <T> sharedProcess(scope: CoroutineScope = AppScope, emitter: suspend Emitter<T>.() -> Unit): Reactive<T> = reactiveProcess(scope, emitter)

@Deprecated("Use reactiveProcess", ReplaceWith("reactiveProcess", "com.lightningkite.reactive.core"))
@JvmName("sharedProcessReceiving")
fun <T> CoroutineScope.sharedProcess(emitter: suspend Emitter<T>.() -> Unit): Reactive<T> = reactiveProcess(emitter)

@Deprecated("Use reactiveState", ReplaceWith("reactiveState", "com.lightningkite.reactive.core"))
inline fun <T> readableState(action: () -> T): ReactiveState<T> = reactiveState(action)

@Deprecated("Use toReactiveState", ReplaceWith("toReactiveState", "com.lightningkite.reactive.core"))
inline fun <T> Result<T>.toReadableState(): ReactiveState<T> = toReactiveState()