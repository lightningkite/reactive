package com.lightningkite.reactive.context

open class DependencyTracker {
    protected val dependencies = ArrayList<Pair<Any, () -> Unit>>()
    protected val usedDependencies = ArrayList<Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> existingDependency(listenable: T): T? {
        usedDependencies.add(listenable)
        if (dependencies.size > usedDependencies.size) {
            val maybe = dependencies[usedDependencies.size].first
            if (maybe == listenable) return maybe as T
        }
        return dependencies.find { it.first == listenable }?.first as? T
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