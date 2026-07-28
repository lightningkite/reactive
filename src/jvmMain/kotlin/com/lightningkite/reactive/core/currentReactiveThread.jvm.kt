package com.lightningkite.reactive.core

internal actual fun currentReactiveThread(): Any? = Thread.currentThread()
