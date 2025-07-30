package com.lightningkite.reactive.core

import com.lightningkite.reactive.core.ReactiveMutableList
import com.lightningkite.reactive.lensing.lens

/**
 * A wrapper around [LinkedHashSet] that signals its listeners whenever it is mutated
 * */
class ReactiveMutableSet<T>(private val hashSet: LinkedHashSet<T>): MutableSet<T> by hashSet, MutableReactiveValue<Set<T>>, BaseListenable() {
    constructor() : this(LinkedHashSet<T>())
    constructor(items: Set<T>) : this(LinkedHashSet(items))
    constructor(vararg startingItems: T) : this(LinkedHashSet(startingItems.toList()))

    override var value: Set<T>
        get() = hashSet
        set(value) {
            if (hashSet != value) {
                hashSet.clear()
                addAll(value)
            }
        }

    private inline fun signal(operation: MutableSet<T>.()->Boolean): Boolean =
        hashSet.operation().also { if (it) invokeAllListeners() }

    override fun clear() {
        if (isEmpty()) return
        hashSet.clear()
        invokeAllListeners()
    }
    override fun retainAll(elements: Collection<T>): Boolean = signal { retainAll(elements.toSet()) }
    override fun removeAll(elements: Collection<T>): Boolean = signal { removeAll(elements.toSet()) }
    override fun addAll(elements: Collection<T>): Boolean = signal { addAll(elements) }
    override fun add(element: T): Boolean = signal { add(element) }
    override fun remove(element: T): Boolean = signal { remove(element) }

    fun reactiveContains(element: T) = object : MutableReactiveValue<Boolean> {
        private val lens = this@ReactiveMutableSet.lens { element in it }

        override fun addListener(listener: () -> Unit): () -> Unit = lens.addListener(listener)

        override var value: Boolean
            get() = lens.value
            set(value) {
                if (value) add(element)
                else remove(element)
            }
    }
}