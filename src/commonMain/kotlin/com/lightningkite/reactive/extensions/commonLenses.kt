package com.lightningkite.reactive.extensions

import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import kotlin.collections.plus
import kotlin.jvm.JvmName

infix fun <T> MutableReactive<T>.equalTo(value: T): MutableReactive<Boolean> = lens(
    get = { it == value },
    modify = { o, it -> if (it) value else o }
)

fun <T : Any> MutableReactive<T?>.notNull(default: T): MutableReactive<T> = lens(
    get = { it ?: default },
    set = { it }
)

fun MutableReactive<String?>.nullToBlank(): MutableReactive<String> = lens(
    get = { it ?: "" },
    set = { it.takeUnless { it.isBlank() } }
)

infix fun <T> MutableReactive<Set<T>>.contains(value: T): MutableReactive<Boolean> = lens(
    get = { value in it },
    modify = { items, bool -> if (bool) items + value else items - value  }
)

infix fun <T> MutableReactive<List<T>>.contains(value: T): MutableReactive<Boolean> = lens(
    get = { value in it },
    modify = { items, bool -> if (bool) items + value else items - value }
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
