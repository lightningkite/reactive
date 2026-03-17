package com.lightningkite.reactive.core

import com.lightningkite.reactive.lensing.ValueLens
import com.lightningkite.reactive.lensing.lens

/**
 * A wrapper around [ArrayList] that signals its listeners whenever it is mutated
 * */
class ReactiveMutableList<T>(private val list: ArrayList<T>): MutableList<T> by list, MutableReactiveValue<List<T>>, BaseListenable() {
    constructor() : this(ArrayList<T>())
    constructor(items: List<T>) : this(ArrayList(items))
    constructor(vararg startingItems: T) : this(ArrayList(startingItems.asList()))

    override var value: List<T>
        get() = list
        set(value) {
            list.clear()
            addAll(value)
        }

    private inline fun <V> signal(operation: MutableList<T>.()->V): V = list.operation().also { invokeAllListeners() }

    // Used for methods that return if the list was modified
    private inline fun signalChange(operation: MutableList<T>.()->Boolean): Boolean =
        list.operation().also {
            if (it) invokeAllListeners()
        }

    override fun clear() { if (list.isNotEmpty()) signal { clear() } }
    override fun removeAt(index: Int): T = signal { removeAt(index) }
    override fun set(index: Int, element: T): T = signal { set(index, element) }
    override fun retainAll(elements: Collection<T>): Boolean = signalChange { retainAll(elements) }
    override fun removeAll(elements: Collection<T>): Boolean = signalChange { removeAll(elements) }
    override fun addAll(elements: Collection<T>): Boolean = signalChange { addAll(elements) }
    override fun addAll(index: Int, elements: Collection<T>): Boolean = signalChange { addAll(index, elements) }
    override fun add(index: Int, element: T) = signal { add(index, element) }
    override fun add(element: T): Boolean = signal { add(element) }
    override fun remove(element: T): Boolean = signalChange { remove(element) }

    fun reactiveContains(element: T) = object : MutableReactiveValue<Boolean> {
        private val lens = this@ReactiveMutableList.lens { element in it }

        override fun addListener(listener: () -> Unit): Release = lens.addListener(listener)

        override var value: Boolean
            get() = lens.value
            set(value) {
                if (value) add(element)
                else remove(element)
            }
    }
}