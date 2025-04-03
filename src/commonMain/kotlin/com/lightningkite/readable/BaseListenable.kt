package com.lightningkite.readable

import com.lightningkite.jsoptimized.copy
import com.lightningkite.jsoptimized.emptyVector
import com.lightningkite.jsoptimized.forEach
import com.lightningkite.jsoptimized.indexOf
import com.lightningkite.jsoptimized.isEmpty
import com.lightningkite.jsoptimized.length
import com.lightningkite.jsoptimized.push
import com.lightningkite.jsoptimized.splice
import com.lightningkite.jsoptimized.vectorOf
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

abstract class BaseListenable : Listenable {
    protected open fun activate() {}
    protected open fun deactivate() {}
    private val listeners = emptyVector<() -> Unit>()
    override fun addListener(listener: () -> Unit): () -> Unit {
        if (listeners.length == 0) activate()
        listeners.push(listener)
        return {
            val pos = listeners.indexOf(listener)
            if (pos != -1) {
                listeners.splice(pos, 1)
                if (listeners.isEmpty()) deactivate()
            }
        }
    }

    protected fun invokeAllListeners() {
        listeners.copy().forEach {
            try {
                it()
            } catch (e: Exception) {
                if (e is CancellationException) return@forEach
                Readable.reportException(e)
            }
        }
    }
}

abstract class BaseReadable<T>(start: ReadableState<T> = ReadableState.notReady) : Readable<T>, BaseListenable() {
    override var state: ReadableState<T> = start
        protected set(value) {
            if (field.raw !== value.raw && field != value) {
                field = value
                invokeAllListeners()
            }
        }
}

class RawReadable<T>(start: ReadableState<T> = ReadableState.notReady) : BaseReadable<T>(start) {
    override var state: ReadableState<T>
        get() = super.state
        public set(value) { super.state = value }
}

abstract class BaseImmediateReadable<T>(start: T) : ImmediateReadable<T>, BaseListenable() {
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

class Property<T>(startValue: T) : ImmediateWritable<T>, BaseImmediateReadable<T>(startValue) {
    override suspend infix fun set(value: T) {
        this.value = value
    }
}

class LateInitProperty<T>() : Writable<T>, ImmediateWriteOnly<T>, BaseReadable<T>() {
    var value: T
        get() = state.get()
        set(value) {
            state = ReadableState(value)
        }

    override fun setImmediate(value: T) {
        this.value = value
    }

    fun unset() {
        state = ReadableState.notReady
    }
}

class Constant<T>(override val value: T) : ImmediateReadable<T> {
    companion object {
        private val NOOP = {}
    }

    override fun addListener(listener: () -> Unit): () -> Unit = NOOP
}
