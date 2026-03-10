package com.lightningkite.reactive.core

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

val AppJob = SupervisorJob()

val AppScope = CoroutineScope(AppJob + CoroutineExceptionHandler { coroutineContext, throwable ->
    Reactive.reportException(throwable)
} + Dispatchers.Main.immediate)