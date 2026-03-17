package com.lightningkite.reactive.extensions

import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.Release
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.ReactiveValue

inline fun <T> Reactive<T>.addStateListener(crossinline listener: (ReactiveState<T>) -> Unit): Release {
    return addListener { listener(state) }
}

inline fun <T> Reactive<T>.addAndRunStateListener(crossinline listener: (ReactiveState<T>) -> Unit): Release {
    val listener: () -> Unit = { listener(state) }
    val release = addListener(listener)
    listener()
    return release
}

inline fun <T> ReactiveValue<T>.addValueListener(crossinline listener: (T) -> Unit): Release {
    return addListener { listener(value) }
}

inline fun <T> ReactiveValue<T>.addAndRunValueListener(crossinline listener: (T) -> Unit): Release {
    val listener: () -> Unit = { listener(value) }
    val release = addListener(listener)
    listener()
    return release
}

