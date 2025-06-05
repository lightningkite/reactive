package com.lightningkite.signal

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class DebounceSignal<T>(val source: Signal<T>, val duration: Duration) : Signal<T>, Listenable by DebounceListenable(source, duration) {
    override val state: SignalState<T> get() = source.state
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

fun <T> Signal<T>.debounce(timeMs: Long): Signal<T> = DebounceSignal(this, timeMs.milliseconds)
fun <T> Signal<T>.debounce(duration: Duration): Signal<T> = DebounceSignal(this, duration)
fun Listenable.debounce(timeMs: Long): Listenable = DebounceListenable(this, timeMs.milliseconds)
fun Listenable.debounce(duration: Duration): Listenable = DebounceListenable(this, duration)

fun <T> MutableSignal<T>.debounceWrite(duration: Duration): MutableSignal<T> = object: MutableSignal<T> by this {
    var setIndex = 0
    override suspend fun set(value: T) {
        val mine = ++setIndex
        delay(duration)
        if(mine == setIndex) this@debounceWrite.set(value)
    }
}