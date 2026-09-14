package com.lightningkite.reactive.extensions

import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.reactive.lensing.validation.MutableValidated
import com.lightningkite.reactive.lensing.validation.MutableValidatedValue
import kotlin.collections.plus
import kotlin.jvm.JvmName

public infix fun <T> MutableReactive<T>.equalTo(value: T): MutableReactive<Boolean> = lens(
    get = { it == value },
    modify = { o, it -> if (it) value else o }
)

public fun <T : Any> MutableReactive<T?>.notNull(default: T): MutableReactive<T> = lens(
    get = { it ?: default },
    set = { it }
)

public fun MutableReactive<String?>.nullToBlank(): MutableReactive<String> = lens(
    get = { it ?: "" },
    set = { it.takeUnless { it.isBlank() } }
)

public infix fun <T> MutableReactive<Set<T>>.contains(value: T): MutableReactive<Boolean> = lens(
    get = { value in it },
    modify = { items, bool -> if (bool) items + value else items - value  }
)

@JvmName("containsList")
public infix fun <T> MutableReactive<List<T>>.contains(value: T): MutableReactive<Boolean> = lens(
    get = { value in it },
    modify = { items, bool -> if (bool) items + value else items - value }
)

@JvmName("writableStringAsDouble")
public fun MutableReactive<String>.asDouble(): MutableReactive<Double?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toDoubleOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsFloat")
public fun MutableReactive<String>.asFloat(): MutableReactive<Float?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toFloatOrNull() }, set = { it?.toDouble()?.commaString() ?: "" })

