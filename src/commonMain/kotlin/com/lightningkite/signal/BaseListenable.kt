package com.lightningkite.signal

import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

abstract class BaseListenable : Listenable {
    protected open fun activate() {}
    protected open fun deactivate() {}
    private val listeners = ArrayList<() -> Unit>()
    override fun addListener(listener: () -> Unit): () -> Unit {
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

abstract class BaseReactive<T>(start: ReactiveState<T> = ReactiveState.notReady) : Reactive<T>, BaseListenable() {
    override var state: ReactiveState<T> = start
        protected set(value) {
            if (field.raw !== value.raw && field != value) {
                field = value
                invokeAllListeners()
            }
        }
}

class RawReactive<T>(start: ReactiveState<T> = ReactiveState.notReady) : BaseReactive<T>(start) {
    override var state: ReactiveState<T>
        get() = super.state
        public set(value) { super.state = value }
}

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

class BasicListenable : BaseListenable() {
    private var id = Random.nextInt(0, 100000)
    override fun toString(): String {
        return "BasicListenable($id)"
    }

    fun invokeAll() {
        super.invokeAllListeners()
    }
}

class Signal<T>(startValue: T) : MutableReactiveValue<T>, BaseReactiveValue<T>(startValue)

class LateInitSignal<T>() : ReactiveWithMutableValue<T>, BaseReactive<T>() {
    override fun valueSet(value: T) {
        state = ReactiveState(value)
    }

    fun unset() {
        state = ReactiveState.notReady
    }
}

class Constant<T>(override val value: T) : ReactiveValue<T> {
    companion object {
        private val NOOP = {}
    }

    override fun addListener(listener: () -> Unit): () -> Unit = NOOP
}
