package com.lightningkite.reactive.core

import com.lightningkite.reactive.lensing.lens

/**
 * A wrapper around [HashMap] that signals its listeners whenever it is mutated
 * */
class ReactiveMutableMap<K, V>(private val map: HashMap<K, V>): MutableMap<K, V> by map, MutableReactiveValue<Map<K, V>>, BaseListenable() {
    constructor() : this(HashMap())

    override var value: Map<K, V>
        get() = map
        set(value) {
            if (value != map) {
                map.clear()
                map.putAll(value)
                invokeAllListeners()
            }
        }

    val reactiveEntries: ReactiveValue<Set<Map.Entry<K, V>>> =
        object : ReactiveValue<Set<Map.Entry<K, V>>>, Listenable by this {
            override val value: Set<Map.Entry<K, V>> get() = map.entries
        }

    val reactiveKeys: ReactiveValue<Set<K>> =
        object : ReactiveValue<Set<K>>, Listenable by this {
            override val value: Set<K> get() = map.keys
        }

    val reactiveValues: ReactiveValue<Collection<V>> =
        object : ReactiveValue<Collection<V>>, Listenable by this {
            override val value: Collection<V> get() = map.values
        }

    private inline fun <T> signal(operation: MutableMap<K, V>.()->T) = map.operation().also { invokeAllListeners() }

    override fun clear() = signal { clear() }
    override fun remove(key: K): V? = map.remove(key).also { if (it != null) invokeAllListeners() }
    override fun putAll(from: Map<out K, V>) = signal { putAll(from) }
    override fun put(key: K, value: V): V? = signal { put(key, value) }

    private inner class Element(private val key: K) : MutableReactiveValue<V?> {
        private val lens = this@ReactiveMutableMap.lens { it[key] }

        override fun addListener(listener: () -> Unit): Release = lens.addListener(listener)

        override var value: V?
            get() = lens.value
            set(value) {
                if (value == null) remove(key)
                else put(key, value)
            }
    }

    fun getReactive(key: K): MutableReactiveValue<V?> = Element(key)
}