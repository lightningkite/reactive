package com.lightningkite.reactive

import com.lightningkite.reactive.core.BaseListenable
import com.lightningkite.reactive.core.BasicListenable
import com.lightningkite.reactive.core.Listenable
import kotlin.collections.plusAssign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource
import kotlin.time.measureTime

class DependencyTrackerTest {
    data class LoopTimings(
        val registerAll: Duration,
        val duplicateLoop: Duration,
        val removeHalf: Duration,
        val removeAll: Duration,
        val sizeAfterRegister: Int,
    ) {
        val total: Duration get() = registerAll + duplicateLoop + removeHalf + removeAll
    }

    interface Tracker {
        fun <T : Any> existingDependency(listenable: T): T?

        fun registerDependency(any: Any, remove: () -> Unit)

        fun cancel()

        fun dependencyBlockStart()
        fun dependencyBlockEnd()

        val collectionSizes: Int
    }

    open class DependencyTracker : Tracker {
        protected val dependencies = ArrayList<Pair<Any, () -> Unit>>()
        protected val usedDependencies = ArrayList<Any?>()

        override val collectionSizes: Int get() = dependencies.size + usedDependencies.size

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> existingDependency(listenable: T): T? {
            usedDependencies.add(listenable)
            if (dependencies.size > usedDependencies.size) {
                val maybe = dependencies[usedDependencies.size].first
                if (maybe == listenable) return listenable
            }
            return if (dependencies.any { it.first == listenable }) listenable else null
        }

        override fun registerDependency(any: Any, remove: () -> Unit) {
            this.dependencies += any to remove
        }

        override fun cancel() {
            dependencies.forEach { it.second() }
            dependencies.clear()
        }

