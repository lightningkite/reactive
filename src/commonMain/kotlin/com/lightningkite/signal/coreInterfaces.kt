package com.lightningkite.signal

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface ResourceUse {
    fun beginUse(): () -> Unit
}

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
fun Listenable.addAndRunListener(listener: () -> Unit): () -> Unit {
    val remover = addListener(listener)
    listener()
    return remover
}

interface Signal<out T> : Listenable {
    val state: SignalState<T>
    object Never: Signal<Nothing> {
        override val state: SignalState<Nothing> get() = SignalState.notReady
        override fun addListener(listener: () -> Unit): () -> Unit = {}
    }

    companion object Companion {
        // System settings, since this is the name of the project
        var reportException: (Throwable) -> Unit = { it.printStackTrace() }
    }
}

interface Mutable<T> {
    suspend infix fun set(value: T)
}

interface MutableSignal<T> : Signal<T>, Mutable<T>

interface ValueSignal<out T> : Signal<T>, ReadOnlyProperty<Any?, T> {
    val value: T
    override val state: SignalState<T> get() = SignalState(value)
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}

interface ImmediateMutable<T>: Mutable<T> {
    fun setImmediate(value: T)
    override suspend fun set(value: T) { setImmediate(value) }
}

interface ImmediateMutableValueSignal<T> : MutableSignal<T>, ValueSignal<T>
interface ImmediateMutableSignal<T> : MutableSignal<T>, ImmediateMutable<T>

interface MutableValueSignal<T> : ImmediateMutable<T>, ValueSignal<T>, MutableSignal<T>, ReadWriteProperty<Any?, T> {
    override var value: T
    override fun setImmediate(value: T) { this.value = value }
    override suspend fun set(value: T) { this.value = value }
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

class NotReadyException(message: String? = null) : IllegalStateException(message)

