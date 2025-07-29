package com.lightningkite.reactive.extensions

import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.reactive.lensing.validation.MutableValidated
import com.lightningkite.reactive.lensing.validation.MutableValidatedValue
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

@JvmName("containsList")
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

// Validated variants


fun MutableValidated<String?>.nullToBlank(): MutableValidated<String> = lens(
    get = { it ?: "" },
    set = { it.takeUnless { it.isBlank() } }
)

@JvmName("writableStringAsDoubleValidated")
fun MutableValidated<String>.asDouble(): MutableValidated<Double?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toDoubleOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsFloatValidated")
fun MutableValidated<String>.asFloat(): MutableValidated<Float?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toFloatOrNull() }, set = { it?.toDouble()?.commaString() ?: "" })

@JvmName("writableStringAsByteValidated")
fun MutableValidated<String>.asByte(): MutableValidated<Byte?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toByteOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsShortValidated")
fun MutableValidated<String>.asShort(): MutableValidated<Short?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toShortOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsIntValidated")
fun MutableValidated<String>.asInt(): MutableValidated<Int?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toIntOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsLongValidated")
fun MutableValidated<String>.asLong(): MutableValidated<Long?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toLongOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsByteHexValidated")
fun MutableValidated<String>.asByteHex(): MutableValidated<Byte?> = lens(get = { it.toByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUByteHexValidated")
fun MutableValidated<String>.asUByteHex(): MutableValidated<UByte?> = lens(get = { it.toUByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsShortHexValidated")
fun MutableValidated<String>.asShortHex(): MutableValidated<Short?> = lens(get = { it.toShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUShortHexValidated")
fun MutableValidated<String>.asUShortHex(): MutableValidated<UShort?> = lens(get = { it.toUShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsIntHexValidated")
fun MutableValidated<String>.asIntHex(): MutableValidated<Int?> = lens(get = { it.toIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUIntHexValidated")
fun MutableValidated<String>.asUIntHex(): MutableValidated<UInt?> = lens(get = { it.toUIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsLongHexValidated")
fun MutableValidated<String>.asLongHex(): MutableValidated<Long?> = lens(get = { it.toLongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsULongHexValidated")
fun MutableValidated<String>.asULongHex(): MutableValidated<ULong?> = lens(get = { it.toULongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableIntAsDoubleNullableValidated")
fun MutableValidated<Int?>.asDouble(): MutableValidated<Double?> = lens(get = { it?.toDouble() }, set = { it?.toInt() })

@JvmName("writableStringAsDoubleValidatedValue")
fun MutableValidatedValue<String>.asDouble(): MutableValidatedValue<Double?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toDoubleOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsFloatValidatedValue")
fun MutableValidatedValue<String>.asFloat(): MutableValidatedValue<Float?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toFloatOrNull() }, set = { it?.toDouble()?.commaString() ?: "" })

@JvmName("writableStringAsByteValidatedValue")
fun MutableValidatedValue<String>.asByte(): MutableValidatedValue<Byte?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toByteOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsShortValidatedValue")
fun MutableValidatedValue<String>.asShort(): MutableValidatedValue<Short?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toShortOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsIntValidatedValue")
fun MutableValidatedValue<String>.asInt(): MutableValidatedValue<Int?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toIntOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsLongValidatedValue")
fun MutableValidatedValue<String>.asLong(): MutableValidatedValue<Long?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toLongOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsByteHexValidatedValue")
fun MutableValidatedValue<String>.asByteHex(): MutableValidatedValue<Byte?> = lens(get = { it.toByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUByteHexValidatedValue")
fun MutableValidatedValue<String>.asUByteHex(): MutableValidatedValue<UByte?> = lens(get = { it.toUByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsShortHexValidatedValue")
fun MutableValidatedValue<String>.asShortHex(): MutableValidatedValue<Short?> = lens(get = { it.toShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUShortHexValidatedValue")
fun MutableValidatedValue<String>.asUShortHex(): MutableValidatedValue<UShort?> = lens(get = { it.toUShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsIntHexValidatedValue")
fun MutableValidatedValue<String>.asIntHex(): MutableValidatedValue<Int?> = lens(get = { it.toIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUIntHexValidatedValue")
fun MutableValidatedValue<String>.asUIntHex(): MutableValidatedValue<UInt?> = lens(get = { it.toUIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsLongHexValidatedValue")
fun MutableValidatedValue<String>.asLongHex(): MutableValidatedValue<Long?> = lens(get = { it.toLongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsULongHexValidatedValue")
fun MutableValidatedValue<String>.asULongHex(): MutableValidatedValue<ULong?> = lens(get = { it.toULongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableIntAsDoubleNullableValidatedValue")
fun MutableValidatedValue<Int?>.asDouble(): MutableValidatedValue<Double?> = lens(get = { it?.toDouble() }, set = { it?.toInt() })
