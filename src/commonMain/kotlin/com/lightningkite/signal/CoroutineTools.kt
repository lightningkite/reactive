package com.lightningkite.signal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


@OptIn(ExperimentalStdlibApi::class)
fun CoroutineScope.load(context: CoroutineContext = EmptyCoroutineContext, action: suspend () -> Unit): Job {
    val state = RawSignal<Unit>()
    val result = launch(
        context,
        block = {
            val r = signalState { action() }
            state.state = r
        },
        start = if (coroutineContext[CoroutineDispatcher.Key]?.isDispatchNeeded(
                coroutineContext
            ) == false
        ) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT
    )
    coroutineContext[StatusListener]?.loading(state)
    return result
}

class WaitGate(permit: Boolean = false) {
    var permit: Boolean = permit
        set(value) {
            field = value
            if (value) {
                for (continuation in continuations) {
                    continuation.resume(Unit)
                }
                continuations.clear()
            }
        }
    fun permitOnce() {
        permit = true
        permit = false
    }
    val continuations = ArrayList<Continuation<Unit>>()
    suspend fun await(): Unit {
        if (permit) return
        else return suspendCancellableCoroutine {
            continuations.add(it)
        }
    }
    fun abandon() {
        for (continuation in continuations) {
            continuation.resumeWithException(CancellationException("abandoned as requested"))
        }
        continuations.clear()
    }
}