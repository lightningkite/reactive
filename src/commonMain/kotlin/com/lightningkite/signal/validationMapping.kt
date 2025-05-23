package com.lightningkite.signal

private open class ModifyValidationLens<O, T>(
    val source: MutableSignal<O>,
    val get: (O) -> T,
    val check: (O) -> Unit,
    val modify: (O, T) -> O,
) :
    BaseSignal<T>(), MutableSignal<T> {

    private var lastParentState: SignalState<O>? = null
    override var state: SignalState<T>
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
        super.state = SignalState(value)
        val v = modify(source.awaitOnce(), value)
        check(v)
        source.set(v)
    }
}

fun <O, T> MutableSignal<O>.validationLens(
    get: (O) -> T,
    check: (O) -> Unit,
    modify: (O, T) -> O
): MutableSignal<T> = ModifyValidationLens(this, get, check, modify)
