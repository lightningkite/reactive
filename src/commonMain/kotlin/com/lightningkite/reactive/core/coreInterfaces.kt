package com.lightningkite.reactive.core

import com.lightningkite.reactive.lensing.ModifyLens
import com.lightningkite.reactive.lensing.ModifyValueLens
import com.lightningkite.reactive.lensing.SetLens
import com.lightningkite.reactive.lensing.SetValueLens
import com.lightningkite.reactive.context.ReactiveContext
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

typealias Release = () -> Unit

/**
 * Represents a resource that can be used and released.
 * Implementations should provide logic for starting and stopping resource usage.
 *
 * @see Listenable
 */
interface ResourceUse {
    /**
     * Begins using the resource. Returns a function to stop using the resource.
     */
    fun beginUse(): () -> Unit
}

/**
 * Represents an object that can have listeners attached for change events.
 *
 * - Listeners are notified when the event fires.
 * - Use [addListener] to register a listener and receive a function to remove it.
 *
 * @see ResourceUse
 */
interface Listenable : ResourceUse {
    /**
     * Adds the [listener] to be called every time this event fires.
     * @return a function to remove the [listener] that was added.  Removing multiple times should not cause issues.
     */
    fun addListener(listener: () -> Unit): () -> Unit
    override fun beginUse(): () -> Unit = addListener { }

    object Never: Listenable {
        override fun addListener(listener: () -> Unit): () -> Unit = {}
    }
}

/**
 * Adds a listener and immediately runs it once.
 * @return a function to remove the listener.
 */
fun Listenable.addAndRunListener(listener: () -> Unit): () -> Unit {
    val remover = addListener(listener)
    listener()
    return remover
}

/**
 * Represents a reactive value that can be observed for changes.
 *
 * - Holds a [ReactiveState], which can represent not only ready values, but also **loading states** and **error states**.
 * - Values can be retrieved asynchronously and reactively via `Reactive.invoke()` when in a [ReactiveContext].
 * - Listeners are notified only when the [state] changes; repeated or identical states do not trigger notifications.
 *
 * This interface is central to the reactive system, allowing you to observe and respond to changes in state, including asynchronous or error-prone data sources.
 *
 * @see Listenable
 * @see ReactiveState
 * @see com.lightningkite.reactive.context.ReactiveContext
 */
interface Reactive<out T> : Listenable {
    val state: ReactiveState<T>
    object Never: Reactive<Nothing> {
        override val state: ReactiveState<Nothing> get() = ReactiveState.Companion.notReady
        override fun addListener(listener: () -> Unit): () -> Unit = {}
    }

    companion object Companion {
        /**
         * Used to report exceptions thrown in listeners or reactive calculations.
         */
        var reportException: (Throwable) -> Unit = { it.printStackTrace() }
    }
}

/**
 * Represents a mutable value that can be set asynchronously.
 */
interface Mutable<T> {
    suspend infix fun set(value: T)
}

/**
 * Represents a reactive value that can be modified and observed for changes.
 *
 * - Combines the features of [Reactive] (observation) and [Mutable] (modification).
 * - Allows asynchronous updates via [set], which triggers notifications to listeners if the value changes.
 * - Listeners are notified only when the underlying state changes, not for repeated or identical values.
 *
 * @see Reactive
 * @see Mutable
 */
interface MutableReactive<T> : Reactive<T>, Mutable<T> {

    /**
     * 'Lenses' a new type from this [MutableReactive]. This is useful when translating one
     * type to another for user input, or safe type coercion.
     *
     * - The lens allows you to observe and modify a derived value of type [L] from the original value [T].
     * - Changes to the lens are propagated back to the original value using the [set] function.
     * - Useful for focusing on a specific property or transformation of the reactive value.
     * - Supertypes of [MutableReactive] typically overload [lens] to return the supertype.
     *
     * @param get Function to extract the sub-value from the original value.
     * @param set Function to produce a new original value from the sub-value.
     * @return A [MutableReactive] lens for the sub-value.
     *
     * Example:
     * ```kotlin
     * val int: MutableReactive<Int> = // snip
     * val double: MutableReactive<Double> = int.lens(
     *    get = { it.toDouble() },
     *    set = { it.toInt() }
     * )
     * ```
     */
    fun <L> lens(
        get: (T) -> L,
        set: (L) -> T
    ): MutableReactive<L> = SetLens(this, get, set)

