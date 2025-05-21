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
                Signal.reportException(e)
            }
        }
    }
}

abstract class BaseSignal<T>(start: SignalState<T> = SignalState.notReady) : Signal<T>, BaseListenable() {
    override var state: SignalState<T> = start
        protected set(value) {
            if (field.raw !== value.raw && field != value) {
                field = value
                invokeAllListeners()
            }
        }
}

class RawSignal<T>(start: SignalState<T> = SignalState.notReady) : BaseSignal<T>(start) {
    override var state: SignalState<T>
        get() = super.state
        public set(value) { super.state = value }
}

abstract class BaseValueSignal<T>(start: T) : ValueSignal<T>, BaseListenable() {
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

class BasicSignal<T>(startValue: T) : MutableValueSignal<T>, BaseValueSignal<T>(startValue) {
    override suspend infix fun set(value: T) {
        this.value = value
    }
}

class LateInitSignal<T>() : MutableSignal<T>, ImmediateMutable<T>, BaseSignal<T>() {
    var value: T
        get() = state.get()
        set(value) {
            state = SignalState(value)
        }

    override fun setImmediate(value: T) {
        this.value = value
    }

    fun unset() {
        state = SignalState.notReady
    }
}

class Constant<T>(override val value: T) : ValueSignal<T> {
    companion object {
        private val NOOP = {}
    }

    override fun addListener(listener: () -> Unit): () -> Unit = NOOP
}
