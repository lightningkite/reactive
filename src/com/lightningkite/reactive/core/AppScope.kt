package com.lightningkite.reactive.core

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

public val AppJob: CompletableJob = SupervisorJob()

public val AppScope: CoroutineScope = CoroutineScope(AppJob + CoroutineExceptionHandler { coroutineContext, throwable ->
    Reactive.reportException(throwable)
} + Dispatchers.Main.immediate)