package com.lightningkite.signal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

@Deprecated("Use new name: Signal", ReplaceWith("Signal", "com.lightningkite.signal"))
typealias Readable<T> = Signal<T>

@Deprecated("Use new name: MutableSignal", ReplaceWith("MutableSignal", "com.lightningkite.signal"))
typealias Writable<T> = MutableSignal<T>

@Deprecated("Use new name: SharedReadable", ReplaceWith("RememberSignal", "com.lightningkite.signal"))
typealias SharedReadable<T> = RememberSignal<T>

@Deprecated("Use new name: ValueSignal", ReplaceWith("ValueSignal", "com.lightningkite.signal"))
typealias ImmediateReadable<T> = ValueSignal<T>

@Deprecated("Use new name: MutableValueSignal", ReplaceWith("MutableValueSignal", "com.lightningkite.signal"))
typealias ImmediateWritable<T> = MutableValueSignal<T>

@Deprecated("Use new name: BasicSignal", ReplaceWith("BasicSignal", "com.lightningkite.signal"))
typealias Property<T> = BasicSignal<T>

@Deprecated("Use new name: RememberBasicSignal", ReplaceWith("RememberBasicSignal", "com.lightningkite.signal"))
typealias LazyProperty<T> = RememberBasicSignal<T>

@Deprecated("Use new name: DebounceSignal", ReplaceWith("DebounceSignal", "com.lightningkite.signal"))
typealias DebounceReadable<T> = DebounceSignal<T>

@Deprecated("Use new name: InternalSignalWrapper", ReplaceWith("InternalSignalWrapper", "com.lightningkite.signal"))
typealias InternalReadableWrapper<T> = InternalSignalWrapper<T>

@Deprecated("Use new name: RawSignal", ReplaceWith("RawSignal", "com.lightningkite.signal"))
typealias RawReadable<T> = RawSignal<T>

@Deprecated("Use new name: SignalState", ReplaceWith("SignalState", "com.lightningkite.signal"))
typealias ReadableState<T> = SignalState<T>

@Deprecated("Use new name: SignalEmitter", ReplaceWith("SignalEmitter", "com.lightningkite.signal"))
typealias ReadableEmitter<T> = SignalEmitter<T>

@Deprecated("Use new name: ImmediateMutable", ReplaceWith("ImmediateMutable", "com.lightningkite.signal"))
typealias ImmediateWriteOnly<T> = ImmediateMutable<T>

@Deprecated("Use new name: BaseValueSignal", ReplaceWith("BaseValueSignal", "com.lightningkite.signal"))
typealias BaseImmediateReadable<T> = BaseValueSignal<T>

@Deprecated("Use new name: BaseSignal", ReplaceWith("BaseSignal", "com.lightningkite.signal"))
typealias BaseReadable<T> = BaseSignal<T>

@Deprecated("Use new name: BaseSignal", ReplaceWith("BaseSignal", "com.lightningkite.signal"))
typealias BaseWritable<T> = BaseSignal<T>

@Deprecated("Use new name: BaseValueSignal", ReplaceWith("BaseValueSignal", "com.lightningkite.signal"))
typealias BaseReadWrite<T> = BaseValueSignal<T>

@Deprecated("Use new name: LateInitSignal", ReplaceWith("LateInitSignal", "com.lightningkite.signal"))
typealias LateInitProperty<T> = LateInitSignal<T>

@Deprecated("Use new name: remember", ReplaceWith("remember", "com.lightningkite.signal"))
fun <T> shared(coroutineContext: CoroutineContext = Dispatchers.Unconfined, useLastWhileLoading: Boolean = false, action: ReactiveContext.() -> T): Signal<T> = remember(coroutineContext, useLastWhileLoading, action)

@Deprecated("User new name: rememberProcess", ReplaceWith("rememberProcess", "com.lightningkite.signal"))
fun <T> sharedProcess(scope: CoroutineScope = AppScope, emitter: suspend SignalEmitter<T>.() -> Unit): Signal<T> = rememberProcess(scope, emitter)

@Deprecated("Use new name: signalState", ReplaceWith("signalState", "com.lightningkite.signal"))
inline fun <T> readableState(action: () -> T): SignalState<T> = signalState(action)

@Deprecated("Use new name: toSignalState", ReplaceWith("toSignalState", "com.lightningkite.signal"))
inline fun <T> Result<T>.toReadableState(): SignalState<T> = toSignalState()

