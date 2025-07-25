package com.lightningkite.signal

import kotlin.test.Test
import kotlin.test.assertEquals

class RememberTests {
    @Test
    fun sharedPassesNulls() {
        val a = LateInitReactiveValue<Int?>()
        val b = remember { a() }
        var hits = 0
        testContext {
            reactiveScope {
                b()
                hits++
            }
            assertEquals(0, hits)
            a.value = null
            assertEquals(1, hits)
            a.value = 1
            assertEquals(2, hits)
        }
    }

    @Test fun sharedDoesNotEmitSameValue() {
        val a = LateInitReactiveValue<Int?>()
        val b = remember { a() }
        var hits = 0
        testContext {
            reactiveScope {
                b()
                hits++
            }
            assertEquals(0, hits)
            a.value = null
            assertEquals(1, hits)
            a.value = null
            assertEquals(1, hits)
        }
    }

    @Test fun sharedTerminatesWhenNoOneIsListening() {
        var onRemoveCalled = 0
        var scopeCalled = 0
        val shared = remember {
            scopeCalled++
            onRemove { onRemoveCalled++ }
            42
        }
        assertEquals(0, scopeCalled)
        assertEquals(0, onRemoveCalled)
        val removeListener = shared.addListener {  }
        assertEquals(1, scopeCalled)
        assertEquals(0, onRemoveCalled)
        removeListener()
        assertEquals(1, scopeCalled)
        assertEquals(1, onRemoveCalled)
    }

    @Test fun sharedTerminatesWhenNoOneIsListeningCancelDeps() {
        var onRemoveCalled = 0
        var scopeCalled = 0
        var dependencyListeners = 0
        val listener = object: Listenable {
            override fun addListener(listener: () -> Unit): () -> Unit {
                dependencyListeners++
                return { dependencyListeners-- }
            }
        }
        val shared = remember {
            rerunOn(listener)
            scopeCalled++
            onRemove { onRemoveCalled++ }
            42
        }
        assertEquals(0, scopeCalled)
        assertEquals(0, onRemoveCalled)
        assertEquals(0, dependencyListeners)
        var removeListener = shared.addListener {  }
        assertEquals(1, dependencyListeners)
        assertEquals(1, scopeCalled)
        assertEquals(0, onRemoveCalled)
        removeListener()
        assertEquals(0, dependencyListeners)
        assertEquals(1, scopeCalled)
        assertEquals(1, onRemoveCalled)
        removeListener = shared.addListener {  }
        assertEquals(1, dependencyListeners)
        assertEquals(2, scopeCalled)
        assertEquals(1, onRemoveCalled)
        removeListener()
        assertEquals(0, dependencyListeners)
        assertEquals(2, scopeCalled)
        assertEquals(2, onRemoveCalled)
    }

    @Test fun sharedSharesCalculations() {
        var hits = 0
        val basicSignal = Signal(1)
        val a = remember {
            hits++
            basicSignal()
        }
        testContext {
            reactiveScope {
                a()
            }
            load {
                a.await()
            }
            reactiveScope {
                a()
            }
            assertEquals(1, hits)

            basicSignal.value = 2
            assertEquals(2, hits)
        }

        // Shouldn't be listening anymore, so it does not trigger a hit
        basicSignal.value = 3
        assertEquals(2, hits)

        testContext {
            reactiveScope {
                a()
            }
            load {
                a.await()
            }
            reactiveScope {
                a()
            }
        }
        assertEquals(3, hits)
    }

    @Test fun sharedReloads() {
        val late = LateInitReactiveValue<Int>()
        var starts = 0
        var hits = 0
        val a = remember {
            starts++
            val r = late()
            hits++
            r
        }
        testContext {
            late.addListener {}
            a.addListener {}

            late.value = 1
            assertEquals(ReactiveState(1), a.state)

            late.unset()
            assertEquals(ReactiveState.notReady, a.state)

            late.value = 2
            assertEquals(ReactiveState(2), a.state)
        }
    }

}