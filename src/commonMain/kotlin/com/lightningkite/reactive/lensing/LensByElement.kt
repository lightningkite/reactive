package com.lightningkite.reactive.lensing

import com.lightningkite.reactive.context.CalculationContext
import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableWithReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.extensions.invokeAllSafe
import com.lightningkite.reactive.lensing.validation.IssueNode
import com.lightningkite.reactive.lensing.validation.MutableValidated
import com.lightningkite.reactive.lensing.validation.Validated
import com.lightningkite.reactive.lensing.validation.ValidatedValue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.collections.plus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName

fun <E, ID, W> MutableReactive<List<E>>.lensByElementWithIdentity(
    identity: (E) -> ID,
    map: CalculationContext.(MutableWithReactiveValue<E>) -> W
) = LensByElement<E, ID, W>(this, identity = identity, elementLens = { it.map(it) })

fun <E, ID> MutableReactive<List<E>>.lensByElementWithIdentity(
    identity: (E) -> ID
) = LensElements<E, ID>(this, identity = identity, elementLens = { it })

@JvmName("setLensByElementWithIdentity")
@Suppress("Deprecation")
fun <E, ID, W> MutableReactive<Set<E>>.lensByElementWithIdentity(
    identity: (E) -> ID,
    map: CalculationContext.(MutableWithReactiveValue<E>) -> W
) = lens(get = { it.toList() }, set = { it.toSet() }).lensByElement(identity, map)

@JvmName("setLensByElementWithIdentity")
@Suppress("Deprecation")
fun <E, ID> MutableReactive<Set<E>>.lensByElementWithIdentity(
    identity: (E) -> ID
) = lens(get = { it.toList() }, set = { it.toSet() }).lensByElement(identity)


typealias LensElements<E, ID> = LensByElement<E, ID, LensByElement<E, ID, *>.Element>

class LensByElement<E, ID, T>(
    val source: MutableReactive<List<E>>,
    val identity: (E) -> ID,
    val elementLens: (LensByElement<E, ID, T>.Element) -> T
) : Reactive<List<T>> {
    private val node = IssueNode(parent = (source as? MutableValidated)?.node).apply { connect() }

    inner class Element internal constructor(valueInit: E) : MutableWithReactiveValue<E>, MutableValidated<E>, CalculationContext {
        override val node: IssueNode = this@LensByElement.node.child()

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
                if (value) node.disconnect() else node.connect()
                listeners.invokeAllSafe()
                job.cancel()
                job = Job()
            }
        var id: ID = identity(valueInit)
            private set
        private val listeners = ArrayList<() -> Unit>()
        override var value: E = valueInit
            internal set(value) {
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


@Deprecated("Be specific about what kind you need.")
fun <E, ID, W> MutableReactive<List<E>>.lensByElement(identity: (E) -> ID, map: CalculationContext.(MutableWithReactiveValue<E>) -> W) =
    LensByElement<E, ID, W>(this, identity = identity, elementLens = { it.map(it) })

@Deprecated("Be specific about what kind you need.")
fun <E, ID> MutableReactive<List<E>>.lensByElement(identity: (E) -> ID) =
    LensElements<E, ID>(this, identity = identity, elementLens = { it })

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
