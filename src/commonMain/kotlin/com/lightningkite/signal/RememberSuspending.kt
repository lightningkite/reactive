package com.lightningkite.signal

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

class RememberSuspending<T>(
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    val useLastWhileLoading: Boolean = false,
    private val action: suspend CalculationContext.() -> T
) : BaseReactive<T>(), CalculationContext {
    private var job = SupervisorJob()
    private val restOfContext = (coroutineContext ?: EmptyCoroutineContext) +
            CoroutineExceptionHandler { coroutineContext, throwable ->
                if (throwable !is CancellationException) {
                    Reactive.reportException(throwable)
                }
            }
    override val coroutineContext: CoroutineContext get() = restOfContext + job
    private val me = Random.nextInt()

    private var instanceNumber: Int = 1
    override fun activate() {
        super.activate()
        reactiveSuspending(action = {
            try {
                val result = ReactiveState(action(this))
                if (result == state) return@reactiveSuspending
                state = result
            } catch (e: CancellationException) {
                // just bail, since either we're already rerunning or this stuff doesn't matter anymore
                return@reactiveSuspending
            } catch (e: Exception) {
                state = ReactiveState.exception(e)
            }
        }, onLoad = {
            if(!useLastWhileLoading) {
                state = ReactiveState.notReady
            }
        })
    }

    override fun deactivate() {
        super.deactivate()
        job.cancel()
        job = SupervisorJob()
        state = ReactiveState.notReady
    }
}
/**
 * Desired behavior for shared:
 *
 * - Outside a reactive scope, [Reactive.await] invokes the action with no sharing
 * - Inside a reactive scope, [Reactive.await] starts the whole system listening and sharing the calculation.
 */
fun <T> rememberSuspending(coroutineContext: CoroutineContext = Dispatchers.Unconfined, useLastWhileLoading: Boolean = false, action: suspend CalculationContext.() -> T): Reactive<T> {
    return RememberSuspending(coroutineContext = coroutineContext, useLastWhileLoading = useLastWhileLoading, action = action)
}