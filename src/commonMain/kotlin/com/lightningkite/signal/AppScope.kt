package com.lightningkite.signal

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob


val AppJob = SupervisorJob()
val AppScope = CoroutineScope(AppJob + CoroutineExceptionHandler { coroutineContext, throwable ->
    Signal.reportException(throwable)
} + Dispatchers.Main.immediate)