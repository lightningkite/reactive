package com.lightningkite.reactive

import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactiveSuspending
import com.lightningkite.reactive.core.RememberSuspending
import com.lightningkite.reactive.core.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * Test for the bugfix: RememberSuspending.deactivate() was missing ensureActive() call.
 *
 * Without ensureActive(), if the deactivation job is cancelled (because a new listener is added
 * before the deactivationDelay expires), the shuttingDown job would still be launched in a
 * cancelled coroutine scope, potentially causing issues.
 *
 * The fix ensures that if the deactivation coroutine is cancelled, it properly exits without
 * launching the shuttingDown job.
 */
class RememberSuspendingBugfixTest {

    @Test
    fun deactivationCancellationHandledCorrectly() {
        // This test verifies that when deactivation is cancelled (by adding a new listener
        // before the deactivationDelay expires), the context is properly reused without
        // any issues from a cancelled coroutine trying to launch shuttingDown.
        runTest {
            val signal = Signal(0)
            var calculationCount = 0

            val remember = RememberSuspending(
                Dispatchers.Unconfined,
                deactivationDelay = 100.milliseconds
            ) {
                calculationCount++
                signal.invoke()
            }

            launch(Dispatchers.Default) {
                // Add first listener
                val remover1 = remember.addListener { }
                assertEquals(1, calculationCount, "Should calculate on first listener")

                // Remove listener - starts deactivation timer
                remover1()

                // Wait less than deactivationDelay, then add another listener
                // This should cancel the deactivation
                delay(50.milliseconds)

                // Add second listener while deactivation is pending
                val remover2 = remember.addListener { }

                // Should NOT recalculate - should reuse existing context
                // Before the fix, the cancelled deactivation coroutine might still try to
                // launch shuttingDown, which could cause subtle issues
                assertEquals(1, calculationCount, "Should reuse context, not recalculate")

                // Change the signal to verify the context is still active and working
                signal valueSet 1
                delay(10.milliseconds)

                // Should recalculate due to signal change
                assertEquals(2, calculationCount, "Should recalculate on signal change")

                remover2()

                // Wait for full shutdown
                delay(110.milliseconds)

                // Add third listener after full shutdown
                val remover3 = remember.addListener { }
                assertEquals(3, calculationCount, "Should recalculate after full shutdown")

                remover3()
            }
        }
    }

    @Test
    fun multipleQuickReactivationsHandled() {
        // Test rapid add/remove cycles during deactivation delay
        runTest {
            val signal = Signal(0)
            var calculationCount = 0

            val remember = RememberSuspending(
                Dispatchers.Unconfined,
                deactivationDelay = 50.milliseconds
            ) {
                calculationCount++
                signal.invoke()
            }

            launch(Dispatchers.Default) {
                // Rapid add/remove cycles
                repeat(5) { iteration ->
                    val remover = remember.addListener { }

                    // Only the first activation should trigger calculation
                    if (iteration == 0) {
                        assertEquals(1, calculationCount, "Should calculate on first activation")
                    } else {
                        assertEquals(1, calculationCount, "Should still have only one calculation at iteration $iteration")
                    }

                    remover()

                    // Wait less than deactivationDelay before reactivating
                    delay(20.milliseconds)
                }

                // Wait for full shutdown
                delay(60.milliseconds)

                // Final activation after shutdown
                val finalRemover = remember.addListener { }
                assertEquals(2, calculationCount, "Should recalculate after full shutdown")
                finalRemover()
            }
        }
    }

    @Test
    fun ensureActivePreventsStaleShutdown() {
        // This test specifically targets the scenario where ensureActive() is crucial:
        // If the deactivation job is cancelled just before it would launch shuttingDown,
        // ensureActive() will throw CancellationException and prevent the stale shutdown
        runTest {
            val signal = Signal(42)
            var calculationCount = 0

            val remember = RememberSuspending(
                Dispatchers.Unconfined,
                deactivationDelay = 30.milliseconds
            ) {
                calculationCount++
                signal.invoke()
            }

            launch(Dispatchers.Default) {
                val remover1 = remember.addListener { }
                assertEquals(1, calculationCount)

                // Start deactivation
                remover1()

                // Wait almost until deactivationDelay expires
                delay(28.milliseconds)

                // At this point, the deactivation job is about to complete
                // Adding a new listener should cancel it before shuttingDown launches
                val remover2 = remember.addListener { }

                // The key thing is that the remember should still be functional,
                // not that it necessarily avoided recalculation (timing dependent)
                val currentCount = calculationCount

                // Verify the remember is still functional
                signal valueSet 99
                delay(10.milliseconds)

                // Should have recalculated in response to signal change
                assertEquals(currentCount + 1, calculationCount, "Should respond to signal changes")

                remover2()
            }
        }
    }
}
