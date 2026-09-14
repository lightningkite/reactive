package com.lightningkite.reactive

import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.Release
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.awaitNotNull
import com.lightningkite.reactive.extensions.value
import com.lightningkite.reactive.extensions.waitForNotNull
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class AwaitNotNullTests {
    /**
     * Outside a reactive calculation there is nothing to rerun the caller, so the suspending
     * awaitNotNull has to listen for the value itself.
     */
    @Test
    fun suspendingResumesWhenValueArrives() = runTest {
        val signal = Signal<Int?>(null)
        // UNDISPATCHED so the await is actually parked on the null before the value arrives.
        val result = async(start = CoroutineStart.UNDISPATCHED) { signal.awaitNotNull() }
        signal.value = 3
        assertEquals(3, withTimeout(5.seconds) { result.await() })
    }

    @Test
    fun suspendingTakesAnAlreadyPresentValue() = runTest {
        val signal = Signal<Int?>(7)
        assertEquals(7, withTimeout(5.seconds) { signal.awaitNotNull() })
    }

    @Test
    fun inContextSkipsNulls() {
        val signal = Signal<Int?>(null)
        val emissions = ArrayList<Int>()
        testContext {
            reactive(action = { emissions.add(signal.awaitNotNull()) })
            repeat(10) {
                signal.value = null
                signal.value = it
            }
        }
        assertEquals((0..9).toList(), emissions)
    }

    /** A null reads as loading, not as an error or a value, and recovers when a value arrives. */
    @Test
    fun inContextReportsLoadingWhileNull() {
        val signal = Signal<Int?>(null)
        var loadCalls = 0
        testContext {
            reactive(onLoad = { loadCalls++ }) { signal.awaitNotNull() }
            assertEquals(1, loadCalls, "A null start is a transition into loading")

            signal.value = 5
            assertEquals(1, loadCalls, "A value should end the load, not start another")

            signal.value = null
            assertEquals(2, loadCalls, "Going back to null re-enters loading")

            // Leave in a ready state so testContext's loadCount balance check passes.
            signal.value = 6
        }
    }

    /**
     * Dependency reuse is keyed on equality, so the wrapper a calculation builds each run must
     * compare equal to the previous run's - otherwise every rerun drops and re-adds the listener.
     */
    @Test
    fun mutableWaitForNotNullReusesItsDependency() {
        val source = ListenerCountingMutableReactive(Signal<Int?>(1))
        val unrelated = Signal(0)
        testContext {
            reactive(action = {
                // Explicitly typed so this exercises the MutableReactive overload, not the Reactive one.
                val notNull: MutableReactive<Int> = source.waitForNotNull
                notNull() + unrelated()
            })
            assertEquals(1, source.listenerCount)
            unrelated.value = 1
            unrelated.value = 2
            assertEquals(1, source.listenerCount, "Reruns should reuse the existing waitForNotNull dependency")
        }
    }
}

private class ListenerCountingMutableReactive<T>(private val wraps: MutableReactive<T>) : MutableReactive<T> {
    var listenerCount: Int = 0
        private set

    override val state: ReactiveState<T> get() = wraps.state

    override fun addListener(listener: () -> Unit): Release {
        listenerCount++
        return wraps.addListener(listener)
    }

    override suspend fun set(value: T): Unit = wraps.set(value)
}
