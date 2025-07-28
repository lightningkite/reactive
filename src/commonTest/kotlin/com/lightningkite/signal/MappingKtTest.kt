package com.lightningkite.signal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MappingKtTest {

    data class Sample(
        val x: Int,
        val y: List<Int>
    )

    @Test fun readableLenses() {
        val source = Signal(42)
        val lenses = listOf(
            source.lens(
                get = { it + 1 },
                set = { it - 1 }
            ),
            (source as MutableReactive<Int>).lens(
                get = { it + 1 },
                set = { it - 1 }
            ),
            source.lens(
                get = { it + 1 },
                modify = { _, it -> it - 1 }
            ),
            (source as MutableReactive<Int>).lens(
                get = { it + 1 },
                modify = { _, it -> it - 1 }
            ),
            (source as Reactive<Int>).lens(
                get = { it + 1 },
            ),
            (source as ReactiveValue<Int>).lens(
                get = { it + 1 },
            )
        )
        for (view in lenses) {
            source.value = 41
            assertEquals(ReactiveState(42), view.state)
            testContext {
                var seen = -1
                var sets = 0
                reactiveScope {
                    seen = view()
                    sets++
                }
                assertEquals(42, seen)
                assertEquals(1, sets)
                source.value = 42
                assertEquals(43, seen)
                assertEquals(2, sets)
                source.value = 42
                assertEquals(43, seen)
                assertEquals(2, sets)
                source.value = 43
                assertEquals(44, seen)
                assertEquals(3, sets)
            }
        }
    }

    @Test
    fun writableLenses() {
        val source = Signal(42)
        val lenses = listOf(
            source.lens(
                get = { it + 1 },
                set = { it - 1 }
            ),
            (source as MutableReactive<Int>).lens(
                get = { it + 1 },
                set = { it - 1 }
            ),
            source.lens(
                get = { it + 1 },
                modify = { _, it -> it - 1 }
            ),
            (source as MutableReactive<Int>).lens(
                get = { it + 1 },
                modify = { _, it -> it - 1 }
            )
        )
        for (view in lenses) {
            source.value = 41
            assertEquals(ReactiveState(42), view.state)
            testContext {
                var seen = -1
                var sets = 0
                reactiveScope {
                    seen = view()
                    sets++
                }
                assertEquals(42, seen)
                assertEquals(1, sets)
                load { view.set(43) }
                assertEquals(43, seen)
                assertEquals(2, sets)
                load { view.set(43) }
                assertEquals(43, seen)
                assertEquals(2, sets)
                source.value += 1
                assertEquals(44, seen)
                assertEquals(3, sets)
            }
        }
    }

    @Test
    fun subfield() {
        val source = Signal(Sample(42, listOf(1, 2, 3)))
        val lenses = listOf(
            source.lens(
                get = { it.x },
                modify = { old, it -> old.copy(x = it) }
            ),
            (source as MutableReactive<Sample>).lens(
                get = { it.x },
                modify = { old, it -> old.copy(x = it) }
            )
        )
        for (view in lenses) {
            source.value = Sample(42, listOf(1, 2, 3))
            assertEquals(ReactiveState(42), view.state)
            testContext {
                var seen = -1
                var sets = 0
                reactiveScope {
                    seen = view()
                    sets++
                }
                assertEquals(42, seen)
                assertEquals(1, sets)
                load { view.set(43) }
                assertEquals(43, seen)
                assertEquals(2, sets)
                load { view.set(43) }
                assertEquals(43, seen)
                assertEquals(2, sets)
                source.value = source.value.copy(y = source.value.y + 4)
                assertEquals(43, seen)
                assertEquals(2, sets)
                source.value = source.value.copy(x = source.value.x + 1)
                assertEquals(44, seen)
                assertEquals(3, sets)
            }
        }
    }

    @Test
    fun subfieldLate() {
        val source = LateInitSignal<Sample>()
        val view = source.lens(
            get = { it.x },
            modify = { old, it ->
                println("Modify $old $it")
                old.copy(x = it)
            }
        )
        source.addListener {
            println("source raw: ${source.state}")
        }
//        view.addListener {
//            println("View raw: ${view.state}")
//        }
        assertEquals(ReactiveState.notReady, view.state)
        testContext {
            var seen = -1
            var sets = 0
            reactiveScope {
                println("Rerunning...")
                seen = view()
                println("Got $seen")
                sets++
            }
            assertEquals(-1, seen)
            assertEquals(0, sets)
            load { view.set(43) }
            assertEquals(-1, seen)
            assertEquals(0, sets)
            source.value = Sample(x = 42, y = listOf(1, 2, 3))
            // Weird trait here: set is queued!
            assertEquals(43, seen)
            assertEquals(2, sets)
            load { view.set(44) }
            assertEquals(44, seen)
            assertEquals(3, sets)
            source.value = source.state.get().let {
                it.copy(y = it.y + 4)
            }
            assertEquals(44, seen)
            assertEquals(3, sets)
            source.value = source.state.get().let {
                it.copy(x = it.x + 1)
            }
            assertEquals(45, seen)
            assertEquals(4, sets)
        }
    }

    fun perElementTest(action: CalculationContext.(source: MutableReactiveValue<List<Int>>, view: MutableReactiveList<Int, Int, MutableReactiveList<Int, Int, *>.Element>) -> Unit) {
        val source = Signal(listOf(1, 2, 3)).apply {
            addListener {
                println("source: $value")
            }
        }
        val view = MutableReactiveList<Int, Int, MutableReactiveList<Int, Int, *>.Element>(
            source,
            identity = { it },
            elementLens = { it })
        assertEquals(source.value, view.state.get().map { it.value })
        testContext {
            // The state of each subwritable always matches the source
            assertEquals(source.value, view.state.get().map { it.value }.also { println("Before: $it") })
            action(source, view)
            println("After values: ${source.value}")
            assertEquals(source.value, view.state.get().map {
                println("Checking item ${it.id}")
                it.value
            }.also { println("After: $it") })
        }
    }


    // Identity of sub writables remains the same for same elements
    @Test
    fun listIdentityWithoutListen() = perElementTest { source, view ->
        val two = view.state.get().find { it.value == 2 }!!
        val three = view.state.get().find { it.value == 3 }!!
        source.value = listOf(2, 3, 4)
        assertSame(view.state.get().find { it.value == 2 }, two)
        assertSame(view.state.get().find { it.value == 3 }, three)
    }

    // Identity of sub writables remains the same for same elements
    @Test
    fun listIdentity() = perElementTest { source, view ->
        val two = view.state.get().find { it.value == 2 }!!
        val three = view.state.get().find { it.value == 3 }!!
        reactiveScope { three() }
        source.value = listOf(2, 3, 4)
        assertSame(view.state.get().find { it.value == 2 }, two)
        assertSame(view.state.get().find { it.value == 3 }, three)
    }

    // Setting a sub writable updates the source
    @Test
    fun listSettingWithoutListen() = perElementTest { source, view ->
        val sub = view.state.get().find { it.value == 3 }!!
        load {
            sub.set(4)
        }
        assertEquals(4, source.value.last())
        assertEquals(4, sub.value)
    }

    // Setting a sub writable updates the source
    @Test
    fun listSettingWithListen() = perElementTest { source, view ->
        val sub = view.state.get().find { it.value == 3 }!!
        reactiveScope { sub() }
        load {
            sub.set(4)
        }
        assertEquals(4, source.value.last())
        assertEquals(4, sub.value)
    }

    // Insertion works
    @Test
    fun listInsertionWithoutListen() = perElementTest { source, view ->
        val sub = view.state.get().find { it.value == 3 }!!
        load { view.elements.set(view.elements.awaitOnce() + view.newElement(4)) }
        assertEquals(4, source.value.size)
    }

    @Test
    fun listInsertion() = perElementTest { source, view ->
        val sub = view.state.get().find { it.value == 3 }!!
        reactiveScope { sub() }
        load { view.elements.set(view.elements.awaitOnce() + view.newElement(4)) }
        assertEquals(4, source.value.size)
    }

    // Removal works
    @Test
    fun listRemovalWithoutListen() = perElementTest { source, view ->
        val sub = view.state.get().find { it.value == 3 }!!
        load { view.elements.set(view.elements.awaitOnce().filter { it.value != 3 }) }
        assertEquals(2, source.value.size)
    }

    @Test
    fun listRemoval() = perElementTest { source, view ->
        val sub = view.state.get().find { it.value == 3 }!!
        reactiveScope {
            try {
                sub()
            } catch (e: Exception) {
                println("Blocked $e")
            }
        }
        load { view.elements.set(view.elements.awaitOnce().filter { it.value != 3 }) }
        assertEquals(2, source.value.size)
    }

    // Removal works by identity
    @Test
    fun listRemovalByIdentityWithoutListen() = perElementTest { source, view ->
        load { view.remove(3) }
        assertEquals(2, source.value.size)
    }

    @Test
    fun listRemovalByIdentity() = perElementTest { source, view ->
        load { view.remove(3) }
        assertEquals(2, source.value.size)
    }

    // Rearranging works and retains identity
    @Test
    fun listRearrangingWithoutListening() = perElementTest { source, view ->
        val sub = view.state.get().find { it.value == 3 }!!
        load { view.elements.set(view.elements.awaitOnce().reversed()) }
        assertEquals(3, sub.value)
    }

    @Test
    fun listRearranging() = perElementTest { source, view ->
        val sub = view.state.get().find { it.value == 3 }!!
        reactiveScope { sub() }
        load { view.elements.set(view.elements.awaitOnce().reversed()) }
        assertEquals(3, sub.value)
    }

    @Test
    fun listSetWaitsForCompletion() {
        val backing = Signal(listOf(1, 2, 3)).apply {
            addListener {
                println("backing: $value")
            }
        }
        val setGate = WaitGate()
        val source = object : ReactiveValue<List<Int>>, MutableReactive<List<Int>> {
            override val value: List<Int> get() = backing.value
            override fun addListener(listener: () -> Unit): () -> Unit = backing.addListener(listener)

            override suspend fun set(value: List<Int>) {
                setGate.await()
                backing.value = value
            }
        }
        val view = MutableReactiveList<Int, Int, MutableReactiveList<Int, Int, *>.Element>(
            source,
            identity = { it },
            elementLens = { it })
        assertEquals(source.value, view.state.get().map { it.value })
        testContext {
            // The state of each subwritable always matches the source
            assertEquals(source.value, view.state.get().map { it.value }.also { println("Before: $it") })
            val third = view.state.get().find { it.value == 3 }!!
            reactiveScope { println("third: ${third()}") }

            load {
                third.set(4)
                println("Complete")
            }
            assertEquals(3, third.value)
            setGate.permitOnce()
            assertEquals(4, third.value)

            assertEquals(source.value, view.state.get().map { it.value }.also { println("After: $it") })
        }
    }

    @Test
    fun listConcurrentWorks() {
        val backing = Signal(listOf(1, 2, 3)).apply {
            addListener {
                println("backing: $value")
            }
        }
        val setGate = WaitGate()
        val source = object : ReactiveValue<List<Int>>, MutableReactive<List<Int>> {
            override val value: List<Int> get() = backing.value
            override fun addListener(listener: () -> Unit): () -> Unit = backing.addListener(listener)

            override suspend fun set(value: List<Int>) {
                setGate.await()
                backing.value = value
            }
        }
        val view = MutableReactiveList<Int, Int, MutableReactiveList<Int, Int, *>.Element>(
            source,
            identity = { it },
            elementLens = { it })
        assertEquals(source.value, view.state.get().map { it.value })
        testContext {
            // The state of each subwritable always matches the source
            assertEquals(source.value, view.state.get().map { it.value }.also { println("Before: $it") })
            val second = view.state.get().find { it.value == 2 }!!
            val third = view.state.get().find { it.value == 3 }!!
            reactiveScope { println("second: ${second()}") }
            reactiveScope { println("third: ${third()}") }

            load {
                third.set(4)
                println("Complete third")
            }
            load {
                second.set(3)
                println("Complete second")
            }
            assertEquals(2, second.value)
            assertEquals(3, third.value)
            setGate.permitOnce()
            assertEquals(3, second.value)
            assertEquals(4, third.value)

            assertEquals(source.value, view.state.get().map { it.value }.also { println("After: $it") })
        }
    }
}
