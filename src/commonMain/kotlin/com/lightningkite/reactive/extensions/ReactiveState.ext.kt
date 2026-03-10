package com.lightningkite.reactive.extensions

import com.lightningkite.reactive.core.ReactiveState

inline fun <R, T : R> ReactiveState<T>.getOrElse(default: () -> R): R {
    return handle(
        success = { it },
        exception = { default() },
        notReady = default
    )
}