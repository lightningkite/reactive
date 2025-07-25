package com.lightningkite.signal


object Never: Reactive<Nothing> {
    override val state: ReactiveState<Nothing> get() = ReactiveState.notReady
    override fun addListener(listener: () -> Unit): () -> Unit = {}
}