package com.lightningkite.reactive.core

import kotlinx.coroutines.CancellationException

/**
 * One pre-built cause for the cancellations this library performs as a matter of routine, such as a
 * [Remember] shutting down.
 *
 * Passed to `Job.cancel(cause)` so kotlinx-coroutines does not build a fresh `JobCancellationException`
 * per call. On Kotlin/JS every Throwable captures a JS stack trace, and V8's capture cost grows with
 * stack depth, so thousands of routine cancellations during one page build were costing seconds.
 * Sharing one instance is safe: kotlinx never attaches suppressed exceptions to a CancellationException
 * root cause, and child jobs reuse their parent's cancellation cause rather than wrapping it.
 */
internal val RoutineCancellation: CancellationException = CancellationException("Routine cancellation by the reactive library")
