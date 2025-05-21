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
        Signal.reportException(e)
    }
}

@Deprecated("Only exists to not break imports", level = DeprecationLevel.ERROR)
fun <T> Nothing.bind(): Nothing = TODO()

//infix fun <T> ImmediateWritable<T>.bind(master: Writable<T>) {
//    with(CoroutineScopeStack.current()) {
//        var setting = false
//        launch(key = this@bind) {
//            this@bind.set(master.await())
//            master.addListener {
//                if (setting) return@addListener
//                master.state.onSuccess {
//                    setting = true
//                    this@with.reporting(key = this@bind) {
//                        try {
//                            this@bind.value = (it)
//                        } finally {
//                            setting = false
//                        }
//                    }
//                }
//            }.also { onRemove(it) }
//            this@bind.addListener {
//                if (setting) return@addListener
//                this@bind.state.onSuccess {
//                    setting = true
//                    this@with.launch(key = this@bind) {
//                        try {
//                            master.set(it)
//                        } finally {
//                            setting = false
//                        }
//                    }
//                }
//            }.also { onRemove(it) }
//        }
//
//    }
//}
//
//infix fun <T> Writable<T>.bind(master: ImmediateWritable<T>) {
//    with(CoroutineScopeStack.current()) {
//        var setting = false
//        launch(key = this@bind) {
//            this@bind.set(master.value)
//            master.addListener {
//                if (setting) return@addListener
//                master.state.onSuccess {
//                    setting = true
//                    this@with.launch(key = this@bind) {
//                        try {
//                            this@bind.set(it)
//                        } finally {
//                            setting = false
//                        }
//                    }
//
//                }
//            }.also { onRemove(it) }
//            this@bind.addListener {
//                if (setting) return@addListener
//                this@bind.state.onSuccess {
//                    setting = true
//                    this@with.reporting(key = this@bind) {
//                        try {
//                            master.value = it
//                        } finally {
//                            setting = false
//                        }
//                    }
//                }
//            }.also { onRemove(it) }
//        }
//    }
//}
//
//infix fun <T> ImmediateWritable<T>.bind(master: ImmediateWritable<T>) {
//    with(CoroutineScopeStack.current()) {
//        var setting = false
//        this@bind.value = master.value
//        master.addListener {
//            if (setting) {
//                return@addListener
//            }
//            master.state.onSuccess {
//                setting = true
//                this@with.reporting(key = this@bind) {
//                    try {
//                        this@bind.value = it
//                    } finally {
//                        setting = false
//                    }
//                }
//            }
//        }.also { onRemove(it) }
//        this@bind.addListener {
//            if (setting) {
//                return@addListener
//            }
//            this@bind.state.onSuccess {
//                setting = true
//                this@with.reporting(key = this@bind) {
//                    try {
//                        master.value = it
//                    } finally {
//                        setting = false
//                    }
//                }
//            }
//        }.also { onRemove(it) }
//    }
//}

fun <T> Signal<T>.withWrite(action: suspend Signal<T>.(T) -> Unit): MutableSignal<T> =
    object : MutableSignal<T>, Signal<T> by this {
        override suspend fun set(value: T) {
            action(this@withWrite, value)
        }
    }

// Lenses
infix fun <T> MutableSignal<T>.equalTo(value: T): MutableSignal<Boolean> = lens(
    get = { it == value },
    modify = { o, it -> if (it) value else o }
)

infix fun <T> MutableSignal<Set<T>>.contains(value: T): MutableSignal<Boolean> = remember { value in this@contains() }.withWrite { on ->
    if (on) this@contains.set(this@contains.await() + value)
    else this@contains.set(this@contains.await() - value)
}

fun <T : Any> MutableSignal<T>.nullable(): MutableSignal<T?> =
    object : MutableSignal<T?>, Signal<T?> by this {
        override suspend fun set(value: T?) {
            if (value != null) this@nullable.set(value)
        }
    }

