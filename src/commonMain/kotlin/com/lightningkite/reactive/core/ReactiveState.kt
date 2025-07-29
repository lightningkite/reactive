package com.lightningkite.reactive.core

import com.lightningkite.reactive.context.ReactiveLoading
import kotlinx.coroutines.CancellationException
import kotlin.jvm.JvmInline

/**
 * Represents the state of a reactive value, including loading, success, and error conditions.
 *
 * - A [ReactiveState] can hold a ready value, a loading state, or an error state.
 * - Use [ready] to check if the value is available, [success] to check if it is available and not an error, and [exception] to retrieve any error.
 * - Listeners of [Reactive] are only notified when the [ReactiveState] changes.
 * - [ReactiveState] provides methods for safely handling, mapping, and retrieving the underlying value.
 */
@JvmInline
@OptIn(InternalReactiveApi::class)
value class ReactiveState<out T>(val raw: T) {
    inline val ready: Boolean get() = raw !is InternalReactiveNotReady
    inline val success: Boolean get() = ready && raw !is InternalReactiveThrownException
    inline fun <R> onSuccess(action: (T)->R): R? = handle(
        success = { action(it) },
        exception = { null },
        notReady = { null }
    )
    inline val exception: Exception? get() = (raw as? InternalReactiveThrownException)?.exception
    @Deprecated("Only use this if you are *Absolutely Sure* that there is a value ready to retrieve. Otherwise, use `handle`.")
    fun get(): T = handle(
        success = { it },
        exception = { throw it },
        notReady = { throw NotReadyException() }
    )
    fun getOrNull(): T? = handle(
        success = { it },
        exception = { null },
        notReady = { null }
    )
    companion object Companion {
        @Suppress("UNCHECKED_CAST")
        val notReady: ReactiveState<Nothing> = ReactiveState<Any?>(InternalReactiveNotReady) as ReactiveState<Nothing>
        @Suppress("UNCHECKED_CAST")
        fun <T> exception(exception: Exception) = (if(exception is CancellationException) notReady else ReactiveState<Any?>(InternalReactiveThrownException(exception))) as ReactiveState<T>
        @Suppress("UNCHECKED_CAST")
        fun <T> wrap(value: T) = ReactiveState<Any?>(InternalReactiveWrapper(value)) as ReactiveState<T>
    }
    @Suppress("UNCHECKED_CAST")
    inline fun <B> map(mapper: (T)->B): ReactiveState<B> {
        if(raw is InternalReactiveNotReady || raw is InternalReactiveThrownException) return this as ReactiveState<B>
        if(raw is InternalReactiveWrapper<*>) try {
            return ReactiveState(mapper(raw.other as T))
        } catch(e: Exception) {
            return exception(e)
        }
        return try {
            ReactiveState(mapper(raw))
        } catch(e: Exception) {
            exception(e)
        }
    }
    @Suppress("UNCHECKED_CAST")
    inline fun <R> handle(
        success: (T)->R,
        exception: (Exception)->R,
        notReady: ()->R
    ): R {
        return when(raw) {
            InternalReactiveNotReady -> notReady()
            is InternalReactiveThrownException -> exception(raw.exception)
            is InternalReactiveWrapper<*> -> success(raw.other as T)
            else -> success(raw)
        }
    }

    fun asResult(): Result<T> = handle(success = { Result.success(it) }, exception = { Result.failure(it) }, notReady = { Result.failure(NotReadyException()) })

    override fun toString(): String = when(raw) {
        is InternalReactiveNotReady -> "NotReady"
        is InternalReactiveThrownException -> "ThrownException(${raw.exception})"
        is InternalReactiveWrapper<*> -> "ReadyW($raw)"
        else -> "Ready($raw)"
    }
}
@InternalReactiveApi
data class InternalReactiveWrapper<T>(val other: T)
@InternalReactiveApi
data class InternalReactiveThrownException(val exception: Exception)
@InternalReactiveApi
object InternalReactiveNotReady

class NotReadyException(message: String? = null) : IllegalStateException(message)

inline fun <T> reactiveState(action: () -> T): ReactiveState<T> {
    return try {
        ReactiveState(action())
    } catch (_: CancellationException) {
        ReactiveState.notReady
    } catch (_: ReactiveLoading) {
        ReactiveState.notReady
    } catch (e: Exception) {
        ReactiveState.exception(e)
    }
}

inline fun <T> Result<T>.toReactiveState(): ReactiveState<T> {
    @Suppress("UNCHECKED_CAST")
    return if(this.isFailure) ReactiveState.exception(this.exceptionOrNull() as Exception)
    else ReactiveState.wrap(this.getOrNull() as T)
}