package com.lightningkite.reactive.core

/**
 * JS has no shared-memory threads - a web worker gets its own heap and cannot reach a reactive node
 * created elsewhere - so confinement cannot be violated and the check is skipped entirely.
 */
internal actual fun currentReactiveThread(): Any? = null
