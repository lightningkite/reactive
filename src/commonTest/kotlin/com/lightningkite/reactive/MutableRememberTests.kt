package com.lightningkite.reactive

import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.onRemove
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.context.reactiveScope
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.extensions.value
import com.lightningkite.reactive.core.LateInitSignal
import com.lightningkite.reactive.core.MutableRemember
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.mutableRemember
import com.lightningkite.reactive.lensing.lens
import kotlin.test.Test
import kotlin.test.assertEquals

class MutableRememberRememberTests {
    @Test
    fun sharedPassesNulls() {
        val a = LateInitSignal<Int?>()
        val b = MutableRemember { a() }
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
        val a = LateInitSignal<Int?>()
        val b = MutableRemember { a() }
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
        val shared = MutableRemember {
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

    @Test fun sharedSharesCalculations() {
        var hits = 0
        val basicSignal = Signal(1)
        val a = MutableRemember {
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
        val late = LateInitSignal<Int>()
        var starts = 0
        var hits = 0
        val a = MutableRemember {
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

class MutableRememberTests {
    @Test fun sharedIsOverridden() {
        val late = LateInitSignal<Int>()
        val test = MutableRemember {
            println("In initial value")
            late()
        }
        testContext {
            test.addListener {  }

            assertEquals(ReactiveState.notReady, test.state)

            late.value = 1
            assertEquals(ReactiveState(1), test.state)

            test.value = 2
            assertEquals(ReactiveState(2), test.state)
        }
    }

    @Test fun stopsListeningWhenOverridden() {
        var hits: Int = 0
        val prop = Signal(1)
        val test = mutableRemember {
            hits++
            prop()
        }
        testContext {
            assertEquals(0, hits)

            test.addListener {  }
            assertEquals(1, hits)

            prop.value = 2
            assertEquals(ReactiveState(2), test.state)
            assertEquals(2, hits)

            test.value = 0
            assertEquals(ReactiveState(0), test.state)

            prop.value = 3
            assertEquals(ReactiveState(0), test.state)
            assertEquals(2, hits)
        }
    }

    @Test fun startsListeningAgainOnceReset() {
        var hits: Int = 0
        val prop = Signal(1)
        val test = MutableRemember {
            hits++
            prop()
        }
        testContext {
            assertEquals(0, hits)

            test.addListener {  }
            assertEquals(1, hits)

            prop.value = 2
            assertEquals(ReactiveState(2), test.state)
            assertEquals(2, hits)

            test.value = 0
            assertEquals(ReactiveState(0), test.state)
            assertEquals(2, hits)

            prop.value = 3
            assertEquals(2, hits)

            test.reset()
            assertEquals(ReactiveState(3), test.state)

            prop.value = 4
            assertEquals(ReactiveState(4), test.state)
        }
    }

    @Test fun useLastWhileLoadingWorks() {
        val late = LateInitSignal<Int>()
        val test = MutableRemember(useLastWhileLoading = true) {
            late()
        }

        testContext {
            test.addListener {  }

            assertEquals(ReactiveState.notReady, test.state)

            late.value = 1
            assertEquals(ReactiveState(1), test.state)

            test.value = 10
            assertEquals(ReactiveState(10), test.state)

            late.unset()
            assertEquals(ReactiveState(10), test.state)

            test.reset()
            assertEquals(ReactiveState(1), test.state)

            late.value = 2
            assertEquals(ReactiveState(2), test.state)
        }
    }

    @Test fun keepsListeningIfTold() {
        var hits = 0
        val prop = Signal(0)
        val test = MutableRemember(stopListeningWhenOverridden = false) {
            println("Calculation")
            hits++
            prop()
        }

        testContext {
            assertEquals(0, hits)

            println("Adding listener...")
            test.addListener {  }
            println("Added listener.")

            assertEquals(1, hits)
            assertEquals(ReactiveState(0), test.state)

            prop.value = 1

            assertEquals(2, hits)
            assertEquals(ReactiveState(1), test.state)

            test.value = 10

            assertEquals(2, hits)
            assertEquals(ReactiveState(10), test.state)

            prop.value = 2

            assertEquals(3, hits)
            assertEquals(ReactiveState(10), test.state)

            test.reset()

            assertEquals(3, hits)
            assertEquals(ReactiveState(2), test.state)
        }
    }

    @Test fun testStupidCase() {
        val basis = Signal("Test")
        val lazy = MutableRemember(stopListeningWhenOverridden = false) { basis() }
        val lensed = lazy.lens { it.take(3) }
        val lensed2 = lazy.lens(get = { it.take(3) }, modify = { o, it -> it })
        testContext {
            println(lensed.state)
            load {
                assertEquals("Tes", lensed())
            }
            load {
                assertEquals("Tes", lensed2())
            }
        }
    }

    @Test fun testStupidCase2() {
        val basis = Signal("Test")
        val lazy = MutableRemember(stopListeningWhenOverridden = false) { basis() }
        val lensed = lazy.lens { it.take(3) }
        val lensed2 = lazy.lens(get = { it.take(3) }, modify = { o, it -> it })
        var value = ""
        var value2 = ""
        testContext {
            println(lensed.state)
            reactive {
                println("Starting")
                value = lensed()
                println(value)
            }
            reactive {
                println("Starting")
                value2 = lensed2()
                println(value2)
            }
            assertEquals("Tes", value)
            assertEquals("Tes", value2)
        }
    }
}