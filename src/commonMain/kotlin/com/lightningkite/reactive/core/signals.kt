package com.lightningkite.reactive.core

import kotlin.random.Random

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
 * [Signal] is guaranteed to always have a ready value.
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
 * A reactive value that always holds a constant value and does not notify listeners.
 */
class Constant<T>(override val value: T) : ReactiveValue<T> {
    companion object {
        private val NOOP = {}
    }

    override fun addListener(listener: () -> Unit): () -> Unit = NOOP
}
