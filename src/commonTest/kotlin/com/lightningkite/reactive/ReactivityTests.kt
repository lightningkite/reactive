package com.lightningkite.reactive

import com.lightningkite.reactive.context.CoroutineScopeHelpers
import com.lightningkite.reactive.context.StatusListener
import com.lightningkite.reactive.context.TypedReactiveContext
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.onRemove
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.addAndRunListener
import com.lightningkite.reactive.extensions.interceptWrite
import com.lightningkite.reactive.extensions.value
import com.lightningkite.reactive.extensions.waitForNotNull
import com.lightningkite.reactive.core.LateInitSignal
import com.lightningkite.reactive.core.RawReactive
import com.lightningkite.reactive.core.Remember
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.test.*

class ReactivityTests {

    @Test
    fun waitingTest() {
        val basicSignal = Signal<Int?>(null)
        val emissions = ArrayList<Int>()
        testContext {
            reactive(action = { emissions.add(basicSignal.waitForNotNull()) })
            repeat(10) {
                basicSignal.value = null
                basicSignal.value = it
            }
        }
        assertEquals((0..9).toList(), emissions)
    }

    @Test
    fun baselineScope() {
        testContext {
            val a = Signal(0)
            var received = -1
            TypedReactiveContext(this, action = {
                received = a()
            }).startCalculation()
            assertEquals(a.value, received)
            a.value++
            assertEquals(a.value, received)
            a.value++
            assertEquals(a.value, received)
            a.value++
            assertEquals(a.value, received)
        }
    }

    @Test
    fun launchReadableAwait() {
        testContext {
            val a = LateInitSignal<Int>()
            var received = -1
            onRemove { println("Shutting down...") }
            launch {
                println("Started...")
                received = a.await()
            }
            println("Setting...")
            a.value = 42
            assertEquals(a.state.get(), received)
        }
    }

    @Test
    fun sharedShutdownTest() {
        val dependency = Signal(0)

        var onRemoveCalled = 0
        var scopeCalled = 0
        val shared = remember(Dispatchers.Unconfined) {
            rerunOn(dependency)    // without a dependency this will shut down immediately
            scopeCalled++
            onRemove { onRemoveCalled++ } // should invoke on loop refresh
            42
        }
        assertEquals(0, scopeCalled)
        assertEquals(0, onRemoveCalled)
        val removeListener = shared.addListener { }
        assertEquals(1, scopeCalled)
        assertEquals(0, onRemoveCalled)
        removeListener()
        assertEquals(1, scopeCalled)
        assertEquals(1, onRemoveCalled)
    }

    @Test
    fun basicer() {
        val a = Signal(1)
        val b = Signal(2)

        testContext {
            reactive(action = { println("Got ${a() + b()}") })
        }
        println("Done.")
    }

    @Test
    fun basics() {
        val a = Signal(1)
        val b = remember(Dispatchers.Unconfined) { Exception("CALC a").printStackTrace(); a() }
        val c = remember(Dispatchers.Unconfined) { Exception("CALC b").printStackTrace(); b() }
        var hits = 0

        testContext {
            reactive(action = {
                            println("#1 Got ${c()}")
                            hits++
                        })
            reactive(action = {
                            println("#2 Got ${c()}")
                            hits++
                        })
            assertEquals(2, hits)
            a.value = 2
            assertEquals(4, hits)
        }
        println("Done.")
    }

    @Test
    fun lateinit() {
        val a = LateInitSignal<Int>()
        var hits = 0

        testContext {
            launch {
                println("launch ${a.await()}")
                hits++
            }
            this@testContext.reactive(action = {
                println("scope ${a()}")
                hits++
            })

            assertEquals(0, hits)
            a.value = 1
            assertEquals(2, hits)
            a.value = 2
            assertEquals(3, hits)
        }
        println("Done.")
    }

    @Test
    fun sharedTest() {
        val a = Signal(1)
        val b = Signal(2)
        var cInvocations = 0
        val c = remember(Dispatchers.Unconfined) { cInvocations++; println("cInvocations: $cInvocations"); a() + b() }
        println("$c: c")
        var dInvocations = 0
        val d = remember(Dispatchers.Unconfined) { dInvocations++; println("dInvocations: $dInvocations"); c() + c() }
        println("$d: d")
        var eInvocations = 0
        val e = remember(Dispatchers.Unconfined) { eInvocations++; println("eInvocations: $eInvocations"); d() / 2 }
        println("$e: e")

        testContext {
            reactive(action = { e() })
            assertEquals(1, cInvocations)
            assertEquals(1, dInvocations)
            assertEquals(1, eInvocations)
            println("a.value = 3")
            a.value = 3
            assertEquals(2, cInvocations)
            assertEquals(2, dInvocations)
            assertEquals(2, eInvocations)
            println("b.value = 4")
            b.value = 4
            assertEquals(3, cInvocations)
            assertEquals(3, dInvocations)
            assertEquals(3, eInvocations)
        }
        println("Done.")
    }

