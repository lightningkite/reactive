package com.lightningkite.reactive.lensing

import com.lightningkite.reactive.core.BaseListenable
import com.lightningkite.reactive.core.BaseReactiveValue
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableWithReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.ReactiveValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel


interface MutableReactiveElement<E> : MutableWithReactiveValue<E> {
    val index: ReactiveValue<Int>
}

/**
 * THIS ONLY WORKS IF THE `set` on the receiver *never* manipulates the input before notifying.
 */
fun <E> MutableReactive<List<E>>.lensByElementAssumingSetNeverManipulates(): Reactive<List<MutableReactiveElement<E>>> =
    lensByElementAssumingSetNeverManipulates { it }

/**
 * THIS ONLY WORKS IF THE `set` on the receiver *never* manipulates the input before notifying.
 */
fun <E, W> MutableReactive<List<E>>.lensByElementAssumingSetNeverManipulates(map: CoroutineScope.(MutableReactiveElement<E>) -> W): Reactive<List<W>> =
    LensByElementAssumingSetNeverManipulates(this, map)



private class LensByElementAssumingSetNeverManipulates<E, W>(
    val source: MutableReactive<List<E>>,
    private val map: CoroutineScope.(MutableReactiveElement<E>) -> W
) :
    Reactive<List<W>>, BaseListenable() {

    inner class Instance(calculationContext: CoroutineScope, index: Int, value: E) : MutableReactiveElement<E>,
        BaseReactiveValue<E>(value) {
        val mapped = map(calculationContext, this)
        override val index: ReactiveValue<Int> = Constant(index)
        override suspend fun set(value: E) {
            this.value = value
            source.set(sources.map { it.value })
        }
    }

    val sources: ArrayList<Instance> = ArrayList()
    var _state: ReactiveState<List<W>> = ReactiveState.notReady
    private var myListen: (() -> Unit)? = null
    override fun activate() {
        super.activate()
        myListen = source.addListener {
            refresh()
            invokeAllListeners()
        }
        refresh()
    }

    override fun deactivate() {
        super.deactivate()
        myListen?.invoke()
        myListen = null
    }

    override val state: ReactiveState<List<W>>
        get() {
            if (myListen == null) refresh()
            return _state
        }
    var suppress = false
    var old: CoroutineScope? = null

    fun refresh() {
        if (suppress) return
        old?.cancel()
        val context = CoroutineScope(Job())
        old = context
        _state = source.state.map {
            sources.clear()
            sources.addAll(it.mapIndexed { index, it -> Instance(context, index, it) })
            sources.map { it.mapped }
        }
    }
}
