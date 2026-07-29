package com.lightningkite.reactive

import com.lightningkite.reactive.context.CoroutineScopeHelpers
import com.lightningkite.reactive.context.StatusListener
import com.lightningkite.reactive.context.TypedReactiveContext
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.onRemove
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.context.ReactiveReentrancyException
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.addAndRunListener
import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.extensions.flatten
import com.lightningkite.reactive.extensions.interceptWrite
import com.lightningkite.reactive.extensions.onNextSuccess
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.reactive.extensions.value
import com.lightningkite.reactive.extensions.waitForNotNull
import com.lightningkite.reactive.core.LateInitSignal
import com.lightningkite.reactive.core.RawReactive
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.NotActiveException
import com.lightningkite.reactive.core.Remember
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

    @Test
    fun onLoadFiresWhenEnteringLoading() {
        testContext {
            val signal = LateInitSignal<Int>()
            var loadCalls = 0
            reactive(onLoad = { loadCalls++ }) {
                signal()
            }
            // Starts not-ready, so entering the calculation is a transition into loading.
            assertEquals(1, loadCalls, "onLoad should fire once when first entering loading")

            signal.value = 5
            assertEquals(1, loadCalls, "onLoad should not fire again once the value is ready")

            signal.unset()
            assertEquals(2, loadCalls, "onLoad should fire again when re-entering loading")

            // Leave in a ready state so testContext's loadCount balance check passes.
            signal.value = 6
        }
    }

    @Test
    fun reentrancyThrowsClearError() {
        testContext {
            val s = Signal(0)
            // Writing to a signal the calculation also reads re-triggers this same calculation.
            // Without reentrancy detection this recurses until the stack overflows.
            reactive {
                val v = s()
                s.value = v + 1
            }
            val captured = expectException()
            assertTrue(
                captured is ReactiveReentrancyException,
                "Expected a ReactiveReentrancyException, but captured: $captured"
            )
        }
    }

    @Test
    fun reentrancyErrorSaysWhichProblemItIs() {
        // A calculation that opted into settling needs to hear that its budget ran out. Telling it
        // to stop writing its own dependencies is advice it has already declined.
        val unwanted = testContext {
            val s = Signal(0)
            reactive { val v = s(); s.value = v + 1 }
            expectException().message
        }
        val unsettled = testContext {
            val s = Signal(0)
            reactive(reentrancyLimit = 3) { val v = s(); s.value = v + 1 }
            expectException().message
        }
        assertNotEquals(unwanted, unsettled)
    }

    @Test
    fun partialSettlingReentrancy() {
        testContext {
            val s = Signal(0)
            reactive(reentrancyLimit = 2) {
                val v = s()
                if (v > 5) return@reactive
                s.value = v + 1
            }
            assertIs<ReactiveReentrancyException>(expectException())
        }
    }

    @Test
    fun settlingReentrancy() {
        testContext {
            val s = Signal(0)
            val ctx = reactive(reentrancyLimit = 10) {
                val v = s()
                if (v > 5) return@reactive
                s.value = v + 1
            }
            assertEquals(ReactiveState(6), s.state)
            assertNull(ctx.state.exception, "the calculation settled, so it should not have failed")
        }
    }

    @Test
    fun settlingExactlyAtTheLimitSucceeds() {
        // Climbing to six takes six writes, so six self-triggered reruns: precisely the budget.
        testContext {
            val s = Signal(0)
            val ctx = reactive(reentrancyLimit = 6) {
                val v = s()
                if (v > 5) return@reactive
                s.value = v + 1
            }
            assertEquals(ReactiveState(6), s.state)
            assertNull(ctx.state.exception)
        }
    }

    @Test
    fun settlingOneShortOfTheLimitFails() {
        testContext {
            val s = Signal(0)
            reactive(reentrancyLimit = 5) {
                val v = s()
                if (v > 5) return@reactive
                s.value = v + 1
            }
            assertIs<ReactiveReentrancyException>(expectException())
        }
    }

    @Test
    fun settlingPublishesEachIntermediateValue() {
        // Deliberate: a settling calculation publishes every step rather than only its fixed point.
        // That is what lets two settling calculations drive each other along, as the co-reentrancy
        // tests below rely on.
        testContext {
            val s = Signal(0)
            val observed = ArrayList<Int>()
            reactive { observed.add(s()) }
            reactive(reentrancyLimit = 10) {
                val v = s()
                if (v > 5) return@reactive
                s.value = v + 1
            }
            assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), observed)
        }
    }

    @Test
    fun eachExternalChangeGetsAFreshReentrancyBudget() {
        // The limit bounds one settling sequence, not the lifetime of the calculation. A
        // calculation that self-triggers once per change has to keep working indefinitely.
        testContext {
            val trigger = Signal(0)
            val clamped = Signal(0)
            val ctx = reactive(reentrancyLimit = 1) {
                val t = trigger()
                if (clamped() != t) clamped.value = t
            }
            repeat(20) { trigger.value = it + 1 }
            assertEquals(ReactiveState(20), clamped.state)
            assertNull(ctx.state.exception)
        }
    }

    @Test
    fun aContextRecoversAfterAReentrancyError() {
        // The failed run keeps the dependencies of its last complete pass, so a later change still
        // reaches the calculation and it computes normally once the cycle is gone.
        testContext {
            val cycle = Signal(0)
            val other = Signal("a")
            var runaway = true
            val ctx = reactive {
                val o = other()
                if (runaway) cycle.value = cycle() + 1
                o
            }
            assertIs<ReactiveReentrancyException>(expectException())

            runaway = false
            other.value = "b"
            assertEquals(ReactiveState("b"), ctx.state)
        }
    }

    @Test
    fun aFlowsFirstValueIsNotReentrancy() {
        // A cold flow collects synchronously under an undispatched scheduler, so its first value
        // lands while the calculation that registered it is still running. That is a dependency
        // arriving, not the calculation triggering itself, so it must not spend the budget.
        testContext {
            val flow = flowOf(7)
            val seen = ArrayList<Int>()
            val ctx = reactive { seen.add(flow()) }
            assertEquals(listOf(7), seen)
            assertNull(ctx.state.exception)
        }
    }

    @Test
    fun aStateFlowsCurrentValueIsNotReentrancy() {
        testContext {
            val flow = MutableStateFlow(1)
            val seen = ArrayList<Int>()
            val ctx = reactive { seen.add(flow()) }
            flow.value = 2
            assertEquals(listOf(1, 2), seen)
            assertNull(ctx.state.exception)
        }
    }

    @Test
    fun rememberDefaultsToRejectingReentrancy() {
        testContext {
            val s = Signal(0)
            val r = remember { val v = s(); s.value = v + 1; v }
            reactive { r() }
            assertIs<ReactiveReentrancyException>(expectException())
        }
    }

    @Test
    fun rememberPassesItsReentrancyLimitThrough() {
        testContext {
            val s = Signal(0)
            val settled = remember(reentrancyLimit = 10) {
                val v = s()
                if (v <= 5) s.value = v + 1
                v
            }
            val seen = ArrayList<Int>()
            reactive { seen.add(settled()) }
            assertEquals(ReactiveState(6), s.state)
            assertEquals(6, seen.last())
        }
    }

    @Test
    fun partialSettlingCoReentrancy() {
        testContext {
            val a = Signal(0)
            val b = Signal(0)
            reactive(reentrancyLimit = 3) {
                val other = b()
                if (other < 10) a.value = other + 1
            }
            reactive(reentrancyLimit = 3) {
                val other = a()
                if (other < 10) b.value = other + 1
            }
            assertIs<ReactiveReentrancyException>(expectException())
        }
    }

    @Test
    fun settlingCoReentrancy() {
        // Two calculations driving each other converge as long as each one's own budget covers the
        // reruns it personally performs. They are not symmetric: the first context is re-entered
        // from scratch by the second - so its budget is never spent - while the second settles in
        // place and pays for every step.
        testContext {
            val a = Signal(0)
            val b = Signal(0)
            val firstSaw = ArrayList<Int>()
            val secondSaw = ArrayList<Int>()
            reactive(reentrancyLimit = 10) {
                val other = b()
                firstSaw.add(other)
                if (other < 10) a.value = other + 1
            }
            reactive(reentrancyLimit = 10) {
                val other = a()
                secondSaw.add(other)
                if (other < 10) b.value = other + 1
            }
            assertEquals(ReactiveState(9), a.state)
            assertEquals(ReactiveState(10), b.state)
            assertEquals(listOf(0, 2, 4, 6, 8, 10), firstSaw)
            assertEquals(listOf(1, 3, 5, 7, 9), secondSaw)
        }
    }

    @Test
    fun writingUnrelatedSignalDuringCalculationIsAllowed() {
        testContext {
            val trigger = Signal(0)
            val unrelated = Signal(100)
            var runs = 0
            reactive {
                runs++
                val t = trigger()
                // Writing to a signal this calculation does NOT read must remain legal.
                unrelated.value = t + 100
            }
            assertEquals(1, runs)
            assertEquals(100, unrelated.value)
            trigger.value = 5
            assertEquals(2, runs)
            assertEquals(105, unrelated.value)
        }
    }

    @Test
    fun asyncDistinguishesCallSitesWithIdenticalDependencies() = runTest {
        // Two different `async {}` call sites passed the exact same dependency (1). Under the old
        // deps-only cache key, both would hash/equal to the same SuspendCalculation and the second
        // call site would silently reuse the first's result.
        var resultA = -1
        var resultB = -1
        val ctx = TypedReactiveContext(this) {
            resultA = async("a", 1) { 100 }
            resultB = async("b", 1) { 200 }
        }
        ctx.startCalculation()
        runCurrent()

        assertEquals(100, resultA, "first call site should get its own result")
        assertEquals(200, resultB, "second call site must not have collided with the first")

        ctx.cancel()
    }

    @Test
    fun asyncCancelsStaleLaunchOnRerun() = runTest {
        // The async's own dependency is `trigger()`'s value, so changing `trigger` gives the async
        // block a new cache key each run - the old entry becomes unused and should be torn down
        // (job cancelled) rather than left running to write into an orphaned calc.
        val trigger = Signal(0)
        var completions = 0
        val ctx = TypedReactiveContext(this) {
            val t = trigger()
            async("A", t) {
                delay(100)
                completions++
            }
        }
        ctx.startCalculation()
        runCurrent()
        assertEquals(0, completions, "first run's async should still be delaying")

        trigger.value = 1 // gives the async a new key; the stale (t=0) launch must be cancelled
        runCurrent()
        assertEquals(0, completions, "second run's async should also still be delaying")

        // Let the stale run's delay(100) elapse, as if nothing had cancelled it.
        advanceTimeBy(150)
        runCurrent()

        // Only the fresh (t=1) async should ever complete - the cancelled (t=0) one must not.
        assertEquals(1, completions, "the cancelled first launch must not have completed")

        ctx.cancel()
    }

    @Test
    fun changingADependencyEndsThePreviousRunImmediately() = runTest {
        // Under a dispatching scheduler the rerun does not begin until the scheduler gets to it,
        // but the run it supersedes is finished the moment its input changed - whatever that run
        // launched is now computing from a stale value. Cleanup must not wait for the rerun.
        val trigger = Signal(0)
        val cleanedUp = ArrayList<Int>()
        val ctx = TypedReactiveContext(this) {
            val t = trigger()
            onRemove { cleanedUp.add(t) }
        }
        ctx.startCalculation()
        runCurrent()
        assertEquals(listOf(), cleanedUp, "the first run is still current")

        trigger.value = 1
        assertEquals(
            listOf(0), cleanedUp,
            "the superseded run should be torn down at the change, not when the rerun is dispatched"
        )

        runCurrent()
        ctx.cancel()
    }

    @Test
    fun readingStateWithoutListenersDoesNotCalculate() {
        val source = Signal(1)
        var computeCount = 0
        val r = remember {
            computeCount++
            source()
        }

        // No listener added, so the Remember is lazy and has never calculated.
        assertEquals(0, source.listenerCount, "precondition: no listeners before reading state")

        assertEquals(ReactiveState.notActive, r.state, "a Remember with no listeners maintains no value")
        assertEquals(0, computeCount, "reading state must not calculate")
        assertEquals(0, source.listenerCount, "reading state must not subscribe to sources")

        // Mutating the source must not resurrect it either.
        source.value = 2
        assertEquals(0, computeCount, "mutating a source must not calculate a Remember nobody listens to")
        assertEquals(0, source.listenerCount, "source must still have zero listeners after mutation")

        // Gaining a listener is what makes it calculate, and losing it stops it again.
        val release = r.addListener { }
        assertEquals(2, r.state.get(), "activating calculates the current value")
        assertEquals(1, computeCount)
        assertEquals(1, source.listenerCount)
        release()
        assertEquals(0, source.listenerCount, "deactivating releases the subscription")

        // Going dormant drops the value rather than reporting one that is no longer maintained.
        assertEquals(ReactiveState.notActive, r.state, "a dormant Remember must not report its last value")
        source.value = 3
        assertEquals(1, computeCount, "a dormant Remember must not recalculate")

        // Listening again recalculates against the source as it is now.
        r.addListener { }
        assertEquals(3, r.state.get(), "reactivating recalculates rather than reporting the old value")
    }

    // The tests below cover reading a lazy source: because a Remember only calculates while
    // something is listening, and BaseListenable activates before the new listener is in the list,
    // every one of these has to subscribe before reading or it sees the pre-activation state.

    @Test
    fun lensOfLazySourceGetsValueOnActivation() {
        val source = Signal(1)
        val lensed = remember { source() }.lens { it * 10 }
        val release = lensed.addListener { }
        assertEquals(10, lensed.state.get(), "activating a lens must pick up the value its source calculates")
        source.value = 2
        assertEquals(20, lensed.state.get())
        release()
    }

    @Test
    fun onceObtainsValueFromLazySource() {
        val source = Signal(1)
        val r = remember { source() }
        val seen = ArrayList<Int>()
        testContext {
            reactive { seen.add(r.once()) }
            assertEquals(listOf(1), seen, "once must obtain a value from a lazy source")
            source.value = 2
            source.value = 3
            assertEquals(listOf(1), seen, "once must not rerun the calculation, nor change what it reports")
        }
    }

    @Test
    fun stateTransformSeesValueFromActivation() {
        val source = Signal(1)
        val r = remember { source() }
        val seen = ArrayList<Boolean>()
        testContext {
            reactive { seen.add(r.state { it.ready }) }
            assertEquals(listOf(true), seen, "state(get) must see the value its subscription caused")
            source.value = 2
            assertEquals(listOf(true), seen, "readiness did not change, so there is nothing to rerun")
        }
    }

    @Test
    fun onNextSuccessFiresForLazySource() {
        val source = Signal(1)
        val seen = ArrayList<Int>()
        remember { source() }.onNextSuccess { seen.add(it) }
        assertEquals(listOf(1), seen, "onNextSuccess must obtain a value from a lazy source")
        source.value = 2
        assertEquals(listOf(1), seen, "onNextSuccess fires exactly once")
    }

    @Test
    fun writingThroughFlattenedLazySourceReachesTheTarget() = runTest {
        val inner = Signal(1)
        val selection = Signal<MutableReactive<Int>>(inner)
        // The outer reactive is lazy, so the write has to activate it to find the target.
        val outer: Reactive<MutableReactive<Int>> = remember { selection() }
        outer.flatten().set(42)
        assertEquals(42, inner.value, "a write through flatten must not be silently dropped")
    }

    @Test
    fun notActiveIsDistinctFromLoading() {
        val source = LateInitSignal<Int>()
        val r = remember { source() }

        assertEquals(ReactiveState.notActive, r.state, "nobody is maintaining this")

        val release = r.addListener { }
        assertEquals(
            ReactiveState.notReady, r.state,
            "now somebody is maintaining it - it just doesn't have a value yet"
        )

        source.value = 1
        assertEquals(ReactiveState(1), r.state)

        release()
        assertEquals(ReactiveState.notActive, r.state, "and back to nobody maintaining it")
    }

    @Test
    fun notActivePropagatesThroughLenses() {
        val source = Signal(1)
        val r = remember { source() }
        val lensed = r.lens { it * 10 }

        assertTrue(lensed.state.notActive, "a lens over an unmaintained value is equally unmaintained")

        val release = lensed.addListener { }
        assertEquals(ReactiveState(10), lensed.state)
        release()
        assertTrue(lensed.state.notActive)
    }

    @Test
    fun lensOfAlwaysDefiniteSourceNeedsNoListeners() {
        // A Signal's value is accurate whether or not anyone is listening, so a lens over one can
        // be read directly - this is what notActive being a separate state buys.
        val source = Signal(1)
        val lensed = source.lens { it * 10 }
        assertEquals(ReactiveState(10), lensed.state)
        source.value = 2
        assertEquals(ReactiveState(20), lensed.state)
    }

    @Test
    fun readingAValueSomebodyElseMaintainsIsFree() = runTest {
        val source = Signal(1)
        var computeCount = 0
        val r = remember {
            computeCount++
            source()
        }
        val release = r.addListener { }
        assertEquals(1, computeCount)

        // Someone else is keeping this current, so reading it must not re-run anything or churn
        // the subscription.
        assertEquals(1, r.awaitOnce())
        assertEquals(1, computeCount, "awaitOnce must not recalculate a value already being maintained")
        assertEquals(1, source.listenerCount, "awaitOnce must not disturb the existing subscription")

        release()
    }

    @Test
    fun readingNotActiveStateThrowsSomethingExplanatory() {
        val r = remember { Signal(1)() }
        @Suppress("DEPRECATION")
        val thrown = assertFailsWith<NotActiveException> { r.state.get() }
        assertTrue(thrown.message!!.contains("listening"), "the message should say what to do about it")
    }

    @Test
    fun awaitOnceCalculatesAndReleases() = runTest {
        val source = Signal(1)
        var computeCount = 0
        val r = remember {
            computeCount++
            source()
        }

        assertEquals(1, r.awaitOnce(), "awaitOnce must calculate a dormant Remember")
        assertEquals(1, computeCount)
        assertEquals(0, source.listenerCount, "awaitOnce must not hold onto its subscription")

        source.value = 2
        assertEquals(2, r.awaitOnce(), "awaitOnce must recalculate rather than report a stale value")
        assertEquals(0, source.listenerCount)
    }
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

inline fun <T> testContext(action: TestContext.() -> T): T =
    with(TestContext()) {
        val r = action()
        job.cancel()
        if (error != null) throw Exception("Unexpected error", error!!)
        assertEquals(0, loadCount, "Some work was not completed: ${incompleteKeys}")
        r
    }