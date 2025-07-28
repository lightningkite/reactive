package com.lightningkite.reactive.lensing

import com.lightningkite.reactive.context.CalculationContext
import com.lightningkite.reactive.core.Listenable
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.MutableWithReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.ReactiveValue
import com.lightningkite.reactive.core.BaseListenable
import com.lightningkite.reactive.core.BaseReactiveValue
import com.lightningkite.reactive.core.Constant
import kotlinx.coroutines.*
import kotlin.jvm.JvmName


fun <T> Listenable.lensListenable(
    get: () -> T
): Reactive<T> = ValueLens(object: ReactiveValue<Unit>, Listenable by this{
    override val value: Unit get() = Unit
}, { get() })

fun <O, T> Reactive<O>.lens(
    get: (O) -> T
): Reactive<T> = Lens(this, get)

fun <O, T> MutableReactive<O>.lens(
    get: (O) -> T,
    modify: (O, T) -> O
): MutableReactive<T> = ModifyLens(this, get, modify)

fun <O, T> MutableReactive<O>.lens(
    get: (O) -> T,
    set: (T) -> O
): MutableReactive<T> = SetLens(this, get, set)

fun <O, T> ReactiveValue<O>.lens(
    get: (O) -> T
): ReactiveValue<T> = ValueLens(this, get)

fun <O, T> MutableReactiveValue<O>.lens(
    get: (O) -> T,
    set: (T) -> O
): MutableReactiveValue<T> = SetValueLens(this, get, set)

fun <O, T> MutableReactiveValue<O>.lens(
    get: (O) -> T,
    modify: (O, T) -> O
): MutableReactiveValue<T> = ModifyValueLens(this, get, modify)
