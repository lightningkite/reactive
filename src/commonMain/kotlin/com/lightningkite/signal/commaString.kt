package com.lightningkite.signal

import kotlin.math.pow
import kotlin.math.roundToInt


fun Double.toStringNoExponential(): String {
    val preDecimal = toLong().toString()
    val r = rem(1)
    if (r == 0.0) return preDecimal
    val availableDigits = 10 - preDecimal.length
    val postDecimal = r.times(10.0.pow(availableDigits)).roundToInt()
    if (postDecimal == 0) return preDecimal
    else return preDecimal + "." + postDecimal.toString().padStart(availableDigits, '0').trimEnd('0')
}

fun Double.commaString(): String {
    val clean = this.toStringNoExponential().filter { it.isDigit() || it in setOf('.', '-') }
    val preDecimal = clean.substringBefore('.').reversed().chunked(3) { it.reversed() }.reversed().joinToString(",")
    val postDecimal = clean.substringAfter('.', "")
    return if (clean.contains('.')) "$preDecimal.$postDecimal" else preDecimal
}
fun Int.commaString(): String {
    return toString().substringBefore('.').reversed().chunked(3) { it.reversed() }.reversed().joinToString(",")
}
fun Long.commaString(): String {
    return toString().substringBefore('.').reversed().chunked(3) { it.reversed() }.reversed().joinToString(",")
}
