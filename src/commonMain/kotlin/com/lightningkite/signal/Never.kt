package com.lightningkite.signal


object Never: Signal<Nothing> {
    override val state: SignalState<Nothing> get() = SignalState.notReady
    override fun addListener(listener: () -> Unit): () -> Unit = {}
}