@JvmName("writableStringAsByte")
public fun MutableReactive<String>.asByte(): MutableReactive<Byte?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toByteOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsShort")
public fun MutableReactive<String>.asShort(): MutableReactive<Short?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toShortOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsInt")
public fun MutableReactive<String>.asInt(): MutableReactive<Int?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toIntOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsLong")
public fun MutableReactive<String>.asLong(): MutableReactive<Long?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toLongOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsByteHex")
public fun MutableReactive<String>.asByteHex(): MutableReactive<Byte?> = lens(get = { it.toByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUByteHex")
public fun MutableReactive<String>.asUByteHex(): MutableReactive<UByte?> = lens(get = { it.toUByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsShortHex")
public fun MutableReactive<String>.asShortHex(): MutableReactive<Short?> = lens(get = { it.toShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUShortHex")
public fun MutableReactive<String>.asUShortHex(): MutableReactive<UShort?> = lens(get = { it.toUShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsIntHex")
public fun MutableReactive<String>.asIntHex(): MutableReactive<Int?> = lens(get = { it.toIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUIntHex")
public fun MutableReactive<String>.asUIntHex(): MutableReactive<UInt?> = lens(get = { it.toUIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsLongHex")
public fun MutableReactive<String>.asLongHex(): MutableReactive<Long?> = lens(get = { it.toLongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsULongHex")
public fun MutableReactive<String>.asULongHex(): MutableReactive<ULong?> = lens(get = { it.toULongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableIntAsDoubleNullable")
public fun MutableReactive<Int?>.asDouble(): MutableReactive<Double?> = lens(get = { it?.toDouble() }, set = { it?.toInt() })

public fun MutableReactive<Double>.nullToZero(): MutableReactive<Double?> =
    object : MutableReactive<Double?>, Reactive<Double?> by this {
        override suspend fun set(value: Double?) {
            this@nullToZero.set(value ?: 0.0)
        }
    }

@JvmName("writableStringAsDouble")
public fun MutableReactiveValue<String>.asDouble(): MutableReactiveValue<Double?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toDoubleOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsFloat")
public fun MutableReactiveValue<String>.asFloat(): MutableReactiveValue<Float?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toFloatOrNull() }, set = { it?.toDouble()?.commaString() ?: "" })

@JvmName("writableStringAsByte")
public fun MutableReactiveValue<String>.asByte(): MutableReactiveValue<Byte?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toByteOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsShort")
public fun MutableReactiveValue<String>.asShort(): MutableReactiveValue<Short?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toShortOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsInt")
public fun MutableReactiveValue<String>.asInt(): MutableReactiveValue<Int?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toIntOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsLong")
public fun MutableReactiveValue<String>.asLong(): MutableReactiveValue<Long?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toLongOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsByteHex")
public fun MutableReactiveValue<String>.asByteHex(): MutableReactiveValue<Byte?> = lens(get = { it.toByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUByteHex")
public fun MutableReactiveValue<String>.asUByteHex(): MutableReactiveValue<UByte?> = lens(get = { it.toUByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsShortHex")
public fun MutableReactiveValue<String>.asShortHex(): MutableReactiveValue<Short?> = lens(get = { it.toShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUShortHex")
public fun MutableReactiveValue<String>.asUShortHex(): MutableReactiveValue<UShort?> = lens(get = { it.toUShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsIntHex")
public fun MutableReactiveValue<String>.asIntHex(): MutableReactiveValue<Int?> = lens(get = { it.toIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUIntHex")
public fun MutableReactiveValue<String>.asUIntHex(): MutableReactiveValue<UInt?> = lens(get = { it.toUIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsLongHex")
public fun MutableReactiveValue<String>.asLongHex(): MutableReactiveValue<Long?> = lens(get = { it.toLongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsULongHex")
public fun MutableReactiveValue<String>.asULongHex(): MutableReactiveValue<ULong?> = lens(get = { it.toULongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableIntAsDoubleNullable")
public fun MutableReactiveValue<Int?>.asDouble(): MutableReactiveValue<Double?> = lens(get = { it?.toDouble() }, set = { it?.toInt() })

// Validated variants


public fun MutableValidated<String?>.nullToBlank(): MutableValidated<String> = lens(
    get = { it ?: "" },
    set = { it.takeUnless { it.isBlank() } }
)

@JvmName("writableStringAsDoubleValidated")
public fun MutableValidated<String>.asDouble(): MutableValidated<Double?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toDoubleOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsFloatValidated")
public fun MutableValidated<String>.asFloat(): MutableValidated<Float?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toFloatOrNull() }, set = { it?.toDouble()?.commaString() ?: "" })

@JvmName("writableStringAsByteValidated")
public fun MutableValidated<String>.asByte(): MutableValidated<Byte?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toByteOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsShortValidated")
public fun MutableValidated<String>.asShort(): MutableValidated<Short?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toShortOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsIntValidated")
public fun MutableValidated<String>.asInt(): MutableValidated<Int?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toIntOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsLongValidated")
public fun MutableValidated<String>.asLong(): MutableValidated<Long?> = lens(get = { it.filter { it.isDigit() || it == '.' }.toLongOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsByteHexValidated")
public fun MutableValidated<String>.asByteHex(): MutableValidated<Byte?> = lens(get = { it.toByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUByteHexValidated")
public fun MutableValidated<String>.asUByteHex(): MutableValidated<UByte?> = lens(get = { it.toUByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsShortHexValidated")
public fun MutableValidated<String>.asShortHex(): MutableValidated<Short?> = lens(get = { it.toShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUShortHexValidated")
public fun MutableValidated<String>.asUShortHex(): MutableValidated<UShort?> = lens(get = { it.toUShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsIntHexValidated")
public fun MutableValidated<String>.asIntHex(): MutableValidated<Int?> = lens(get = { it.toIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUIntHexValidated")
public fun MutableValidated<String>.asUIntHex(): MutableValidated<UInt?> = lens(get = { it.toUIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsLongHexValidated")
public fun MutableValidated<String>.asLongHex(): MutableValidated<Long?> = lens(get = { it.toLongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsULongHexValidated")
public fun MutableValidated<String>.asULongHex(): MutableValidated<ULong?> = lens(get = { it.toULongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableIntAsDoubleNullableValidated")
public fun MutableValidated<Int?>.asDouble(): MutableValidated<Double?> = lens(get = { it?.toDouble() }, set = { it?.toInt() })

@JvmName("writableStringAsDoubleValidatedValue")
public fun MutableValidatedValue<String>.asDouble(): MutableValidatedValue<Double?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toDoubleOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsFloatValidatedValue")
public fun MutableValidatedValue<String>.asFloat(): MutableValidatedValue<Float?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toFloatOrNull() }, set = { it?.toDouble()?.commaString() ?: "" })

@JvmName("writableStringAsByteValidatedValue")
public fun MutableValidatedValue<String>.asByte(): MutableValidatedValue<Byte?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toByteOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsShortValidatedValue")
public fun MutableValidatedValue<String>.asShort(): MutableValidatedValue<Short?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toShortOrNull() }, set = { it?.toInt()?.commaString() ?: "" })

@JvmName("writableStringAsIntValidatedValue")
public fun MutableValidatedValue<String>.asInt(): MutableValidatedValue<Int?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toIntOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsLongValidatedValue")
public fun MutableValidatedValue<String>.asLong(): MutableValidatedValue<Long?> = lens(get = { it.filter { it.isDigit() || it == '-' || it == '.'}.toLongOrNull() }, set = { it?.commaString() ?: "" })

@JvmName("writableStringAsByteHexValidatedValue")
public fun MutableValidatedValue<String>.asByteHex(): MutableValidatedValue<Byte?> = lens(get = { it.toByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUByteHexValidatedValue")
public fun MutableValidatedValue<String>.asUByteHex(): MutableValidatedValue<UByte?> = lens(get = { it.toUByteOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsShortHexValidatedValue")
public fun MutableValidatedValue<String>.asShortHex(): MutableValidatedValue<Short?> = lens(get = { it.toShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUShortHexValidatedValue")
public fun MutableValidatedValue<String>.asUShortHex(): MutableValidatedValue<UShort?> = lens(get = { it.toUShortOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsIntHexValidatedValue")
public fun MutableValidatedValue<String>.asIntHex(): MutableValidatedValue<Int?> = lens(get = { it.toIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsUIntHexValidatedValue")
public fun MutableValidatedValue<String>.asUIntHex(): MutableValidatedValue<UInt?> = lens(get = { it.toUIntOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsLongHexValidatedValue")
public fun MutableValidatedValue<String>.asLongHex(): MutableValidatedValue<Long?> = lens(get = { it.toLongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableStringAsULongHexValidatedValue")
public fun MutableValidatedValue<String>.asULongHex(): MutableValidatedValue<ULong?> = lens(get = { it.toULongOrNull(16) }, set = { it?.toString(16) ?: "" })

@JvmName("writableIntAsDoubleNullableValidatedValue")
public fun MutableValidatedValue<Int?>.asDouble(): MutableValidatedValue<Double?> = lens(get = { it?.toDouble() }, set = { it?.toInt() })
