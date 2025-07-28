package com.lightningkite.reactive

import kotlinx.coroutines.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName

operator fun Listenable.plus(other: Listenable): Listenable = object: Listenable {
    override fun addListener(listener: () -> Unit): () -> Unit {
        val a = this@plus.addListener(listener)
        val b = other.addListener(listener)
        return {
            a()
            b()
        }
    }
}

private open class ReactiveLens<S : Reactive<O>, O, T>(val source: S, val get: (O) -> T) : BaseReactive<T>() {
    override var state: ReactiveState<T>
        get() {
            if (myListen == null) super.state = source.state.map(get)
            return super.state
        }
        set(_) = TODO()

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

private open class SetLens<O, T>(source: MutableReactive<O>, get: (O) -> T, val set: (T) -> O) :
    ReactiveLens<MutableReactive<O>, O, T>(source, get), MutableReactive<T> {
    override suspend fun set(value: T) {
        source.set(set.invoke(value))
    }
}

private open class ModifyLens<O, T>(source: MutableReactive<O>, get: (O) -> T, val modify: (O, T) -> O) :
    ReactiveLens<MutableReactive<O>, O, T>(source, get), MutableReactive<T> {
    override suspend fun set(value: T) {
        source.set(modify(source.awaitOnce(), value))
    }
}

private open class ReactiveValueLens<S : ReactiveValue<O>, O, T>(val source: S, val get: (O) -> T) :
    BaseReactiveValue<T>(source.value.let(get)) {
    override var value: T
        get() {
            if (myListen == null) super.value = source.value.let(get)
            return super.value
        }
        set(_) = TODO()

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

private open class SetLensValue<O, T>(source: MutableReactiveValue<O>, get: (O) -> T, val set: (T) -> O) :
    ReactiveValueLens<MutableReactiveValue<O>, O, T>(source, get), MutableReactiveValue<T> {
    override var value: T
        get() = super.value
        set(value) {
            source.value = set.invoke(value)
        }
}

private open class ModifyLensValue<O, T>(source: MutableReactiveValue<O>, get: (O) -> T, val modify: (O, T) -> O) :
    ReactiveValueLens<MutableReactiveValue<O>, O, T>(source, get), MutableReactiveValue<T> {
    override var value: T
        get() = super.value
        set(value) {
            source.value = modify(source.value, value)
        }
}

fun <T> Listenable.lensListenable(
    get: () -> T
): Reactive<T> = ReactiveValueLens(object: ReactiveValue<Unit>, Listenable by this{
    override val value: Unit get() = Unit
}, { get() })

fun <O, T> Reactive<O>.lens(
    get: (O) -> T
): Reactive<T> = ReactiveLens(this, get)

@Deprecated("use the new name, lens, instead", ReplaceWith("lens", "com.lightningkite.readable.lens"))
fun <O, T> MutableReactive<O>.map(
    get: (O) -> T,
    set: (O, T) -> O
): MutableReactive<T> = lens(get, set)

fun <O, T> MutableReactive<O>.lens(
    get: (O) -> T,
    modify: (O, T) -> O
): MutableReactive<T> = ModifyLens(this, get, modify)

fun <O, T> MutableReactive<O>.lens(
    get: (O) -> T,
    set: (T) -> O
): MutableReactive<T> = SetLens(this, get, set)

fun <O, T> ReactiveValue<O>.lens(
    get: (O) -> T
): ReactiveValue<T> = ReactiveValueLens(this, get)

fun <O, T> MutableReactiveValue<O>.lens(
    get: (O) -> T,
    set: (T) -> O
): MutableReactiveValue<T> = SetLensValue(this, get, set)

fun <O, T> MutableReactiveValue<O>.lens(
    get: (O) -> T,
    modify: (O, T) -> O
): MutableReactiveValue<T> = ModifyLensValue(this, get, modify)

@Deprecated("Be specific about what kind you need.")
fun <E, ID, W> MutableReactive<List<E>>.lensByElement(identity: (E) -> ID, map: CalculationContext.(MutableWithReactiveValue<E>) -> W) =
    MutableReactiveList<E, ID, W>(this, identity = identity, elementLens = { it.map(it) })

@Deprecated("Be specific about what kind you need.")
fun <E, ID> MutableReactive<List<E>>.lensByElement(identity: (E) -> ID) =
    WritableListWithoutMap<E, ID>(this, identity = identity, elementLens = { it })

@Deprecated("Be specific about what kind you need.")
@JvmName("setLensByElement")
@Suppress("Deprecation")
fun <E, ID, W> MutableReactive<Set<E>>.lensByElement(identity: (E) -> ID, map: CalculationContext.(MutableWithReactiveValue<E>) -> W) =
    lens(get = { it.toList() }, set = { it.toSet() }).lensByElement(identity, map)

@Deprecated("Be specific about what kind you need.")
@JvmName("setLensByElement")
@Suppress("Deprecation")
fun <E, ID> MutableReactive<Set<E>>.lensByElement(identity: (E) -> ID) =
    lens(get = { it.toList() }, set = { it.toSet() }).lensByElement(identity)

fun <E, ID, W> MutableReactive<List<E>>.lensByElementWithIdentity(
    identity: (E) -> ID,
    map: CalculationContext.(MutableWithReactiveValue<E>) -> W
) =
    MutableReactiveList<E, ID, W>(this, identity = identity, elementLens = { it.map(it) })

fun <E, ID> MutableReactive<List<E>>.lensByElementWithIdentity(identity: (E) -> ID) =
    WritableListWithoutMap<E, ID>(this, identity = identity, elementLens = { it })

@JvmName("setLensByElementWithIdentity")
@Suppress("Deprecation")
fun <E, ID, W> MutableReactive<Set<E>>.lensByElementWithIdentity(
    identity: (E) -> ID,
    map: CalculationContext.(MutableWithReactiveValue<E>) -> W
) =
    lens(get = { it.toList() }, set = { it.toSet() }).lensByElement(identity, map)

@JvmName("setLensByElementWithIdentity")
@Suppress("Deprecation")
fun <E, ID> MutableReactive<Set<E>>.lensByElementWithIdentity(identity: (E) -> ID) =
    lens(get = { it.toList() }, set = { it.toSet() }).lensByElement(identity)

interface ListItemMutableReactiveValue<E> : MutableWithReactiveValue<E> {
    val index: ReactiveValue<Int>
}

/**
 * THIS ONLY WORKS IF THE `set` on the receiver *never* manipulates the input before notifying.
 */
fun <E> MutableReactive<List<E>>.lensByElementAssumingSetNeverManipulates(): Reactive<List<ListItemMutableReactiveValue<E>>> =
    lensByElementAssumingSetNeverManipulates { it }

/**
 * THIS ONLY WORKS IF THE `set` on the receiver *never* manipulates the input before notifying.
 */
fun <E, W> MutableReactive<List<E>>.lensByElementAssumingSetNeverManipulates(map: CalculationContext.(ListItemMutableReactiveValue<E>) -> W): Reactive<List<W>> =
    LensByElementAssumingSetNeverManipulates(this, map)

private class LensByElementAssumingSetNeverManipulates<E, W>(
    val source: MutableReactive<List<E>>,
    private val map: CalculationContext.(ListItemMutableReactiveValue<E>) -> W
) :
    Reactive<List<W>>, BaseListenable() {

    inner class Instance(calculationContext: CalculationContext, index: Int, value: E) : ListItemMutableReactiveValue<E>,
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

typealias WritableListWithoutMap<E, ID> = MutableReactiveList<E, ID, MutableReactiveList<E, ID, *>.Element>

class MutableReactiveList<E, ID, T>(
    val source: MutableReactive<List<E>>,
    val identity: (E) -> ID,
    val elementLens: (MutableReactiveList<E, ID, T>.Element) -> T
) : Reactive<List<T>> {
    inner class Element internal constructor(valueInit: E) : MutableWithReactiveValue<E>, CalculationContext {
        private var job = Job()
        private val restOfContext = Dispatchers.Default + CoroutineExceptionHandler { coroutineContext, throwable ->
            if (throwable !is CancellationException) {
                Reactive.reportException(throwable)
            }
        }
        override val coroutineContext get() = restOfContext + job

        internal var dead = false
            set(value) {
                field = value
                listeners.invokeAllSafe()
                job.cancel()
                job = Job()
            }
        var id: ID = identity(valueInit)
            private set
        private val listeners = ArrayList<() -> Unit>()
        override val state: ReactiveState<E>
            get() = ReactiveState(value)
        override var value: E = valueInit
            set(value) {
                if (field != value) {
                    id = identity(value)
                    field = value
                    listeners.invokeAllSafe()
                }
            }
        internal var queuedSet: ReactiveState<E> = ReactiveState.notReady
        internal val queuedOrValue: E
            get() {
                val qs = queuedSet
                return if (qs.success) qs.get() else value
            }
        internal var usedFlag = false

        override suspend fun set(value: E) {
            queuedSet = ReactiveState(value)
            val allWritables = elements.awaitOnce()
            val newList = allWritables.map { it.queuedOrValue }
            if (allWritables.contains(this)) {
                try {
                    source.set(newList)
                } finally {
                    queuedSet = ReactiveState.notReady
                }
                if (elements.myListen == null) {
                    this.value = value
                }
            }
        }

        override fun addListener(listener: () -> Unit): () -> Unit {
            listeners.add(listener)
            return {
                val pos = listeners.indexOfFirst { it === listener }
                if (pos != -1) {
                    listeners.removeAt(pos)
                }
            }
        }

        val view = elementLens(this)
    }

    inner class Elements : MutableReactive<List<Element>> {
        override suspend fun set(value: List<Element>) {
            source.set(value.map { it.queuedOrValue })
        }

        private var lastElements: List<Element> = listOf()
        private var _state: ReactiveState<List<Element>> = ReactiveState.notReady
            set(value) {
                if (value.success) lastElements = value.get()
                field = value
            }
        override var state: ReactiveState<List<Element>>
            get() {
                if (myListen == null || !_state.ready) _state = getStateFromSource()
                return _state.map { it }
            }
            private set(value) {
                if (_state != value) {
                    _state = value
                    myListeners.invokeAllSafe()
                }
            }

        private val myListeners = ArrayList<() -> Unit>()
        internal var myListen: (() -> Unit)? = null
        override fun addListener(listener: () -> Unit): () -> Unit {
            if (myListeners.isEmpty()) {
                myListen = source.addListener {
                    state = getStateFromSource()
                }
                state = getStateFromSource()
            }
            myListeners.add(listener)
            return {
                myListeners.remove(listener)
                if (myListeners.isEmpty()) {
                    myListen?.invoke()
                    myListen = null
                }
            }
        }

        private fun getStateFromSource() = source.state.map { newList ->
            lastElements.forEach { it.usedFlag = false }
            val result = newList.mapIndexed { index, newElement ->
                val existing = lastElements.getOrNull(index)?.takeIf { it.id == identity(newElement) }
                    ?: lastElements.find { old -> !old.usedFlag && old.id == identity(newElement) }
                if (existing != null) {
                    existing.usedFlag = true
                    existing.value = newElement
                    existing
                } else {
                    newElement(newElement)
                }
            }
            lastElements.forEach { if (!it.usedFlag) it.dead = true }
            result
        }
    }

    val elements = Elements()

    fun newElement(e: E): Element = Element(e)
    suspend fun add(index: Int, value: E): T {
        val newly = newElement(value)
        elements.set(elements.awaitOnce().toMutableList().apply { add(index, newly) })
        return newly.view
    }

    suspend fun add(value: E): T {
        val newly = newElement(value)
        elements.set(elements.awaitOnce() + newly)
        return newly.view
    }

    suspend fun upsert(value: E): T {
        val id = identity(value)
        val existing = elements.awaitOnce().find { it.id == id }
        return if (existing == null) add(value) else {
            existing.set(value)
            existing.view
        }
    }

    suspend fun remove(element: E) {
        val id = identity(element)
        removeById(id)
    }

    suspend fun removeById(id: ID) {
        elements.set(elements.awaitOnce().filter { it.id != id })
    }

    override val state: ReactiveState<List<T>>
        get() = elements.state.map { it.map { it.view } }

    override fun addListener(listener: () -> Unit): () -> Unit = elements.addListener(listener)
}
