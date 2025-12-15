package com.lightningkite.reactive

import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactiveScope
import com.lightningkite.reactive.context.reactiveSuspending
import com.lightningkite.reactive.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import com.lightningkite.reactive.context.invoke

/**
 * Tests for race conditions and timing issues in the reactive library.
 * These tests specifically target the scenario described: "I created a 'remember', but the
 * first reactive scope that listened to it didn't get the value. The second one did"
 */
class RaceConditionTests {

    @Test
    fun firstListenerGetsValue() {
        // Test that the first listener immediately gets the value from a remember
        val signal = Signal(42)
        val remember = remember { signal() }

        var firstListenerValue: Int? = null
        var firstListenerHits = 0

        testContext {
            reactiveScope {
                firstListenerValue = remember()
                firstListenerHits++
            }

            // First listener should have gotten the value
            assertEquals(42, firstListenerValue, "First listener should get the initial value")
            assertEquals(1, firstListenerHits, "First listener should have been called once")
        }
    }

    @Test
    fun firstAndSecondListenerBothGetInitialValue() {
        // Test that both first and second listeners get the value
        val signal = Signal(100)
        val remember = remember { signal() }

        var firstValue: Int? = null
        var secondValue: Int? = null
        var firstHits = 0
        var secondHits = 0

        testContext {
            reactiveScope {
                firstValue = remember()
                firstHits++
            }

            reactiveScope {
                secondValue = remember()
                secondHits++
            }

            assertEquals(100, firstValue, "First listener should get value")
            assertEquals(100, secondValue, "Second listener should get value")
            assertEquals(1, firstHits, "First listener should be called once")
            assertEquals(1, secondHits, "Second listener should be called once")
        }
    }

    @Test
    fun rememberActivatesOnFirstListener() {
        // Test that remember calculation starts when first listener is added
        var calculationRuns = 0
        val signal = Signal(1)
        val remember = remember {
            calculationRuns++
            signal()
        }

        assertEquals(0, calculationRuns, "Should not calculate before any listeners")

        testContext {
            reactiveScope {
                remember()
            }

            assertEquals(1, calculationRuns, "Should calculate when first listener is added")
        }
    }

    @Test
    fun multipleListenersShareCalculation() {
        // Test that multiple listeners share the same calculation
        var calculationRuns = 0
        val signal = Signal(5)
        val remember = remember {
            calculationRuns++
            signal()
        }

        testContext {
            val values = mutableListOf<Int>()

            // Add multiple listeners at once
            reactiveScope {
                values.add(remember())
            }

            reactiveScope {
                values.add(remember())
            }

            reactiveScope {
                values.add(remember())
            }

            // All should get the same value
            assertEquals(listOf(5, 5, 5), values, "All listeners should get the same value")
            // Should only calculate once
            assertEquals(1, calculationRuns, "Should only calculate once for all listeners")
        }
    }

    @Test
    fun rememberWithAsyncDependency() {
        // Test remember with a dependency that becomes ready asynchronously
        val lateInit = LateInitSignal<Int>()
        val remember = remember { lateInit() }

        var firstValue: Int? = null
        var secondValue: Int? = null
        var firstHits = 0
        var secondHits = 0

        testContext {
            reactiveScope {
                firstValue = remember()
                firstHits++
            }

            reactiveScope {
                secondValue = remember()
                secondHits++
            }

            // Both should be waiting
            assertEquals(0, firstHits, "First listener should be waiting")
            assertEquals(0, secondHits, "Second listener should be waiting")

            // Set the value
            lateInit valueSet 77

            // Both should now have the value
            assertEquals(77, firstValue, "First listener should get value after it's set")
            assertEquals(77, secondValue, "Second listener should get value after it's set")
            assertEquals(1, firstHits, "First listener should fire once")
            assertEquals(1, secondHits, "Second listener should fire once")
        }
    }

    @Test
    fun shuttingDownRaceCondition() {
        // Test for race condition when shuttingDown is in progress
        runTest {
            val signal = Signal(0)
            var hits = 0
            val remember = Remember(deactivationDelay = 50.milliseconds) {
                hits++
                signal()
            }

            launch(Dispatchers.Default) {
                // Add and remove listener quickly
                val remover1 = remember.addListener { }
                assertEquals(1, hits, "Should calculate on first listener")
                remover1()

                // Wait a bit but not enough for full shutdown
                delay(25.milliseconds)

                // Add another listener while shutdown is pending
                val remover2 = remember.addListener { }

                // Should not recalculate (reuse existing context)
                assertEquals(1, hits, "Should reuse context, not recalculate")

                // Clean up
                remover2()
                delay(60.milliseconds)

                // Now add a listener after full shutdown
                val remover3 = remember.addListener { }
                assertEquals(2, hits, "Should recalculate after full shutdown")
                remover3()
            }
        }
    }

    @Test
    fun rememberSuspendingFirstListenerGetsValue() {
        // Test the suspending version
        val signal = Signal(999)
        val remember = rememberSuspending(Dispatchers.Unconfined) { signal.invoke() }

        var firstValue: Int? = null
        var firstHits = 0

        testContext {
            reactiveSuspending {
                firstValue = remember.invoke()
                firstHits++
            }

            assertEquals(999, firstValue, "First listener should get value in suspending version")
            assertEquals(1, firstHits, "First listener should be called once")
        }
    }

