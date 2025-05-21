package com.lightningkite.readable

import com.lightningkite.signal.*

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