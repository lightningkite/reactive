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

    override fun clear() = signal { clear() }
    override fun retainAll(elements: Collection<T>): Boolean = signal { retainAll(elements.toSet()) }
    override fun removeAll(elements: Collection<T>): Boolean = signal { removeAll(elements.toSet()) }
    override fun addAll(elements: Collection<T>): Boolean = signal { addAll(elements) }
    override fun add(element: T): Boolean = signal { add(element) }

    override fun remove(element: T): Boolean {
        val success = hashSet.remove(element)
        if (success) invokeAllListeners()
        return success
    }
}