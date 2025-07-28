package com.lightningkite.reactive

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

interface Reactive<out T> : Listenable {
    val state: ReactiveState<T>
    object Never: Reactive<Nothing> {
        override val state: ReactiveState<Nothing> get() = ReactiveState.notReady
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

interface MutableReactive<T> : Reactive<T>, Mutable<T>

interface ReactiveValue<out T> : Reactive<T>, ReadOnlyProperty<Any?, T> {
    val value: T
    override val state: ReactiveState<T> get() = ReactiveState(value)
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}

interface MutableValue<T>: Mutable<T> {
    infix fun valueSet(value: T)
    override suspend fun set(value: T) { valueSet(value) }
}

interface MutableWithReactiveValue<T> : MutableReactive<T>, ReactiveValue<T>
interface ReactiveWithMutableValue<T> : MutableReactive<T>, MutableValue<T>

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
}

class NotReadyException(message: String? = null) : IllegalStateException(message)

fun test() {
    val obj = object : MutableValue<Int> {
        override fun valueSet(value: Int) {
            println("Setting value $value")
        }
    }
}