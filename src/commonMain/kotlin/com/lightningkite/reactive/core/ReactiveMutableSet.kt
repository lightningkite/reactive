package com.lightningkite.reactive.core

/**
 * A wrapper around [LinkedHashSet] that signals its listeners whenever it is mutated
 * */
class ReactiveMutableSet<T>(private val hashSet: LinkedHashSet<T>): MutableSet<T> by hashSet, MutableReactiveValue<Set<T>>, BaseListenable() {
    constructor() : this(LinkedHashSet<T>())
    constructor(vararg startingItems: T) : this(LinkedHashSet(startingItems.toList()))

    override var value: Set<T>
        get() = hashSet
        set(value) {
            if (hashSet != value) {
                hashSet.clear()
                addAll(value)
            }
        }

    private inline fun <V> signal(operation: MutableSet<T>.()->V): V = hashSet.operation().also { invokeAllListeners() }

    // Used for methods that return if the list was modified
    private inline fun signalChange(operation: MutableSet<T>.()->Boolean): Boolean =
        hashSet.operation().also {
            if (it) invokeAllListeners()
        }

    override fun clear() = signal { clear() }
    override fun retainAll(elements: Collection<T>): Boolean = signalChange { retainAll(elements.toSet()) }
    override fun removeAll(elements: Collection<T>): Boolean = signalChange { removeAll(elements.toSet()) }
    override fun addAll(elements: Collection<T>): Boolean = signalChange { addAll(elements) }
    override fun add(element: T): Boolean = signalChange { add(element) }
    override fun remove(element: T): Boolean = signalChange { remove(element) }
}