fun <T : Any> MutableSignal<T?>.notNull(default: T): MutableSignal<T> = lens(
    get = { it ?: default },
    set = { it }
)

val <T : Any> MutableSignal<T?>.waitForNotNull: MutableSignal<T>
    get() =
        object : MutableSignal<T>, Signal<T> by (this as Signal<T?>).waitForNotNull {
            override suspend fun set(value: T) = this@waitForNotNull.set(value)
        }

fun MutableSignal<String?>.nullToBlank(): MutableSignal<String> = lens(
    get = { it ?: "" },
    set = { it.takeUnless { it.isBlank() } }
)

@JvmName("writableStringAsDouble")
fun MutableSignal<String>.asDouble(): MutableSignal<Double?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toDoubleOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsFloat")
fun MutableSignal<String>.asFloat(): MutableSignal<Float?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toFloatOrNull() }, set = { it?.toDouble()?.commaString() ?: "" })

@JvmName("writableStringAsByte")
fun MutableSignal<String>.asByte(): MutableSignal<Byte?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toByteOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsShort")
fun MutableSignal<String>.asShort(): MutableSignal<Short?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toShortOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsInt")
fun MutableSignal<String>.asInt(): MutableSignal<Int?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toIntOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsLong")
fun MutableSignal<String>.asLong(): MutableSignal<Long?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toLongOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsByteHex")
fun MutableSignal<String>.asByteHex(): MutableSignal<Byte?> = lens(get = { it.toByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUByteHex")
fun MutableSignal<String>.asUByteHex(): MutableSignal<UByte?> = lens(get = { it.toUByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsShortHex")
fun MutableSignal<String>.asShortHex(): MutableSignal<Short?> = lens(get = { it.toShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUShortHex")
fun MutableSignal<String>.asUShortHex(): MutableSignal<UShort?> = lens(get = { it.toUShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsIntHex")
fun MutableSignal<String>.asIntHex(): MutableSignal<Int?> = lens(get = { it.toIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUIntHex")
fun MutableSignal<String>.asUIntHex(): MutableSignal<UInt?> = lens(get = { it.toUIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsLongHex")
fun MutableSignal<String>.asLongHex(): MutableSignal<Long?> = lens(get = { it.toLongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsULongHex")
fun MutableSignal<String>.asULongHex(): MutableSignal<ULong?> = lens(get = { it.toULongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableIntAsDoubleNullable")
fun MutableSignal<Int?>.asDouble(): MutableSignal<Double?> = lens(get = { it?.toDouble() }, set = { it?.toInt() })

fun MutableSignal<Double>.nullToZero(): MutableSignal<Double?> =
    object : MutableSignal<Double?>, Signal<Double?> by this {
        override suspend fun set(value: Double?) {
            this@nullToZero.set(value ?: 0.0)
        }
    }

@JvmName("writableStringAsDouble")
fun MutableValueSignal<String>.asDouble(): MutableValueSignal<Double?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toDoubleOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsFloat")
fun MutableValueSignal<String>.asFloat(): MutableValueSignal<Float?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toFloatOrNull() }, set = { it?.toDouble()?.commaString() ?: "" })

@JvmName("writableStringAsByte")
fun MutableValueSignal<String>.asByte(): MutableValueSignal<Byte?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toByteOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsShort")
fun MutableValueSignal<String>.asShort(): MutableValueSignal<Short?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toShortOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsInt")
fun MutableValueSignal<String>.asInt(): MutableValueSignal<Int?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toIntOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsLong")
fun MutableValueSignal<String>.asLong(): MutableValueSignal<Long?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toLongOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsByteHex")
fun MutableValueSignal<String>.asByteHex(): MutableValueSignal<Byte?> = lens(get = { it.toByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUByteHex")
fun MutableValueSignal<String>.asUByteHex(): MutableValueSignal<UByte?> = lens(get = { it.toUByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsShortHex")
fun MutableValueSignal<String>.asShortHex(): MutableValueSignal<Short?> = lens(get = { it.toShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUShortHex")
fun MutableValueSignal<String>.asUShortHex(): MutableValueSignal<UShort?> = lens(get = { it.toUShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsIntHex")
fun MutableValueSignal<String>.asIntHex(): MutableValueSignal<Int?> = lens(get = { it.toIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUIntHex")
fun MutableValueSignal<String>.asUIntHex(): MutableValueSignal<UInt?> = lens(get = { it.toUIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsLongHex")
fun MutableValueSignal<String>.asLongHex(): MutableValueSignal<Long?> = lens(get = { it.toLongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsULongHex")
fun MutableValueSignal<String>.asULongHex(): MutableValueSignal<ULong?> = lens(get = { it.toULongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableIntAsDoubleNullable")
fun MutableValueSignal<Int?>.asDouble(): MutableValueSignal<Double?> = lens(get = { it?.toDouble() }, set = { it?.toInt() })

suspend infix fun <T> MutableSignal<T>.modify(action: suspend (T) -> T) {
    set(action(await()))
}

suspend infix fun <T> MutableValueSignal<T>.modify(action: suspend (T) -> T) {
    value = action(value)
}

suspend fun MutableSignal<Boolean>.toggle() { set(!awaitOnce()) }
fun MutableValueSignal<Boolean>.toggle() { value = !value }

fun CalculationContext.use(resourceUse: ResourceUse) {
    val x = resourceUse.beginUse()
    onRemove { x() }
}

fun <T, WRITE : MutableSignal<T>> WRITE.interceptWrite(action: suspend WRITE.(T) -> Unit): MutableSignal<T> =
    object : MutableSignal<T>, Signal<T> by this {
        override suspend fun set(value: T) {
            action(this@interceptWrite, value)
        }
    }

fun <T> Signal<MutableSignal<T>>.flatten(): MutableSignal<T> = remember { this@flatten()() }
    .withWrite { this@flatten.state.onSuccess { s -> s set it } }


interface SignalEmitter<T>: CoroutineScope {
    fun emit(value: T)
}

fun <T> CoroutineScope.signal(emitter: suspend SignalEmitter<T>.() -> Unit): Signal<T> {
    val prop = LateInitSignal<T>()
    launch {
        emitter(object : SignalEmitter<T>, CoroutineScope by this {
            override fun emit(value: T) {
                prop.value = value
            }
        })
    }
    return prop
}

fun <T> rememberProcess(scope: CoroutineScope = AppScope, emitter: suspend SignalEmitter<T>.() -> Unit): Signal<T> {
    return object: BaseSignal<T>() {
        var job: Job? = null
        override fun activate() {
            state = SignalState.notReady
            job = scope.launch {
                emitter(object : SignalEmitter<T>, CoroutineScope by this@launch {
                    override fun emit(value: T) {
                        state = SignalState(value)
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
fun <T> sharedProcessRaw(scope: CoroutineScope = AppScope, emitter: suspend SignalEmitter<SignalState<T>>.() -> Unit): Signal<T> {
    return object: BaseSignal<T>() {
        var job: Job? = null
        override fun activate() {
            job = scope.launch {
                emitter(object : SignalEmitter<SignalState<T>>, CoroutineScope by this@launch {
                    override fun emit(value: SignalState<T>) {
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

fun <T> CoroutineScope.asyncReadable(action: suspend () -> T): Signal<T> {
    val prop = LateInitSignal<T>()
    launch {
        prop.value = action()
    }
    return prop
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Deferred<T>.signal() = object : BaseSignal<T>() {
    init {
        this@signal[Job]?.invokeOnCompletion {
            state = if (it == null) SignalState(getCompleted()) else SignalState.exception(it as? Exception ?: Exception("Must be exception, not throwable", it))
        }
    }
}
