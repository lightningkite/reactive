package com.lightningkite.reactive.core

import platform.Foundation.NSThread

internal actual fun currentReactiveThread(): Any? = NSThread.currentThread()
