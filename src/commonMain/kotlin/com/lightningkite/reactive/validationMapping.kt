package com.lightningkite.reactive

import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.impl.BaseReactive

private open class ModifyValidationLens<O, T>(
    val source: MutableReactive<O>,
    val get: (O) -> T,
    val check: (O) -> Unit,
    val modify: (O, T) -> O,
) :
    BaseReactive<T>(), MutableReactive<T> {

    private var lastParentState: ReactiveState<O>? = null
    override var state: ReactiveState<T>
        get() {
            if (myListen == null && super.state != lastParentState) super.state = source.state.map(get)
            return super.state
        }
        set(_) = TODO()

    private var myListen: (() -> Unit)? = null

    override fun activate() {
        super.activate()
        super.state = source.state.map(get)
        myListen = source.addListener {
            lastParentState = source.state
//            source.state.handle(
//                success = { super.state = ReadableState(get(it)) },
//                exception = {},
//                notReady = { super.state = ReadableState.notReady }
//            )
            super.state = source.state.map(get)
//            source.state.onSuccess { check(it) }
        }
//        source.state.onSuccess { check(it) }
    }

    override fun deactivate() {
        super.deactivate()
        myListen?.invoke()
        myListen = null
    }

    override suspend fun set(value: T) {
        super.state = ReactiveState(value)
        val v = modify(source.awaitOnce(), value)
        check(v)
        source.set(v)
    }
}

fun <O, T> MutableReactive<O>.validationLens(
    get: (O) -> T,
    check: (O) -> Unit,
    modify: (O, T) -> O
): MutableReactive<T> = ModifyValidationLens(this, get, check, modify)