        override fun dependencyBlockStart() {
            usedDependencies.clear()
        }
        override fun dependencyBlockEnd() {
            val iter = dependencies.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                if (entry.first !in usedDependencies) {
                    entry.second()
                    iter.remove()
                }
            }
        }
    }

    open class HashedDependencyTracker : Tracker {
        protected val dependencies = HashMap<Any, () -> Unit>()
        protected val usedDependencies = HashSet<Any>()

        override val collectionSizes: Int get() = dependencies.size + usedDependencies.size

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> existingDependency(listenable: T): T? {
            usedDependencies.add(listenable)
            return if (dependencies.containsKey(listenable)) listenable else null
        }

        override fun registerDependency(any: Any, remove: () -> Unit) {
            dependencies[any] = remove
        }

        override fun cancel() {
            dependencies.forEach { it.value() }
            dependencies.clear()
        }

        override fun dependencyBlockStart() {
            usedDependencies.clear()
        }
        override fun dependencyBlockEnd() {
            val iter = dependencies.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                if (entry.key !in usedDependencies) {
                    entry.value()
                    iter.remove()
                }
            }
        }
    }

    open class HashedDependencyTracker2 : Tracker {
        protected val dependenciesWithRemovers = ArrayList<Pair<Any, () -> Unit>>()
        protected val dependencies = HashSet<Any>()
        protected val usedDependencies = HashSet<Any>()

        override val collectionSizes: Int get() = dependenciesWithRemovers.size + dependencies.size + usedDependencies.size

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> existingDependency(listenable: T): T? {
            usedDependencies.add(listenable)
            return if (dependencies.contains(listenable)) listenable else null
        }

        override fun registerDependency(any: Any, remove: () -> Unit) {
            if (dependencies.add(any)) dependenciesWithRemovers.add(any to remove)
        }

        override fun cancel() {
            dependenciesWithRemovers.forEach { it.second() }
            dependencies.clear()
            dependenciesWithRemovers.clear()
        }

        override fun dependencyBlockStart() {
            usedDependencies.clear()
        }
        override fun dependencyBlockEnd() {
            val iter = dependenciesWithRemovers.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                if (entry.first !in usedDependencies) {
                    entry.second()
                    dependencies.remove(entry.first)
                    iter.remove()
                }
            }
        }
    }

    fun Tracker.loop(dependencies: List<Listenable>) {
        dependencyBlockStart()
        for (d in dependencies) if (existingDependency(d) == null) registerDependency(d, d.addListener {})
        dependencyBlockEnd()
    }

    fun Tracker.testLoop(listSize: Int, print: Boolean = false): LoopTimings {
        var active = 0

        class Dep : BaseListenable() {
            override fun activate() {
                active += 1
            }
            override fun deactivate() {
                active -= 1
            }
            fun invokeAll() = invokeAllListeners()
        }

        val dependencies = List(listSize) { Dep() }

        var mark = TimeSource.Monotonic.markNow()

        // register all dependencies
        loop(dependencies)

        assertEquals(dependencies.size, active)

        val registerAll = mark.elapsedNow()
        val sizeAfterRegister = collectionSizes
        if (print) println("Register All: $registerAll (size: $sizeAfterRegister)")

        mark = TimeSource.Monotonic.markNow()

        // run again with same dependencies
        loop(dependencies)

        assertEquals(dependencies.size, active)

        val duplicateLoop = mark.elapsedNow()
        if (print) println("Duplicate Loop: $duplicateLoop")

        mark = TimeSource.Monotonic.markNow()

        // remove half of the dependencies
        loop(dependencies.slice(0..<dependencies.size/2))

        assertEquals(dependencies.size / 2, active)

        val removeHalf = mark.elapsedNow()
        if (print) println("Remove Half: $removeHalf")

        mark = TimeSource.Monotonic.markNow()

        // remove all the dependencies
        loop(emptyList())

        assertEquals(0, active)

        val removeAll = mark.elapsedNow()
        if (print) println("Remove All: $removeAll")

        val timings = LoopTimings(registerAll, duplicateLoop, removeHalf, removeAll, sizeAfterRegister)
        if (print) println("Total time: ${timings.total}")

        return timings
    }

    fun Tracker.fullTest(): Map<Int, LoopTimings> {
        val times = mutableMapOf<Int, LoopTimings>()
        for (size in arrayOf(1, 3, 5, 10, 20, 100, 500, 1000, 10_000)) {
            times[size] = testLoop(size)
        }

        val min = times.entries.minBy { it.value.total }
        val max = times.entries.maxBy { it.value.total }
        val meanTotal = times.values.map { it.total }.reduce { acc, d -> acc + d } / times.size

        println("Min Total: ${min.key} items (${min.value.total})")
        println("Max Total: ${max.key} items (${max.value.total})")
        println("Mean Total: $meanTotal")

        return times
    }

    fun printComparisonTable(
        title: String,
        normalTimes: Map<Int, LoopTimings>,
        hashedTimes: Map<Int, LoopTimings>,
        hashed2Times: Map<Int, LoopTimings>,
        extract: (LoopTimings) -> Duration
    ) {
        println("__ $title __")
        println("${"Size".padEnd(10)} | ${"Normal".padEnd(15)} | ${"Hashed".padEnd(15)} | ${"Hashed2".padEnd(15)} | Winner")
        println("-".repeat(85))
        for (size in normalTimes.keys.sorted()) {
            val normal = extract(normalTimes[size]!!)
            val hashed = extract(hashedTimes[size]!!)
            val hashed2 = extract(hashed2Times[size]!!)

            val entries = listOf("Normal" to normal, "Hashed" to hashed, "Hashed2" to hashed2)
            val (winnerName, winnerTime) = entries.minBy { it.second }
            val slowest = entries.maxOf { it.second }
            val factor = if (winnerTime.inWholeNanoseconds > 0L) {
                slowest.inWholeNanoseconds.toDouble() / winnerTime.inWholeNanoseconds.toDouble()
            } else 0.0
            val winnerStr = "$winnerName ${(factor * 100).toInt() / 100.0}x"

            println("${size.toString().padEnd(10)} | ${normal.toString().padEnd(15)} | ${hashed.toString().padEnd(15)} | ${hashed2.toString().padEnd(15)} | $winnerStr")
        }
        println()
    }