    /**
     * 'Lenses' a subtype from this [MutableReactive]. This is useful for extracting properties from a data class
     * or modifying a single item in a collection.
     *
     * - The lens allows you to observe and modify a derived value of type [L] from the original value [T].
     * - Changes to the lens are propagated back to the original value using the [modify] function.
     * - Useful for focusing on a specific property or transformation of the reactive value, where modification depends on both the original and new value.
     * - Supertypes of [MutableReactive] typically overload [lens] to return the supertype.
     *
     * @param get Function to extract the sub-value from the original value.
     * @param modify Function to produce a new original value from the original and sub-value.
     * @return A [MutableReactive] lens for the sub-value.
     *
     * Example:
     * ```kotlin
     * data class Point(val x: Double, val y: Double)
     *
     * val point: MutableReactive<Point> = // snip
     * val x: MutableReactive<Double> = list.lens(
     *    get = { it.x },
     *    modify = { point, newX -> point.copy(x = newX) }
     * )
     * ```
     */
    fun <L> lens(
        get: (T) -> L,
        modify: (T, L) -> T
    ): MutableReactive<L> = ModifyLens(this, get, modify)
}

/**
 * Represents an infallible reactive value.
 *
 * This differs from [Reactive] in that the value can never be loading or in an error state.
 * [ReactiveValue] guarantees that values are immediately accessible and without any issues.
 *
 * - [value] is the current value.
 * - Supports Kotlin property delegation via [getValue].
 *
 * @see Reactive
 */
interface ReactiveValue<out T> : Reactive<T>, ReadOnlyProperty<Any?, T> {
    val value: T
    override val state: ReactiveState<T> get() = ReactiveState(value)
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}

/**
 * Represents a mutable value that can be set synchronously and/or asynchronously.
 *
 * - [valueSet] sets the value synchronously.
 * - [set] sets the value asynchronously (typically just calls [valueSet]).
 *
 * @see Mutable
 */
interface MutableValue<T>: Mutable<T> {
    infix fun valueSet(value: T)
    override suspend fun set(value: T) { valueSet(value) }
}

/**
 * A [ReactiveValue] that can be asynchronously modified.
 *
 * Combines [MutableReactive] and [ReactiveValue].
 */
interface MutableWithReactiveValue<T> : MutableReactive<T>, ReactiveValue<T>

/**
 * A [Reactive] that can be synchronously modified.
 *
 * Combines [MutableReactive] and [MutableValue].
 */
interface ReactiveWithMutableValue<T> : MutableReactive<T>, MutableValue<T>

/**
 * Represents a mutable reactive value that can be modified and observed for changes.
 *
 * This differs from [MutableReactive] in that modifying and reading values can be
 * done synchronously and without error. [MutableReactiveValue] guarantees that values are both
 * immediately accessible and modifiable, without any error states.
 *
 * @see MutableValue
 * @see ReactiveValue
 * @see MutableReactive
 * @see MutableWithReactiveValue
 * @see ReactiveWithMutableValue
 */
interface MutableReactiveValue<T> : MutableValue<T>, ReactiveValue<T>,
    // Interfaces below are just for typing convenience, they are already implemented by intersection of MutableSignal and ValueSignal
    MutableReactive<T>,
    MutableWithReactiveValue<T>,
    ReactiveWithMutableValue<T>,
    ReadWriteProperty<Any?, T>
{
    override var value: T

    override fun valueSet(value: T) { this.value = value }
    override suspend fun set(value: T) { this.value = value }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    override fun <L> lens(
        get: (T) -> L,
        set: (L) -> T
    ): MutableReactiveValue<L> = SetValueLens(this, get, set)

    override fun <L> lens(
        get: (T) -> L,
        modify: (T, L) -> T
    ): MutableReactiveValue<L> = ModifyValueLens(this, get, modify)
}