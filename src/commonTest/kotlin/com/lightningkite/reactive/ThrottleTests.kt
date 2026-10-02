package com.lightningkite.reactive

import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.LateInitSignal
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.ThrottleListenable
import com.lightningkite.reactive.extensions.ThrottleReactive
import com.lightningkite.reactive.extensions.value
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ThrottleTests {
    fun testThrottle(
        action: suspend TestContext.(hit: suspend () -> Unit) -> Unit,
        expected: List<Pair<Int, Duration>>,
        getThrottle: TestScope.(source: Reactive<Int>) -> Reactive<Int>,
    ) = runTest {
        val actual = mutableListOf<Pair<Int, Duration>>()
        val source = LateInitSignal<Int>()
        val throttle = getThrottle(source)

        testContext {
            reactive { actual.add(throttle() to currentTime.milliseconds) }

            action {
                if (source.state.ready) source.value = source() + 1
                else source.value = 0
            }

            delay(2.seconds)

            assertEquals(expected, actual)
        }
    }

    fun testHeadThrottle(
        action: suspend TestContext.(hit: suspend () -> Unit) -> Unit,
        expect: List<Pair<Int, Duration>>,
    ) = testThrottle(action, expect) { ThrottleReactive(it, 1.seconds, true, null, testTimeSource) }

    fun testTailThrottle(
        action: suspend TestContext.(hit: suspend () -> Unit) -> Unit,
        expect: List<Pair<Int, Duration>>,
    ) = testThrottle(action, expect) { ThrottleReactive(it, 1.seconds, false, this, testTimeSource) }

    fun testFullThrottle(
        action: suspend TestContext.(hit: suspend () -> Unit) -> Unit,
        expect: List<Pair<Int, Duration>>,
    ) = testThrottle(action, expect) { ThrottleReactive(it, 1.seconds, true, this, testTimeSource) }

    fun action(it: suspend TestContext.(hit: suspend () -> Unit) -> Unit) = it

    @Test
    fun testSingleFire() {
        val action = action { it() }

        testHeadThrottle(action, listOf(0 to 0.seconds))
        testTailThrottle(action, listOf(0 to 1.seconds))
        testFullThrottle(action, listOf(0 to 0.seconds))
    }

    @Test
    fun testDoubleFire() {
        val action = action { it(); it() }

        testHeadThrottle(action, listOf(0 to 0.seconds))
        testTailThrottle(action, listOf(1 to 1.seconds))
        testFullThrottle(
            action, listOf(
                0 to 0.seconds,
                1 to 1.seconds,
            )
        )
    }

    @Test
    fun testTripleFire() {
        val action = action { it(); it(); it() }

        testHeadThrottle(action, listOf(0 to 0.seconds))
        testTailThrottle(action, listOf(2 to 1.seconds))
        testFullThrottle(
            action, listOf(
                0 to 0.seconds,
                2 to 1.seconds,
            )
        )
    }

    @Test
    fun testSporadicOffsets() {
        val action = action {
            it()
            it()
            delay(1.1.seconds)
            it()
            it()
            delay(0.6.seconds)
            it()
            delay(0.5.seconds)
            it()
        }

        testHeadThrottle(
            action, listOf(
                0 to 0.seconds,
                2 to 1.1.seconds,
                5 to 2.2.seconds,
            )
        )

        testTailThrottle(
            action, listOf(
                1 to 1.seconds,
                4 to 2.seconds,
                5 to 3.seconds,
            )
        )

        testFullThrottle(
            action, listOf(
                0 to 0.seconds,
                1 to 1.seconds,
                4 to 2.seconds,
                5 to 3.seconds,
            )
        )
    }

    @Test
    fun testTies() { // useful for finding underlying logic bugs
        val action = action {
            it()
            delay(1.seconds)
            it()
            delay(0.5.seconds)
            it()
            delay(1.seconds)
            it()
            delay(1.seconds)
            it()
        }

        testHeadThrottle(
            action, listOf(
                0 to 0.seconds,
                1 to 1.seconds,
                3 to 2.5.seconds,
                4 to 3.5.seconds,
            )
        )

        testTailThrottle(
            action, listOf(
                0 to 1.seconds,
                2 to 2.seconds,
                3 to 3.seconds,
                4 to 4.seconds,
            )
        )

        testFullThrottle(
            action, listOf(
                0 to 0.seconds,
                1 to 1.seconds,
                2 to 2.seconds,
                3 to 3.seconds,
                4 to 4.seconds,
            )
        )
    }

    @Test
    fun testInitialTailWindow() {
        val action = action {
            it()
            delay(0.5.seconds)
            it()
        }

        testHeadThrottle(action, listOf(0 to 0.seconds))
        testTailThrottle(action, listOf(1 to 1.seconds))
        testFullThrottle(
            action, listOf(
                0 to 0.seconds,
                1 to 1.seconds,
            )
        )
    }

    @Test
    fun testQuietPeriod() {
        val action = action {
            it()
            delay(0.5.seconds)
            it()
            delay(2.seconds)
            it()
        }

        testHeadThrottle(
            action, listOf(
                0 to 0.seconds,
                2 to 2.5.seconds,
            )
        )

        testTailThrottle(
            action, listOf(
                1 to 1.seconds,
                2 to 3.5.seconds,
            )
        )

        testFullThrottle(
            action, listOf(
                0 to 0.seconds,
                1 to 1.seconds,
                2 to 2.5.seconds,
            )
        )
    }

    @Test
    fun testInitialQuietPeriod() {
        val action = action {
            delay(2.5.seconds)
            it()
            delay(0.5.seconds)
            it()
        }

        testHeadThrottle(action, listOf(0 to 2.5.seconds))
        testTailThrottle(action, listOf(1 to 3.5.seconds))
        testFullThrottle(
            action, listOf(
                0 to 2.5.seconds,
                1 to 3.5.seconds,
            )
        )
    }

    @Test
    fun testBoundary() {
        val action = action {
            it()
            delay(0.999.seconds)
            it()
        }

        testHeadThrottle(action, listOf(0 to 0.seconds))
        testTailThrottle(action, listOf(1 to 1.seconds))
        testFullThrottle(
            action, listOf(
                0 to 0.seconds,
                1 to 1.seconds,
            )
        )
    }

    @Test
    fun testLongStream() {
        val action = action {
            1.rangeTo(40).forEach {
                it()
                delay(0.25.seconds)
            }
        }

        testHeadThrottle(action, 0.rangeTo(9).map { it * 4 to it.seconds })
        testTailThrottle(action, 1.rangeTo(10).map { (it * 4 - 1) to it.seconds })
        testFullThrottle(action, listOf(0 to 0.seconds) + 1.rangeTo(10).map { (it * 4 - 1) to it.seconds })
    }

    @Test
    fun testReentrantTail() {
        // a listener that writes back to the source during a tail notification should get notified again
        fun test(head: Boolean, expected: List<Long>) = runTest {
            val source = Signal(0)
            val throttle = ThrottleListenable(source, 1.seconds, head, this, testTimeSource)
            val fired = mutableListOf<Long>()
            val release = throttle.addListener {
                fired.add(currentTime)
                if (source.value == 2) source.value = 3
            }

            source.value = 1
            delay(0.5.seconds)
            source.value = 2
            delay(3.seconds)
            release()

            assertEquals(expected, fired)
        }

        test(head = false, listOf(1000L, 2000L))
        test(head = true, listOf(0L, 1000L, 2000L))
    }
}
