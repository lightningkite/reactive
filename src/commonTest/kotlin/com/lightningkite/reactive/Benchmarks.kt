package com.lightningkite.reactive

import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.BaseReactiveValue
import com.lightningkite.reactive.core.MutableReactiveValue
import kotlin.reflect.KProperty0
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration
import kotlin.time.measureTime

private data class BenchmarkResults(
    val min: Duration,
    val max: Duration,
    val median: Duration,
    val mean: Duration
)

private inline fun benchmark(samples: Int = 500, action: () -> Unit): BenchmarkResults {
    val durations = ArrayList<Duration>(samples)
    var sum = Duration.ZERO

    repeat(samples) {
        val d = measureTime(action)
        durations.add(d)
        sum += d
    }

    durations.sort()

    return BenchmarkResults(
        min = durations.first(),
        max = durations.last(),
        median = if (samples % 2 == 0) {
            (durations[samples / 2 - 1] + durations[samples / 2]) / 2
        } else {
            durations[samples / 2]
        },
        mean = sum / samples
    )
}

private inline fun coldBenchmark(samples: Int = 500, action: () -> Unit): BenchmarkResults {
    repeat(300) { action() }
    return benchmark(samples, action)
}

private fun printBenchmarkTable(results: Map<String, BenchmarkResults>) {
    if (results.isEmpty()) return

    val headers = listOf("Benchmark", "Min", "Max", "Median", "Mean")

    // Find the minimum value for each metric
    val minMin = results.values.minOf { it.min }
    val minMax = results.values.minOf { it.max }
    val minMedian = results.values.minOf { it.median }
    val minMean = results.values.minOf { it.mean }

    val rows = results.map { (name, result) ->
        fun formatWithMultiple(value: Duration, fastest: Duration): String {
            val multiple = value / fastest
            return if (multiple > 1.01) { // Use small threshold to account for floating point
                "$value (${multiple.toString().let { 
                    if (it.contains('.')) it.substringBefore('.') + '.' + it.substringAfter('.').take(2)
                    else it
                }}x)"
            } else {
                value.toString()
            }
        }

        listOf(
            name,
            formatWithMultiple(result.min, minMin),
            formatWithMultiple(result.max, minMax),
            formatWithMultiple(result.median, minMedian),
            formatWithMultiple(result.mean, minMean)
        )
    }

    // Calculate column widths
    val allRows = listOf(headers) + rows
    val columnWidths = headers.indices.map { col ->
        allRows.maxOf { it[col].length }
    }

    // Print header
    println(headers.mapIndexed { i, header ->
        header.padEnd(columnWidths[i])
    }.joinToString(" | "))

    // Print separator
    println(columnWidths.joinToString("-+-") { "-".repeat(it) })

    // Print data rows
    rows.forEach { row ->
        println(row.mapIndexed { i, cell ->
            cell.padEnd(columnWidths[i])
        }.joinToString(" | "))
    }
}
private fun printBenchmarkTable(vararg results: Pair<String, BenchmarkResults>) = printBenchmarkTable(results.toMap())

class Benchmarks {
    class TestSignal<T>(start: T) : MutableReactiveValue<T>, BaseReactiveValue<T>(start) {
        var active: Boolean = false
            private set

        override fun activate() {
            active = true
        }
        override fun deactivate() {
            active = false
        }
    }

    class HasVariable<T>(var value: T)

    @Test
    fun `single value reactive contexts vs direct listener`() {
        testContext {
            val signal = TestSignal(0)

            val ctx = reactive { signal() }
            val reactiveContext = coldBenchmark { signal.value += 1 }
            ctx.cancel()

            assertFalse(signal.active)

            signal.value = 0
            val release = signal.addListener {  }
            val direct = coldBenchmark { signal.value += 1 }
            release()

            assertFalse(signal.active)

            printBenchmarkTable(
                "reactive" to reactiveContext,
                "direct" to direct
            )
        }
    }

    @Test
    fun `indirect vs direct bind`() {
        val signal = TestSignal(0)

        fun testBind(bind: TestContext.(HasVariable<Int>) -> Unit): BenchmarkResults {
            signal.value = 0
            val r = testContext {
                val v = HasVariable(0)
                val d = measureTime { bind(v) }

                println("Took $d to bind")

                val r = coldBenchmark { signal.value += 1 }

                assertEquals(signal.value, v.value)

                r
            }
            assertFalse(signal.active)
            return r
        }

        val indirect = testBind { it::value { signal() } }
        val direct = testBind { it::value bind signal }

        printBenchmarkTable(
            "indirect" to indirect,
            "direct" to direct
        )
    }

//    @Test
//    fun `bind ReactiveValue optimization`() {
//        val signal = TestSignal(0)
//
//        class HasVariable(var value: Int = 0)
//
//        fun testBind(bind: TestContext.(HasVariable) -> Unit): BenchmarkResults {
//            signal.value = 0
//            return testContext {
//                val hasVariable = HasVariable()
//
//                val declaration = measureTime { bind(hasVariable) }
//
//                println("Took $declaration to initialize")
//
//                val r = coldBenchmark { signal.value += 1 }
//
//                assertEquals(hasVariable.value, signal.value)
//
//                r
//            }
//        }
//
//        val slow = testBind { it::value bind (signal as Reactive<Int>) }
//        val fast = testBind { it::value bind signal }
//        val alt = testBind { it::value bindAlt signal }
//
//        printBenchmarkTable(
//            "slow" to slow,
//            "fast" to fast,
//            "alt" to alt
//        )
//    }
}