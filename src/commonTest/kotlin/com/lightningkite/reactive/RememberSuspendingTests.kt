package com.lightningkite.reactive

import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.onRemove
import com.lightningkite.reactive.context.reactiveSuspending
import com.lightningkite.reactive.context.rerunOn
import com.lightningkite.reactive.core.BasicListenable
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.extensions.value
import com.lightningkite.reactive.core.LateInitSignal
import com.lightningkite.reactive.core.Remember
import com.lightningkite.reactive.core.RememberSuspending
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.rememberSuspending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class RememberSuspendingTests {
    @Test
    fun sharedPassesNulls() {
        val a = LateInitSignal<Int?>()
        val b = rememberSuspending(Dispatchers.Unconfined) { a() }
        var hits = 0
        testContext {
            reactiveSuspending {
                Exception("Calculating...").printStackTrace()
                b()
                hits++
                println("Calculated")
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
        val b = rememberSuspending(Dispatchers.Unconfined) { a() }
        var hits = 0
        testContext {
            reactiveSuspending {
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
        val dependency = BasicListenable()
        var onRemoveCalled = 0
        var scopeCalled = 0
        val sharedSuspending = rememberSuspending(Dispatchers.Unconfined) {
            rerunOn(dependency)
            scopeCalled++
            onRemove { onRemoveCalled++ }
            42
        }
        assertEquals(0, scopeCalled)
        assertEquals(0, onRemoveCalled)
        val removeListener = sharedSuspending.addListener {  }
        assertEquals(1, scopeCalled)
        assertEquals(0, onRemoveCalled)
        removeListener()
        assertEquals(1, scopeCalled)
        assertEquals(1, onRemoveCalled)
    }

    @Test fun sharedSharesCalculations() {
        var hits = 0
        val basicSignal = Signal(1)
        val a = rememberSuspending(Dispatchers.Unconfined) {
            hits++
            basicSignal()
        }
        testContext {
            reactiveSuspending {
                a()
            }
            load {
                a.await()
            }
            reactiveSuspending {
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
            reactiveSuspending {
                a()
            }
            load {
                a.await()
            }
            reactiveSuspending {
                a()
            }
        }
        assertEquals(3, hits)
    }

    @Test fun sharedReloads() {
        val late = LateInitSignal<Int>()
        var starts = 0
        var hits = 0
        val a = RememberSuspending(incomingCoroutineContext = Dispatchers.Unconfined, useLastWhileLoading = false) {
            starts++
            val r = late()
            hits++
            r
        }
        testContext {
            late.addListener {}
            a.addListener {}

            println("listeners added")

            println("late.value = 1")
            late.value = 1
            println("late.value = 1 done")
            assertEquals(ReactiveState(1), a.state)

            println("late.unset()")
            late.unset()
            println("late.unset() done")
            assertEquals(ReactiveState.notReady, a.state)

            late.value = 2
            assertEquals(ReactiveState(2), a.state)
        }
    }


    @Test
    fun canDelayDeactivation() {
        val signal = Signal(0)
        var hits = 0
        val remember = RememberSuspending(deactivationDelay = 100.milliseconds) { hits++; signal() }
        runTest {
            launch(Dispatchers.Default) {
                assertEquals(0, hits)
                var remover = remember.addListener { }
                assertEquals(1, hits)
                // remove the listener, should delay before shutting down
                remover()
                assertEquals(1, hits)
                remover = remember.addListener { }
                assertEquals(1, hits)

                // should still shut down after deactivationDelay
                val job = launch {
                    remover()
                    println("TEST: Launch delay starting")
                    delay(101.milliseconds)
                    println("TEST: Launch continuing")
                    remover = remember.addListener { }
                    assertEquals(2, hits)
                    remover()
                }
                job.join()
            }
        }
    }
}