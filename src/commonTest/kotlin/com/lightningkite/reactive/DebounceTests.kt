package com.lightningkite.reactive

import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.LateInitSignal
import com.lightningkite.reactive.extensions.DebounceListenable
import com.lightningkite.reactive.extensions.DebounceReactive
import com.lightningkite.reactive.extensions.value
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DebounceTests {
    fun testDebounce(
        action: suspend TestContext.(hit: suspend () -> Unit) -> Unit,
        expected: List<Pair<Int, Duration>>,
    ) = runTest {
        val actual = mutableListOf<Pair<Int, Duration>>()
        val actualListenable = mutableListOf<Duration>()
        val source = LateInitSignal<Int>()
        val debounced = DebounceReactive(source, this, 1.seconds, testTimeSource)
        val debouncedListenable = DebounceListenable(source, this, 1.seconds, testTimeSource)

        testContext {
            reactive { actual.add(debounced() to currentTime.milliseconds) }
            val release = debouncedListenable.addListener { actualListenable.add(currentTime.milliseconds) }

            action {
                if (source.state.ready) source.value = source() + 1
                else source.value = 0
            }

            delay(2.seconds)
            release()

            assertEquals(expected, actual)
            assertEquals(expected.map { it.second }, actualListenable)
        }
    }

    fun action(it: suspend TestContext.(hit: suspend () -> Unit) -> Unit) = it

    @Test
    fun testSingleFire() = testDebounce(
        action { it() },
        listOf(0 to 1.seconds),
    )

    @Test
    fun testTripleFire() = testDebounce(
        action { it(); it(); it() },
        listOf(2 to 1.seconds),
    )

    @Test
    fun testSporadicOffsets() = testDebounce(
        action {
            it()
            it()
            delay(1.1.seconds)
            it()
            it()
            delay(0.6.seconds)
            it()
            delay(0.5.seconds)
            it()
        },
        listOf(
            1 to 1.seconds,
            5 to 3.2.seconds,
        )
    )

    @Test
    fun testTies() = testDebounce( // useful for finding underlying logic bugs
        action {
            it()
            delay(1.seconds)
            it()
            delay(0.5.seconds)
            it()
            delay(1.seconds)
            it()
            delay(1.seconds)
            it()
        },
        listOf(4 to 4.5.seconds),
    )

    @Test
    fun testBoundaryUnder() = testDebounce(
        action {
            it()
            delay(0.999.seconds)
            it()
        },
        listOf(1 to 1.999.seconds),
    )

    @Test
    fun testBoundaryOver() = testDebounce(
        action {
            it()
            delay(1.001.seconds)
            it()
        },
        listOf(
            0 to 1.seconds,
            1 to 2.001.seconds,
        )
    )

    @Test
    fun testLongStreamWithGaps() = testDebounce(
        action {
            1.rangeTo(4).forEach {
                1.rangeTo(5).forEach {
                    it()
                    delay(0.5.seconds)
                }
                delay(1.seconds)
            }
        },
        0.rangeTo(3).map { (it * 5 + 4) to (it * 3.5 + 3).seconds },
    )
}