//
//    @Test
//    fun performanceWithTables() {
//        println()
//
//        println("__ NORMAL __")
//        val normalTimes = DependencyTracker().fullTest()
//        println()
//
//        println("__ HASHED __")
//        val hashedTimes = HashedDependencyTracker().fullTest()
//        println()
//
//        println("__ HASHED 2 __")
//        val hashed2Times = HashedDependencyTracker2().fullTest()
//        println()
//
//        printComparisonTable("TOTAL", normalTimes, hashedTimes, hashed2Times) { it.total }
//        printComparisonTable("REGISTER ALL", normalTimes, hashedTimes, hashed2Times) { it.registerAll }
//        printComparisonTable("DUPLICATE LOOP", normalTimes, hashedTimes, hashed2Times) { it.duplicateLoop }
//        printComparisonTable("REMOVE HALF", normalTimes, hashedTimes, hashed2Times) { it.removeHalf }
//        printComparisonTable("REMOVE ALL", normalTimes, hashedTimes, hashed2Times) { it.removeAll }
//    }

    fun Collection<Duration>.average() = sumOf { it.inWholeNanoseconds }.div(size.toDouble()).nanoseconds

    data class TestMetrics(
        val min: Duration,
        val max: Duration,
        val mean: Duration
    )

    data class Results(
        val normal: TestMetrics,
        val hashed: TestMetrics,
        val hashed2: TestMetrics
    )

    @Test
    fun microBenchmark() {
        fun Tracker.runLoop(size: Int) {
            val dependencies = List(size) { BasicListenable() }

            fun loop(deps: List<Listenable>) {
                dependencyBlockStart()
                for (d in deps) if (existingDependency(d) == null) registerDependency(d, d.addListener {})
                dependencyBlockEnd()
            }

            loop(dependencies)
            loop(dependencies)
            loop(dependencies.slice(0..<size/2))
            loop(emptyList())
        }

        fun Tracker.test(size: Int): List<Duration> {
            repeat(500) { runLoop(size) }

            return List(1000) {
                measureTime { runLoop(size) }
            }
        }

        fun List<Duration>.metrics(): TestMetrics =
            TestMetrics(min = min(), max = max(), mean = average())

        val sizes = arrayOf(1, 3, 5, 10, 20, 100, 500, 1000)

        val results = sizes.associateWith { size ->
            Results(
                normal = DependencyTracker().test(size).metrics(),
                hashed = HashedDependencyTracker().test(size).metrics(),
                hashed2 = HashedDependencyTracker2().test(size).metrics(),
            ).also { println("Finished Size=$size") }
        }

        fun table(name: String, metric: (TestMetrics) -> Duration) {

            fun row(a: Any, b: Any, c: Any, d: Any, e: Any) {
                println("${a.toString().padEnd(10)} | ${b.toString().padEnd(15)} | ${c.toString().padEnd(15)} | ${d.toString().padEnd(15)} | $e")
            }

            println("__ $name __")
            row("Size", "Normal", "Hashed", "Hashed2", "Winner")
            println("-".repeat(85))

            for ((size, result) in results) {
                val normal = metric(result.normal)
                val hashed = metric(result.hashed)
                val hashed2 = metric(result.hashed2)

                val entries = listOf("Normal" to normal, "Hashed" to hashed, "Hashed2" to hashed2)
                val (winnerName, winnerTime) = entries.minBy { it.second }
                val slowest = entries.maxOf { it.second }
                val factor = if (winnerTime.inWholeNanoseconds > 0L) {
                    slowest.inWholeNanoseconds.toDouble() / winnerTime.inWholeNanoseconds.toDouble()
                } else 0.0
                val winnerStr = "$winnerName ${(factor * 100).toInt() / 100.0}x"

                row(size, normal, hashed, hashed2, winnerStr)
            }
        }

        table("MEAN") { it.mean }
        table("MIN") { it.min }
        table("MAX") { it.max }
    }
}