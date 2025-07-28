package com.lightningkite.signal

import kotlinx.coroutines.*
import kotlinx.coroutines.launch
import kotlin.js.JsName
import kotlin.jvm.JvmName

@JsName("invokeAllSafeMutable")
@JvmName("invokeAllSafeMutable")
fun MutableList<() -> Unit>.invokeAllSafe() = toList().invokeAllSafe()
fun List<() -> Unit>.invokeAllSafe() = forEach {
    try {
        it()
    } catch (e: Exception) {
        if (e is CancellationException) return@forEach
        Reactive.reportException(e)
    }
}

var <T> MutableValue<T>.value: T
    @Deprecated("This is syntax sugar for SETTING values. Retrieving will always throw an exception.", level = DeprecationLevel.ERROR)
    get() = throw IllegalStateException("Attempted to retrieve value for set-only property")
    @JvmName("setValue2")
    set(value) {
        println("Setting outer value: $value")
        valueSet(value)
    }


fun <T> Reactive<T>.withWrite(action: suspend Reactive<T>.(T) -> Unit): MutableReactive<T> =
    object : MutableReactive<T>, Reactive<T> by this {
        override suspend fun set(value: T) {
            action(this@withWrite, value)
        }
    }

// Lenses
infix fun <T> MutableReactive<T>.equalTo(value: T): MutableReactive<Boolean> = lens(
    get = { it == value },
    modify = { o, it -> if (it) value else o }
)

infix fun <T> MutableReactive<Set<T>>.contains(value: T): MutableReactive<Boolean> = remember { value in this@contains() }.withWrite { on ->
    if (on) this@contains.set(this@contains.await() + value)
    else this@contains.set(this@contains.await() - value)
}

fun <T : Any> MutableReactive<T>.nullable(): MutableReactive<T?> =
    object : MutableReactive<T?>, Reactive<T?> by this {
        override suspend fun set(value: T?) {
            if (value != null) this@nullable.set(value)
        }
    }

fun <T : Any> MutableReactive<T?>.notNull(default: T): MutableReactive<T> = lens(
    get = { it ?: default },
    set = { it }
)

val <T : Any> MutableReactive<T?>.waitForNotNull: MutableReactive<T>
    get() =
        object : MutableReactive<T>, Reactive<T> by (this as Reactive<T?>).waitForNotNull {
            override suspend fun set(value: T) = this@waitForNotNull.set(value)
        }

fun MutableReactive<String?>.nullToBlank(): MutableReactive<String> = lens(
    get = { it ?: "" },
    set = { it.takeUnless { it.isBlank() } }
)

@JvmName("writableStringAsDouble")
fun MutableReactive<String>.asDouble(): MutableReactive<Double?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toDoubleOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsFloat")
fun MutableReactive<String>.asFloat(): MutableReactive<Float?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toFloatOrNull() }, set = { it?.toDouble()?.commaString() ?: "" })

