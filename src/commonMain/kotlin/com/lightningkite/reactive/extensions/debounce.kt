package com.lightningkite.reactive.extensions

import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class DebounceReactive<T>(val source: Reactive<T>, val duration: Duration) : Reactive<T>, Listenable by DebounceListenable(source, duration) {
    override val state: ReactiveState<T> get() = source.state
}
data class DebounceListenable(val source: Listenable, val duration: Duration) : Listenable {
    override fun addListener(listener: () -> Unit): () -> Unit {
        var changeCount = 0
        return source.addListener {
            val num = ++changeCount
            AppScope.launch {
                delay(duration)
                if (num == changeCount) listener()
            }
        }
    }
}

fun <T> Reactive<T>.debounce(timeMs: Long): Reactive<T> = DebounceReactive(this, timeMs.milliseconds)
fun <T> Reactive<T>.debounce(duration: Duration): Reactive<T> = DebounceReactive(this, duration)
fun Listenable.debounce(timeMs: Long): Listenable = DebounceListenable(this, timeMs.milliseconds)
fun Listenable.debounce(duration: Duration): Listenable = DebounceListenable(this, duration)

fun <T> MutableReactive<T>.debounceWrite(duration: Duration): MutableReactive<T> = object: MutableReactive<T> by this {
    var setIndex = 0
    override suspend fun set(value: T) {
        val mine = ++setIndex
        delay(duration)
        if(mine == setIndex) this@debounceWrite.set(value)
    }
}