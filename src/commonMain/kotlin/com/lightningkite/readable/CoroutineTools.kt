package com.lightningkite.readable

import com.lightningkite.jsoptimized.emptyVector
import com.lightningkite.jsoptimized.forEach
import com.lightningkite.jsoptimized.push
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
    val state = RawReadable<Unit>()
    val result = launch(
        context,
        block = {
            val r = readableState { action() }
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
                continuations.forEach {
                    it.resume(Unit)
                }
                continuations = emptyVector()
            }
        }
    fun permitOnce() {
        permit = true
        permit = false
    }
    var continuations = emptyVector<Continuation<Unit>>()
    suspend fun await(): Unit {
        if (permit) return
        else return suspendCancellableCoroutine {
            continuations.push(it)
        }
    }
    fun abandon() {
        continuations.forEach {
            it.resumeWithException(CancellationException("abandoned as requested"))
        }
        continuations = emptyVector()
    }
}