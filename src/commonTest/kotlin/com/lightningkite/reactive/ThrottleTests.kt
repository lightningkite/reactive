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
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ThrottleTests {
    fun TestScope.getTestClock() = object : Clock {
        override fun now(): Instant {
            return Instant.fromEpochMilliseconds(currentTime)
        }
    }

    fun testThrottle(
        action: suspend TestContext.(hit: suspend () -> Unit) -> Unit,
        expect: Map<Int, (time: Duration) -> Unit>,
        getThrottle: (source: Reactive<Int>, scope: TestScope) -> Reactive<Int>,
    ) = runTest {
        val actualHits = mutableListOf<Pair<Int, Duration>>()
        val source = LateInitSignal<Int>()
        val throttle = getThrottle(source, this)

        testContext {
            reactive { actualHits.add(throttle() to currentTime.milliseconds) }

            action {
                if (source.state.ready) source.value = source() + 1
                else source.value = 0
            }

            delay(2.seconds)

            actualHits.forEach {
                expect[it.first]?.invoke(it.second) ?: fail("Did not expect $it")
            }
            expect.keys.forEach {
                if (!actualHits.map { it.first }.contains(it)) fail("Expected $it")
            }
        }
    }

    fun testHeadThrottle(
        action: suspend TestContext.(hit: suspend () -> Unit) -> Unit,
        expect: Map<Int, (time: Duration) -> Unit>,
    ) = testThrottle(action, expect) { source, scope ->
        ThrottleReactive(
            source,
            1.seconds,
            true,
            null,
            scope.getTestClock()
        )
    }

    fun testTailThrottle(
        action: suspend TestContext.(hit: suspend () -> Unit) -> Unit,
        expect: Map<Int, (time: Duration) -> Unit>,
    ) = testThrottle(action, expect) { source, scope ->
        ThrottleReactive(
            source,
            1.seconds,
            false,
            scope,
            scope.getTestClock()
        )
    }

    fun testFullThrottle(
        action: suspend TestContext.(hit: suspend () -> Unit) -> Unit,
        expect: Map<Int, (time: Duration) -> Unit>,
    ) = testThrottle(action, expect) { source, scope ->
        ThrottleReactive(
            source,
            1.seconds,
            true,
            scope,
            scope.getTestClock()
        )
    }

    fun action(it: suspend TestContext.(hit: suspend () -> Unit) -> Unit) = it

    @Test
    fun testSingleFire() {
        val action = action { it() }

        testHeadThrottle(action, mapOf(0 to { assertEquals(0.seconds, it) }))
        testTailThrottle(action, mapOf(0 to { assertEquals(1.seconds, it) }))
        testFullThrottle(action, mapOf(0 to { assertEquals(0.seconds, it) }))
    }

    @Test
    fun testDoubleFire() {
        val action = action { it(); it() }

        testHeadThrottle(action, mapOf(0 to { assertEquals(0.seconds, it) }))
        testTailThrottle(action, mapOf(1 to { assertEquals(1.seconds, it) }))
        testFullThrottle(
            action, mapOf(
                0 to { assertEquals(0.seconds, it) },
                1 to { assertEquals(1.seconds, it) },
            )
        )
    }

    @Test
    fun testTripleFire() {
        val action = action { it(); it(); it() }

        testHeadThrottle(action, mapOf(0 to { assertEquals(0.seconds, it) }))
        testTailThrottle(action, mapOf(2 to { assertEquals(1.seconds, it) }))
        testFullThrottle(
            action, mapOf(
                0 to { assertEquals(0.seconds, it) },
                2 to { assertEquals(1.seconds, it) },
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

        testHeadThrottle(action, mapOf(
            0 to { assertEquals(0.seconds, it) },
            2 to { assertEquals(1.1.seconds, it) },
            5 to { assertEquals(2.2.seconds, it) },
        ))

        testTailThrottle(action, mapOf(
            1 to { assertEquals(1.seconds, it) },
            4 to { assertEquals(2.seconds, it) },
            5 to { assertEquals(3.seconds, it) },
        ))

        testFullThrottle(action, mapOf(
            0 to { assertEquals(0.seconds, it) },
            1 to { assertEquals(1.seconds, it) },
            4 to { assertEquals(2.seconds, it) },
            5 to { assertEquals(3.seconds, it) },
        ))
    }
}