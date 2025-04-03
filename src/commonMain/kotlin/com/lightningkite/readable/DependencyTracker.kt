package com.lightningkite.readable

import com.lightningkite.jsoptimized.*


abstract class DependencyTracker{
    protected var dependencies = emptyVector<JsPair<Any, () -> Unit>>()
    protected var usedDependencies = emptyVector<Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> existingDependency(listenable: T): T? {
        usedDependencies.push(listenable)
        if (dependencies.size > usedDependencies.size) {
            val maybe = dependencies[usedDependencies.size].first
            if (maybe == listenable) return maybe as T
        }
        dependencies.forEach {
            if(it.first == listenable) return it.first as T
        }
        return null
    }

    fun registerDependency(any: Any, remove: () -> Unit) {
        this.dependencies.push(any jsTo remove)
    }

    open fun cancel() {
        dependencies.forEach { it.second() }
        dependencies = emptyVector()
    }

    protected fun dependencyBlockStart() {
        usedDependencies = emptyVector()
    }
    protected fun dependencyBlockEnd() {
        dependencies = dependencies.filter { it.first in usedDependencies }
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