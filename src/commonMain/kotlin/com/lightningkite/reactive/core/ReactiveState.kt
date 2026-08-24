package com.lightningkite.reactive.core

import com.lightningkite.reactive.context.ReactiveLoading
import kotlinx.coroutines.CancellationException
import kotlin.jvm.JvmInline

/**
 * Represents the state of a reactive value, including loading, success, and error conditions.
 *
 * - A [ReactiveState] can hold a ready value, a loading state, an error state, or [notActive].
 * - Use [ready] to check if the value is available, [success] to check if it is available and not an error, and [exception] to retrieve any error.
 * - Listeners of [Reactive] are only notified when the [ReactiveState] changes.
 * - [ReactiveState] provides methods for safely handling, mapping, and retrieving the underlying value.
 *
 * ### notReady versus notActive
 *
 * Both mean "no value", for different reasons, and both make [ready] false:
 *
 * - [Companion.notReady]: something is maintaining this value, and it does not have one right now -
 *   it is loading, or one of its dependencies is not ready.
 * - [Companion.notActive]: *nothing* is maintaining this value. Lazy reactives like `remember` only
 *   calculate while they have listeners, so with none they cannot vouch for any value. Sources whose
 *   value is accurate whether or not anyone is listening - `Signal`, `Constant` - never report it.
 *
 * The distinction is what makes it safe to read a value without subscribing: anything other than
 * [notActive] is current, no matter who is (or isn't) listening. On [notActive] you must subscribe -
 * see `awaitOnce` - or accept that there is no value to be had.
 */
@JvmInline
@OptIn(InternalReactiveApi::class)
public value class ReactiveState<out T>(public val raw: T) {
    public inline val active: Boolean get() = raw != InternalReactiveNotActive
    public inline val ready: Boolean get() = active && raw != InternalReactiveNotReady
    public inline val success: Boolean get() = ready && raw !is InternalReactiveThrownException

    /** True when nothing is maintaining this value; see the [ReactiveState] docs. */
    public inline val notActive: Boolean get() = raw == InternalReactiveNotActive

    public inline fun <R> onSuccess(action: (T)->R): R? = handle(
        success = { action(it) },
        exception = { null },
        notReady = { null }
    )
    public inline val exception: Exception? get() = (raw as? InternalReactiveThrownException)?.exception

    @Deprecated("Only use this if you are *Absolutely Sure* that there is a value ready to retrieve. Otherwise, use `handle`.")
    public fun get(): T = handle(
        success = { it },
        exception = { throw it },
        notReady = { throw NotReadyException() },
        notActive = { throw NotActiveException() }
    )

    public fun getOrNull(): T? = handle(
        success = { it },
        exception = { null },
        notReady = { null }
    )

    public companion object Companion {
        @Suppress("UNCHECKED_CAST")
        public val notReady: ReactiveState<Nothing> = ReactiveState<Any?>(InternalReactiveNotReady) as ReactiveState<Nothing>

        /** No value, because nothing is maintaining one; see the [ReactiveState] docs. */
        @Suppress("UNCHECKED_CAST")
        public val notActive: ReactiveState<Nothing> = ReactiveState<Any?>(InternalReactiveNotActive) as ReactiveState<Nothing>

        @Suppress("UNCHECKED_CAST")
        public fun <T> exception(exception: Exception): ReactiveState<T> = (if(exception is CancellationException) notReady else ReactiveState<Any?>(InternalReactiveThrownException(exception))) as ReactiveState<T>
        @Suppress("UNCHECKED_CAST")
        public fun <T> wrap(value: T): ReactiveState<T> = ReactiveState<Any?>(InternalReactiveWrapper(value)) as ReactiveState<T>
    }
    @Suppress("UNCHECKED_CAST")
    public inline fun <B> map(mapper: (T)->B): ReactiveState<B> {
        // notActive propagates like the other valueless states: a value derived from a source
        // nobody is maintaining is equally unmaintained.
        if(raw is InternalReactiveNotReady || raw is InternalReactiveNotActive || raw is InternalReactiveThrownException) return this as ReactiveState<B>
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
    /**
     * Handles [Companion.notActive] the same way as [notReady], because "nobody is maintaining a
     * value" and "there is no value yet" are the same thing to code that only wants to display or
     * wait for one. Use the four-argument overload to do something better, such as subscribing.
     */
    public inline fun <R> handle(
        success: (T)->R,
        exception: (Exception)->R,
        notReady: ()->R
    ): R = handle(success, exception, notReady, notReady)

    @Suppress("UNCHECKED_CAST")
    public inline fun <R> handle(
        success: (T)->R,
        exception: (Exception)->R,
        notReady: ()->R,
        notActive: ()->R
    ): R {
        return when(raw) {
            InternalReactiveNotReady -> notReady()
            InternalReactiveNotActive -> notActive()
            is InternalReactiveThrownException -> exception(raw.exception)
            is InternalReactiveWrapper<*> -> success(raw.other as T)
            else -> success(raw)
        }
    }

    public fun asResult(): Result<T> = handle(
        success = { Result.success(it) },
        exception = { Result.failure(it) },
        notReady = { Result.failure(NotReadyException()) },
        notActive = { Result.failure(NotActiveException()) }
    )

    override fun toString(): String = when(raw) {
        is InternalReactiveNotReady -> "NotReady"
        is InternalReactiveNotActive -> "NotActive"
        is InternalReactiveThrownException -> "ThrownException(${raw.exception})"
        is InternalReactiveWrapper<*> -> "ReadyW($raw)"
        else -> "Ready($raw)"
    }
}
@InternalReactiveApi
public data class InternalReactiveWrapper<T>(val other: T)
@InternalReactiveApi
public data class InternalReactiveThrownException(val exception: Exception)
@InternalReactiveApi
public object InternalReactiveNotReady
@InternalReactiveApi
public object InternalReactiveNotActive

public open class NotReadyException(message: String? = null) : IllegalStateException(message)

/**
 * Thrown when reading a value from a reactive that nothing is listening to, and which therefore
 * has no value to give. Subscribe to it first, or use `awaitOnce`, which subscribes for as long
 * as it takes to obtain a value.
 */
public class NotActiveException(message: String = "Nothing is listening to this reactive value, so it has no value to report. Subscribe to it, or use awaitOnce.") : NotReadyException(message)

public inline fun <T> reactiveState(action: () -> T): ReactiveState<T> {
    @OptIn(InternalReactiveApi::class)
    return try {
        ReactiveState(action())
    } catch (e: CancellationException) {
        // A cancellation means the coroutine running `action` was torn down mid-calculation - it
        // must propagate so the caller's suspension point actually stops, rather than being
        // reinterpreted as "not ready" and letting the calculation resume past its cancellation.
        throw e
    } catch (_: ReactiveLoading) {
        ReactiveState.notReady
    } catch (e: Exception) {
        ReactiveState.exception(e)
    }
}

public fun <T> Result<T>.toReactiveState(): ReactiveState<T> {
    @Suppress("UNCHECKED_CAST")
    return if(this.isFailure) ReactiveState.exception(this.exceptionOrNull() as Exception)
    else ReactiveState.wrap(this.getOrNull() as T)
}