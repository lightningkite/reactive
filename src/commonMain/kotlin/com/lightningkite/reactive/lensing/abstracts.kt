package com.lightningkite.reactive.lensing

import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.core.BaseReactive
import com.lightningkite.reactive.core.BaseReactiveValue
import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.ReactiveValue

open class Lens<S : Reactive<T>, T, L>(val source: S, val get: (T) -> L) : BaseReactive<L>() {
    override var state: ReactiveState<L>
        get() {
            if (myListen == null) super.state = source.state.map(get)
            return super.state
        }
        protected set(value) {
            super.state = value
        }

    private var myListen: (() -> Unit)? = null

    override fun activate() {
        super.activate()
        super.state = source.state.map(get)
        myListen = source.addListener {
            super.state = source.state.map(get)
        }
    }

    override fun deactivate() {
        super.deactivate()
        myListen?.invoke()
        myListen = null
    }
}

open class SetLens<O, T>(
    source: MutableReactive<O>, get: (O) -> T,
    val set: (T) -> O
) : Lens<MutableReactive<O>, O, T>(source, get), MutableReactive<T> {
    override suspend fun set(value: T) {
        val transformed = set.invoke(value)
        state = ReactiveState(get(transformed)) // using get(set(value)) so that echos are filtered out
        source.set(transformed)
    }
}

open class ModifyLens<O, T>(
    source: MutableReactive<O>,
    get: (O) -> T,
    val modify: (O, T) -> O
) : Lens<MutableReactive<O>, O, T>(source, get), MutableReactive<T> {
    override suspend fun set(value: T) {
        val transformed = modify(source.awaitOnce(), value)
        state = ReactiveState(get(transformed)) // using get(modify(this, value)) so that echos are filtered out
        source.set(transformed)
    }
}

open class ValueLens<S : ReactiveValue<T>, T, L>(
    val source: S,
    val get: (T) -> L
) : BaseReactiveValue<L>(source.value.let(get))  {
    override var value: L
        get() {
            if (myListen == null) super.value = source.value.let(get)
            return super.value
        }
        set(value) {
            super.value = value
        }

    private var myListen: (() -> Unit)? = null
    override fun activate() {
        super.activate()
        super.value = source.value.let(get)
        myListen = source.addListener {
            super.value = source.value.let(get)
        }
    }

    override fun deactivate() {
        super.deactivate()
        myListen?.invoke()
        myListen = null
    }
}

open class SetValueLens<O, T>(source: MutableReactiveValue<O>, get: (O) -> T, val set: (T) -> O) :
    ValueLens<MutableReactiveValue<O>, O, T>(source, get), MutableReactiveValue<T> {
    override var value: T
        get() = super.value
        set(value) {
            val transformed = set.invoke(value)
            super.value = get(transformed)
            source.value = transformed
        }
}

open class ModifyValueLens<O, T>(source: MutableReactiveValue<O>, get: (O) -> T, val modify: (O, T) -> O) :
    ValueLens<MutableReactiveValue<O>, O, T>(source, get), MutableReactiveValue<T> {
    override var value: T
        get() = super.value
        set(value) {
            val transformed = modify(source.value, value)
            super.value = get(transformed)
            source.value = transformed
        }
}

fun <T, L> Reactive<T>.lens(get: (T) -> L): Reactive<L> = Lens(this, get)
fun <T, L> ReactiveValue<T>.lens(get: (T) -> L): ReactiveValue<L> = ValueLens(this, get)

fun <T> Listenable.lensListenable(
    get: () -> T
): Reactive<T> = ValueLens(
    object: ReactiveValue<Unit>, Listenable by this {
        override val value: Unit get() = Unit
    },
    get = { get() }
)