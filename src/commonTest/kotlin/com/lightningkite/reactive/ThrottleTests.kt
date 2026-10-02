package com.lightningkite.reactive

import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.LateInitSignal
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.extensions.ThrottleReactive
import com.lightningkite.reactive.extensions.value
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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

    fun TestScope.getTestClock() = object : Clock {
        override fun now() = Instant.fromEpochMilliseconds(currentTime)
    }

    fun testHeadThrottle(
        action: suspend TestContext.(hit: suspend () -> Unit) -> Unit,
        expect: List<Pair<Int, Duration>>,
    ) = testThrottle(action, expect) { ThrottleReactive(it, 1.seconds, true, null, getTestClock()) }

    fun testTailThrottle(
        action: suspend TestContext.(hit: suspend () -> Unit) -> Unit,
        expect: List<Pair<Int, Duration>>,
    ) = testThrottle(action, expect) { ThrottleReactive(it, 1.seconds, false, this, getTestClock()) }

    fun testFullThrottle(
        action: suspend TestContext.(hit: suspend () -> Unit) -> Unit,
        expect: List<Pair<Int, Duration>>,
    ) = testThrottle(action, expect) { ThrottleReactive(it, 1.seconds, true, this, getTestClock()) }

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
    fun testDependantOffsets() {
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
}