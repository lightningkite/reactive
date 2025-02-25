package com.lightningkite.readable


object Never: Readable<Nothing> {
    override val state: ReadableState<Nothing> get() = ReadableState.notReady
    override fun addListener(listener: () -> Unit): () -> Unit = {}
}