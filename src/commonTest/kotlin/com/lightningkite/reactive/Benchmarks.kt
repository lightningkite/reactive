package com.lightningkite.reactive

import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.BaseReactiveValue
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Signal
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
    val rows = results.map { (name, result) ->
        listOf(
            name,
            result.min.toString(),
            result.max.toString(),
            result.median.toString(),
            result.mean.toString()
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
}