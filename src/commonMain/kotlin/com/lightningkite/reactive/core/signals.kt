package com.lightningkite.reactive.core

import kotlin.random.Random
import com.lightningkite.reactive.context.ReactiveContext
import kotlin.jvm.JvmInline

/**
 * A reactive value that exposes its state and allows direct mutation.
 * Used for low-level reactive state management.
 */
class RawReactive<T>(start: ReactiveState<T> = ReactiveState.notReady) : BaseReactive<T>(start) {
    override var state: ReactiveState<T>
        get() = super.state
        public set(value) { super.state = value }
}

/**
 * A basic implementation of a listenable object.
 * Can invoke all listeners and provides a unique identifier for debugging.
 */
class BasicListenable : BaseListenable() {
    private var id = Random.nextInt(0, 100000)
    override fun toString(): String {
        return "BasicListenable($id)"
    }

    fun invokeAll() {
        super.invokeAllListeners()
    }
}

/**
 * A mutable reactive value that can be updated and observed.
 *
 * This is the most basic entrypoint into the Reactive ecosystem. [Signal] is simply a container that notifies it's listeners when it is changed.
 * Listeners are typically added in a [ReactiveContext], which a context that registers [Reactive] dependencies and reruns when any dependency is changed.
 *
 * Example:
 * ```kotlin
 * val number = Signal(0)
 *
 * reactive {
 *    println("Number: ${number()}") // prints "Number: 0"
 * }
 *
 * number.value = 1 // prints "Number: 1"
 * number.value = 2 // prints "Number: 2"
 * ```
 */
class Signal<T>(startValue: T) : MutableReactiveValue<T>, BaseReactiveValue<T>(startValue)

/**
 * A reactive value that can be set after initialization and unset to a not-ready state.
 * Useful for cases where the value is not available at construction time.
 */
class LateInitSignal<T>() : ReactiveWithMutableValue<T>, BaseReactive<T>() {
    override fun valueSet(value: T) {
        state = ReactiveState(value)
    }

    fun unset() {
        state = ReactiveState.notReady
    }
}

/**
 * A wrapper around a `value` to coerce it into a [ReactiveValue] that never changes.
 * This will never notify listeners, because it never changes.
 *
 * Useful for passing constant parameters where reactive ones are expected with
 * no overhead.
 */
@JvmInline
value class Constant<T>(override val value: T) : ReactiveValue<T> {
    companion object {
        private val NOOP = {}
    }

    override fun addListener(listener: () -> Unit): () -> Unit = NOOP
}