    @Test
    fun ensureActiveCalledInRememberSuspending() {
        // Test that RememberSuspending properly handles cancellation during deactivation delay
        runTest {
            val signal = Signal(0)
            var hits = 0
            val remember = RememberSuspending(Dispatchers.Unconfined, deactivationDelay = 50.milliseconds) {
                hits++
                signal.invoke()
            }

            launch(Dispatchers.Default) {
                val remover1 = remember.addListener { }
                assertEquals(1, hits)
                remover1()

                // Wait a bit
                delay(25.milliseconds)

                // This should cancel the deactivation
                val remover2 = remember.addListener { }
                assertEquals(1, hits, "Should reuse context")

                remover2()
            }
        }
    }

    @Test
    fun runOnceWhileDeadShouldNotAffectActiveState() {
        // Test that calling state getter when dead doesn't interfere with later activation
        val signal = Signal(50)
        var calculationRuns = 0
        val remember = remember {
            calculationRuns++
            signal()
        }

        // Call state while dead (no listeners)
        val stateWhileDead = remember.state
        assertEquals(ReactiveState(50), stateWhileDead, "Should get state even when dead")
        assertEquals(1, calculationRuns, "Should run calculation for state getter")

        testContext {
            var listenerValue: Int? = null
            reactiveScope {
                listenerValue = remember()
            }

            // Should recalculate when listener is added
            assertEquals(50, listenerValue, "Listener should get value")
            assertEquals(2, calculationRuns, "Should recalculate on activation after runOnceWhileDead")
        }
    }

    @Test
    fun concurrentListenerAdditionRemoval() {
        // Test rapid addition and removal of listeners
        val signal = Signal(1)
        var hits = 0
        val remember = remember {
            hits++
            signal()
        }

        testContext {
            val removers = mutableListOf<() -> Unit>()

            // Add 10 listeners rapidly
            repeat(10) { i ->
                reactiveScope {
                    remember()
                }
            }

            // Should only calculate once
            assertEquals(1, hits, "Should share calculation across all listeners")

            // Change the signal
            signal valueSet 2

            // Should recalculate once for all listeners
            assertEquals(2, hits, "Should share recalculation")
        }
    }

    @Test
    fun listenerAddedDuringCalculation() {
        // Test what happens if a listener is added while a calculation is in progress
        val signal1 = Signal(1)
        val signal2 = Signal(2)

        var remember2Hits = 0
        val remember2 = remember {
            remember2Hits++
            signal2()
        }

        var remember1Hits = 0
        val remember1 = remember {
            remember1Hits++
            // This accesses remember2 during remember1's calculation
            val val2 = remember2()
            signal1() + val2
        }

        testContext {
            var value: Int? = null
            reactiveScope {
                value = remember1()
            }

            assertEquals(3, value, "Should calculate nested remember correctly")
            assertEquals(1, remember1Hits, "Outer remember should calculate once")
            assertEquals(1, remember2Hits, "Inner remember should calculate once")
        }
    }

    @Test
    fun stateAccessBeforeAndAfterActivation() {
        // Test accessing .state property both before activation (no listeners) and after
        val signal = Signal(123)
        var calculationRuns = 0
        val remember = remember {
            calculationRuns++
            signal()
        }

        // Access state before any listeners (uses runOnceWhileDead)
        val stateBefore = remember.state
        assertEquals(ReactiveState(123), stateBefore, "State should be accessible before activation")
        assertEquals(1, calculationRuns, "Should run once for state access")

        testContext {
            // Now add a listener
            var listenerValue: Int? = null
            reactiveScope {
                listenerValue = remember()
            }

            // Access state after activation
            val stateAfter = remember.state
            assertEquals(ReactiveState(123), stateAfter, "State should be accessible after activation")
            assertEquals(123, listenerValue, "Listener should get value")
            // Should recalculate on activation (runOnceWhileDead doesn't share with activated context)
            assertEquals(2, calculationRuns, "Should recalculate on activation")
        }
    }

    @Test
    fun dependencyChangeDuringInitialCalculation() {
        // Test if a dependency changes while the initial calculation is running
        val signal = Signal(1)
        var outerHits = 0
        var innerHits = 0

        val inner = remember {
            innerHits++
            signal()
        }

        val outer = remember {
            outerHits++
            val value = inner()
            // Simulate slow calculation
            value * 2
        }

        testContext {
            var outerValue: Int? = null
            reactiveScope {
                outerValue = outer()
            }

            assertEquals(2, outerValue, "Should get correct initial value")
            assertEquals(1, outerHits, "Outer should calculate once")
            assertEquals(1, innerHits, "Inner should calculate once")

            // Now change the dependency
            signal valueSet 5

            assertEquals(10, outerValue, "Should get updated value")
            assertEquals(2, outerHits, "Outer should recalculate")
            assertEquals(2, innerHits, "Inner should recalculate")
        }
    }
}
