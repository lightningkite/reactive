package com.lightningkite.signal

import kotlinx.coroutines.CancellationException
import kotlin.jvm.JvmInline

@JvmInline
@OptIn(InternalSignalApi::class)
value class SignalState<out T>(val raw: T) {
    inline val ready: Boolean get() = raw !is InternalSignalNotReady
    inline val success: Boolean get() = ready && raw !is InternalSignalThrownException
    inline fun <R> onSuccess(action: (T)->R): R? = handle(
        success = { action(it) },
        exception = { null },
        notReady = { null }
    )
    inline val exception: Exception? get() = (raw as? InternalSignalThrownException)?.exception
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
        val notReady: SignalState<Nothing> = SignalState<Any?>(InternalSignalNotReady) as SignalState<Nothing>
        @Suppress("UNCHECKED_CAST")
        fun <T> exception(exception: Exception) = (if(exception is CancellationException) notReady else SignalState<Any?>(InternalSignalThrownException(exception))) as SignalState<T>
        @Suppress("UNCHECKED_CAST")
        fun <T> wrap(value: T) = SignalState<Any?>(InternalSignalWrapper(value)) as SignalState<T>
    }
    @Suppress("UNCHECKED_CAST")
    inline fun <B> map(mapper: (T)->B): SignalState<B> {
        if(raw is InternalSignalNotReady || raw is InternalSignalThrownException) return this as SignalState<B>
        if(raw is InternalSignalWrapper<*>) try {
            return SignalState(mapper(raw.other as T))
        } catch(e: Exception) {
            return exception(e)
        }
        return try {
            SignalState(mapper(raw))
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
            InternalSignalNotReady -> notReady()
            is InternalSignalThrownException -> exception(raw.exception)
            is InternalSignalWrapper<*> -> success(raw.other as T)
            else -> success(raw)
        }
    }

    fun asResult(): Result<T> = handle(success = { Result.success(it) }, exception = { Result.failure(it) }, notReady = { Result.failure(NotReadyException()) })

    override fun toString(): String = when(raw) {
        is InternalSignalNotReady -> "NotReady"
        is InternalSignalThrownException -> "ThrownException(${raw.exception})"
        is InternalSignalWrapper<*> -> "ReadyW($raw)"
        else -> "Ready($raw)"
    }
}
@InternalSignalApi data class InternalSignalWrapper<T>(val other: T)
@InternalSignalApi data class InternalSignalThrownException(val exception: Exception)
@InternalSignalApi object InternalSignalNotReady

inline fun <T> signalState(action: () -> T): SignalState<T> {
    return try {
        SignalState(action())
    } catch (e: CancellationException) {
        SignalState.notReady
    } catch (e: ReactiveLoading) {
        SignalState.notReady
    } catch (e: Exception) {
        SignalState.exception(e)
    }
}

inline fun <T> Result<T>.toReadableState(): SignalState<T> {
    @Suppress("UNCHECKED_CAST")
    return if(this.isFailure) SignalState.exception(this.exceptionOrNull() as Exception)
    else SignalState.wrap(this.getOrNull() as T)
}