@JvmName("writableStringAsByte")
fun MutableReactive<String>.asByte(): MutableReactive<Byte?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toByteOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsShort")
fun MutableReactive<String>.asShort(): MutableReactive<Short?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toShortOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsInt")
fun MutableReactive<String>.asInt(): MutableReactive<Int?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toIntOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsLong")
fun MutableReactive<String>.asLong(): MutableReactive<Long?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toLongOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsByteHex")
fun MutableReactive<String>.asByteHex(): MutableReactive<Byte?> = lens(get = { it.toByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUByteHex")
fun MutableReactive<String>.asUByteHex(): MutableReactive<UByte?> = lens(get = { it.toUByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsShortHex")
fun MutableReactive<String>.asShortHex(): MutableReactive<Short?> = lens(get = { it.toShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUShortHex")
fun MutableReactive<String>.asUShortHex(): MutableReactive<UShort?> = lens(get = { it.toUShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsIntHex")
fun MutableReactive<String>.asIntHex(): MutableReactive<Int?> = lens(get = { it.toIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUIntHex")
fun MutableReactive<String>.asUIntHex(): MutableReactive<UInt?> = lens(get = { it.toUIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsLongHex")
fun MutableReactive<String>.asLongHex(): MutableReactive<Long?> = lens(get = { it.toLongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsULongHex")
fun MutableReactive<String>.asULongHex(): MutableReactive<ULong?> = lens(get = { it.toULongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableIntAsDoubleNullable")
fun MutableReactive<Int?>.asDouble(): MutableReactive<Double?> = lens(get = { it?.toDouble() }, set = { it?.toInt() })

fun MutableReactive<Double>.nullToZero(): MutableReactive<Double?> =
    object : MutableReactive<Double?>, Reactive<Double?> by this {
        override suspend fun set(value: Double?) {
            this@nullToZero.set(value ?: 0.0)
        }
    }

@JvmName("writableStringAsDouble")
fun MutableReactiveValue<String>.asDouble(): MutableReactiveValue<Double?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toDoubleOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsFloat")
fun MutableReactiveValue<String>.asFloat(): MutableReactiveValue<Float?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toFloatOrNull() }, set = { it?.toDouble()?.commaString() ?: "" })

@JvmName("writableStringAsByte")
fun MutableReactiveValue<String>.asByte(): MutableReactiveValue<Byte?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toByteOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsShort")
fun MutableReactiveValue<String>.asShort(): MutableReactiveValue<Short?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toShortOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsInt")
fun MutableReactiveValue<String>.asInt(): MutableReactiveValue<Int?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toIntOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsLong")
fun MutableReactiveValue<String>.asLong(): MutableReactiveValue<Long?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toLongOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsByteHex")
fun MutableReactiveValue<String>.asByteHex(): MutableReactiveValue<Byte?> = lens(get = { it.toByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUByteHex")
fun MutableReactiveValue<String>.asUByteHex(): MutableReactiveValue<UByte?> = lens(get = { it.toUByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsShortHex")
fun MutableReactiveValue<String>.asShortHex(): MutableReactiveValue<Short?> = lens(get = { it.toShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUShortHex")
fun MutableReactiveValue<String>.asUShortHex(): MutableReactiveValue<UShort?> = lens(get = { it.toUShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsIntHex")
fun MutableReactiveValue<String>.asIntHex(): MutableReactiveValue<Int?> = lens(get = { it.toIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUIntHex")
fun MutableReactiveValue<String>.asUIntHex(): MutableReactiveValue<UInt?> = lens(get = { it.toUIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsLongHex")
fun MutableReactiveValue<String>.asLongHex(): MutableReactiveValue<Long?> = lens(get = { it.toLongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsULongHex")
fun MutableReactiveValue<String>.asULongHex(): MutableReactiveValue<ULong?> = lens(get = { it.toULongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableIntAsDoubleNullable")
fun MutableReactiveValue<Int?>.asDouble(): MutableReactiveValue<Double?> = lens(get = { it?.toDouble() }, set = { it?.toInt() })

suspend infix fun <T> MutableReactive<T>.modify(action: suspend (T) -> T) {
    set(action(await()))
}

suspend infix fun <T> MutableReactiveValue<T>.modify(action: suspend (T) -> T) {
    value = action(value)
}

suspend fun MutableReactive<Boolean>.toggle() { set(!awaitOnce()) }
fun MutableReactiveValue<Boolean>.toggle() { value = !value }

fun CalculationContext.use(resourceUse: ResourceUse) {
    val x = resourceUse.beginUse()
    onRemove { x() }
}

fun <T, WRITE : MutableReactive<T>> WRITE.interceptWrite(action: suspend WRITE.(T) -> Unit): MutableReactive<T> =
    object : MutableReactive<T>, Reactive<T> by this {
        override suspend fun set(value: T) {
            action(this@interceptWrite, value)
        }
    }

fun <T> Reactive<MutableReactive<T>>.flatten(): MutableReactive<T> = remember { this@flatten()() }
    .withWrite { this@flatten.state.onSuccess { s -> s set it } }


interface Emitter<T>: CoroutineScope {
    fun emit(value: T)
}

@JvmName("reactiveProcessImplicit")
fun <T> CoroutineScope.reactiveProcess(emitter: suspend Emitter<T>.() -> Unit): Reactive<T> {
    val prop = LateInitSignal<T>()
    launch {
        emitter(object : Emitter<T>, CoroutineScope by this {
            override fun emit(value: T) {
                prop.value = value
            }
        })
    }
    return prop
}
fun <T> reactiveProcess(scope: CoroutineScope = AppScope, emitter: suspend Emitter<T>.() -> Unit): Reactive<T> {
    return object: BaseReactive<T>() {
        var job: Job? = null
        override fun activate() {
            state = ReactiveState.notReady
            job = scope.launch {
                emitter(object : Emitter<T>, CoroutineScope by this@launch {
                    override fun emit(value: T) {
                        state = ReactiveState(value)
                    }
                })
            }
        }
        override fun deactivate() {
            job?.cancel()
            job = null
        }
    }
}
fun <T> rawReactiveProcess(scope: CoroutineScope = AppScope, emitter: suspend Emitter<ReactiveState<T>>.() -> Unit): Reactive<T> {
    return object: BaseReactive<T>() {
        var job: Job? = null
        override fun activate() {
            job = scope.launch {
                emitter(object : Emitter<ReactiveState<T>>, CoroutineScope by this@launch {
                    override fun emit(value: ReactiveState<T>) {
                        state = value
                    }
                })
            }
        }
        override fun deactivate() {
            job?.cancel()
            job = null
        }
    }
}

fun <T> CoroutineScope.asyncReadable(action: suspend () -> T): Reactive<T> {
    val prop = LateInitSignal<T>()
    launch {
        prop.value = action()
    }
    return prop
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Deferred<T>.signal() = object : BaseReactive<T>() {
    init {
        this@signal[Job]?.invokeOnCompletion {
            state = if (it == null) ReactiveState(getCompleted()) else ReactiveState.exception(it as? Exception ?: Exception("Must be exception, not throwable", it))
        }
    }
}
