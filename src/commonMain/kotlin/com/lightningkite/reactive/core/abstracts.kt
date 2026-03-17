package com.lightningkite.reactive.core

import kotlin.coroutines.cancellation.CancellationException

/**
 * Base implementation of [Listenable] for managing listeners and activation lifecycle.
 *
 * - Handles adding and removing listeners, activating when the first listener is added, and deactivating when the last is removed.
 * - Use [invokeAllListeners] to notify all listeners of a change.
 * - Subclasses can override [activate] and [deactivate] to manage resources or start/stop calculations.
 *
 * ### Lifecycle Methods
 *
 * - [activate]: Called when the first listener is added. Override to start calculations, resource usage, or subscriptions.
 * - [deactivate]: Called when the last listener is removed. Override to stop calculations, release resources, or unsubscribe.
 *
 * Example usage:
 * ```kotlin
 * class MyListenable : BaseListenable() {
 *     override fun activate() { /* start something */ }
 *     override fun deactivate() { /* stop something */ }
 * }
 * ```
 */
abstract class BaseListenable : Listenable {
    /**
     * Called when the first listener is added.
     * Override to start calculations, resource usage, or subscriptions.
     */
    protected open fun activate() {}

    /**
     * Called when the last listener is removed.
     * Override to stop calculations, release resources, or unsubscribe.
     */
    protected open fun deactivate() {}

    private val listeners = ArrayList<() -> Unit>()
    override fun addListener(listener: () -> Unit): Release {
        if (listeners.isEmpty()) activate()
        listeners.add(listener)
        return {
            val pos = listeners.indexOfFirst { it === listener }
            if (pos != -1) {
                listeners.removeAt(pos)
                if (listeners.isEmpty()) deactivate()
            }
        }
    }

    protected fun invokeAllListeners() {
        listeners.toList().forEach {
            try {
                it()
            } catch (e: Exception) {
                if (e is CancellationException) return@forEach
                Reactive.reportException(e)
            }
        }
    }
}

/**
 * Base implementation of [Reactive] for managing reactive state and listener notification.
 *
 * - Holds a [ReactiveState] value and notifies listeners when the state changes.
 * - Only notifies listeners if the new state is different from the previous state.
 * - Subclasses can set the state using the protected setter.
 *
 * @see BaseListenable
 */
abstract class BaseReactive<T>(start: ReactiveState<T> = ReactiveState.notReady) : Reactive<T>, BaseListenable() {
    override var state: ReactiveState<T> = start
        protected set(value) {
            if (field.raw !== value.raw && field != value) {
                field = value
                invokeAllListeners()
            }
        }
}

/**
 * Base implementation of [ReactiveValue] for managing a mutable value and listener notification.
 *
 * - Holds a value and notifies listeners when the value changes.
 * - Only notifies listeners if the new value is different from the previous value.
 * - Subclasses can set the value using the setter.
 *
 * @see BaseListenable
 */
abstract class BaseReactiveValue<T>(start: T) : ReactiveValue<T>, BaseListenable() {
    override var value: T = start
        set(value) {
            @Suppress("SuspiciousEqualsCombination")
            if (field !== value && field != value) {
                field = value
                invokeAllListeners()
            }
        }
}