    @Test
    fun sharedTest2() {
        val a = Signal(1)
        val b = Signal(2)
        var cInvocations = 0
        val c = remember(Dispatchers.Unconfined) { cInvocations++; println("cInvocations: $cInvocations"); a() + b() }
        println("$c: c")
        var dInvocations = 0
        val d = remember(Dispatchers.Unconfined) { dInvocations++; println("dInvocations: $dInvocations"); c() + b() }
        println("$d: d")
        var eInvocations = 0
        val e = remember(Dispatchers.Unconfined) { eInvocations++; println("eInvocations: $eInvocations"); d() / 2 }
        println("$e: e")

        testContext {
            reactive(action = { e() })
            assertEquals(1, cInvocations)
            assertEquals(1, dInvocations)
            assertEquals(1, eInvocations)
            println("a.value = 3")
            a.value = 3
            assertEquals(2, cInvocations)
            assertEquals(2, dInvocations)
            assertEquals(2, eInvocations)
            println("b.value = 4")
            b.value = 4
            assertEquals(3, cInvocations)
            assertTrue(4 >= dInvocations)
            assertTrue(4 >= eInvocations)
        }
    }

    @Test
    fun sharedTest3() {
        val a = VirtualDelay { 1 }
        val c = Remember(Dispatchers.Unconfined) { async { a.await() } }
        val d = Remember(Dispatchers.Unconfined) { c() }
        testContext {
            launch { println("launch got " + d.await()) }
            reactive(action = { println("reactiveScope got " + d()) })
            println("Ready... GO!")
            a.go()
        }
    }

    @Test
    fun sharedTest4() {
        val property = LateInitSignal<LateInitSignal<Int>>()
        val shared = remember(Dispatchers.Unconfined) { property()() }
        var completions = 0
        testContext {
            reactive(action = { println("reactiveScope got " + shared()); completions++ })
            launch { println("launch got " + shared.await()); completions++ }
            println("Ready... GO!")
            val lp2 = LateInitSignal<Int>()
            property.value = lp2
            lp2.value = 1
        }
        assertEquals(completions, 2)
    }

    @Test
    fun sharedTest5() {
        val property = LateInitSignal<Int>()
        val shared = remember(Dispatchers.Unconfined) { property() }
        var completions = 0
        testContext {
            launch { println("launchA got " + shared.await()); completions++ }
            launch { println("launchB got " + shared.await()); completions++ }
            println("Ready... GO!")
            property.value = 1
        }
        assertEquals(completions, 2)
    }

    @Test
    fun websocketLikeTest() {
        val source = LateInitSignal<LateInitSignal<String>>()
        val socket = remember(Dispatchers.Unconfined) { source() }
        val sublistener = remember(Dispatchers.Unconfined) { socket()() }
        testContext {
            reactive(action = { println(sublistener()) })
            println("Ready")
            val s2 = LateInitSignal<String>()
            source.value = s2
            s2.value = "A"
            s2.value = "B"
            s2.value = "C"
        }
    }

    @Test
    fun bindTest() {
        val master = LateInitSignal<Int>()
        val secondary = Signal<Int>(0)
        testContext {
            reactive(action = { println("master: ${master()}") })
            reactive(action = { println("secondary: ${secondary()}") })
            secondary bind master
            secondary.value = 1
            master.value = 5

        }
    }

    @Test
    fun dumbtest() {
        val listItem = LateInitSignal<Int>()
        val selected = Signal<Int>(0)
        testContext {
            reactive(action = { println(listItem() == selected()) })
            listItem.value = 1
        }
    }

    @Test
    fun flowtest() {
        testContext {
            val flow = MutableStateFlow(0)
            reactive(action = { println(flow()) })
            repeat(5) { flow.value = it }
        }
    }

    @Test
    fun exceptionReruns() {
        val exceptional = RawReactive<Int>()
        testContext {
            var starts = 0
            var completes = 0
            reactive {
                starts++
                exceptional()
                completes++
            }

            assertEquals(1, starts)
            assertEquals(0, completes)
            exceptional.state = ReactiveState.exception(Exception())
            assertIs<Exception>(expectException())
            assertEquals(2, starts)
            assertEquals(0, completes)
            exceptional.state = ReactiveState.wrap(1)
            assertEquals(3, starts)
            assertEquals(1, completes)
        }
    }

    @Test
    fun bind() {
        val waitGates = ArrayList<WaitGate>()
        fun permit(count: Int) {
            repeat(count) { println("Permit one"); waitGates.removeFirstOrNull()?.permit = true }
        }

        fun permitAll() {
            waitGates.removeAll { println("Permit one"); it.permit = true; true }
        }

        val a = Signal(1)
        val b = Signal(1).interceptWrite {
            WaitGate().also { waitGates += it }.await()
            set(it)
        }
        testContext {
            reactive { println("A: ${a()}, B: ${b()}") }
            a bind b
            launch { a set 2 }
            permitAll()
            launch { a set 3 }
            launch { a set 4 }
            permitAll()
        }
    }

