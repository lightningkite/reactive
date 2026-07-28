package com.lightningkite.reactive.context

abstract class DependencyTracker {
    private val dependencies = ArrayList<Pair<Any, () -> Unit>>()
    private val usedDependencies = ArrayList<Any>()

    protected val dependencyCount: Int get() = dependencies.size

    /**
     * Looks up a dependency already tracked for this run, marking it used.
     *
     * Fast path: if dependencies are read in the same order every run (the steady-state case),
     * the dependency due to be read next always sits at [dependencies]`[usedDependencies.size]` -
     * i.e. the slot immediately following the ones already marked used this run. Checking that one
     * slot avoids the O(n) [dependencies].find fallback below, keeping a rerun over n dependencies
     * O(n) instead of O(n^2).
     *
     * A dependency read more than once in the same run is only added to [usedDependencies] once,
     * so the fast-path alignment above holds even when a call site re-reads an earlier dependency.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> existingDependency(listenable: T): T? {
        val index = usedDependencies.size
        if (index < dependencies.size) {
            val maybe = dependencies[index].first
            if (maybe == listenable) {
                usedDependencies.add(listenable)
                return maybe as T
            }
        }
        val found = dependencies.find { it.first == listenable }?.first as? T
        if (listenable !in usedDependencies) usedDependencies.add(listenable)
        return found
    }

    fun registerDependency(any: Any, remove: () -> Unit) {
        this.dependencies += any to remove
    }

    open fun cancel() {
        dependencies.forEach { it.second() }
        dependencies.clear()
    }

    protected fun dependencyBlockStart() {
        usedDependencies.clear()
    }
    protected fun dependencyBlockEnd() {
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