    @Test
    fun innerScopeIsCancelledOnRefresh() {
        testContext {
            var cancelled = 0
            val dependency = Signal(0)
            reactive {
                dependency()
                onRemove { cancelled += 1 }
            }
            assertEquals(0, cancelled)
            dependency.value += 1
            assertEquals(1, cancelled)
            dependency.value += 1
            assertEquals(2, cancelled)
        }
    }

    @Test
    fun nestedScopesWorks() {
        testContext {
            var outerCancelled = 0
            var innerCancelled = 0
            val nestedDependency = Signal(Signal(0))

            reactive {
                val inner = nestedDependency()
                reactive {
                    inner()
                    onRemove { innerCancelled += 1 }
                }
                onRemove { outerCancelled += 1 }
            }

            assertEquals(0, outerCancelled)
            assertEquals(0, innerCancelled)

            nestedDependency.value.value = 1

            assertEquals(0, outerCancelled)
            assertEquals(1, innerCancelled)

            nestedDependency.value.value = 2

            assertEquals(0, outerCancelled)
            assertEquals(2, innerCancelled)

            nestedDependency.value = Signal(0)

            assertEquals(1, outerCancelled)
            assertEquals(3, innerCancelled)

            nestedDependency.value.value = 1

            assertEquals(1, outerCancelled)
            assertEquals(4, innerCancelled)

            nestedDependency.value.value = 2

            assertEquals(1, outerCancelled)
            assertEquals(5, innerCancelled)
        }
    }

//    @Test fun sharedProcessTest() {
//        val gate = WaitGate()
//        val x = sharedProcess<Int>(GlobalScope) {
//            emit(1)
//            emit(2)
//            emit(3)
//            gate.permitOnce()
//        }
//        testContext {
//            println("Setting up A")
//            reactive { println("A:" + x()) }
//            launch { gate.await() }
//            println("Tearing down A")
//        }
//        testContext {
//            println("Setting up B")
//            reactive { println("B:" + x()) }
//            launch { gate.await() }
//            println("Tearing down B")
//        }
//    }
//
//    @Test fun sharedProcessRawTest() {
//        val gate = WaitGate()
//        val x = sharedProcessRaw<Int>(GlobalScope) {
//            emit(ReadableState.notReady)
//            emit(ReadableState(1))
//            emit(ReadableState(2))
//            emit(ReadableState(3))
//            gate.permitOnce()
//        }
//        testContext {
//            println("Setting up A")
//            reactive { println("A:" + x()) }
//            launch { gate.await() }
//            println("Tearing down A")
//        }
//        testContext {
//            println("Setting up B")
//            reactive { println("B:" + x()) }
//            launch { gate.await() }
//            println("Tearing down B")
//        }
//    }
}

class VirtualDelay<T>(val action: () -> T) {
    val continuations = ArrayList<Continuation<T>>()
    var value: T? = null
    var ready: Boolean = false
    suspend fun await(): T {
        if (ready) return value as T
        return suspendCancellableCoroutine {
            continuations.add(it)
        }
    }

    fun clear() {
        ready = false
    }

    fun go() {
        val value = action()
        this.value = value
        ready = true
        for (continuation in continuations) {
            continuation.resume(value)
        }
        continuations.clear()
    }
}

class VirtualDelayer() {
    val continuations = ArrayList<Continuation<Unit>>()
    suspend fun await(): Unit {
        return suspendCancellableCoroutine {
            continuations.add(it)
        }
    }

    fun go() {
        for (continuation in continuations) {
            continuation.resume(Unit)
        }
        continuations.clear()
    }
}

class TestContext : CoroutineScopeHelpers {
    var error: Throwable? = null
    val job = Job()
    var loadCount = 0
    fun expectException(): Throwable {
        val e = error ?: fail("Expected exception but there was none")
        error = null
        return e
    }

    val incompleteKeys = HashSet<Any>()
    override val coroutineContext: CoroutineContext =
        job +
                CoroutineExceptionHandler { ctx, it ->
                    error = it
                    job.cancel()
                } +
                Dispatchers.Unconfined +
                object : StatusListener {
        override fun watchBackgroundProcess(status: Reactive<*>): () -> Unit {
            var loading = false
            var excEnder: (() -> Unit)? = null
            return status.addAndRunListener {
                val s = status.state
                println("${status} reports ${s}")
                if (loading != !s.ready) {
                    if (s.ready) {
                        loadCount--
                    } else {
                        loadCount++
                    }
                    loading = !s.ready
                }
                excEnder?.invoke()
                s.exception?.let { t ->
                    t.printStackTrace()
                    error = t
                }
            }.also { onRemove(it) }
        }
    }
}

fun testContext(action: TestContext.() -> Unit) {
    with(TestContext()) {
        action()
        job.cancel()
        if (error != null) throw Exception("Unexpected error", error!!)
        assertEquals(0, loadCount, "Some work was not completed: ${incompleteKeys}")
    }
}