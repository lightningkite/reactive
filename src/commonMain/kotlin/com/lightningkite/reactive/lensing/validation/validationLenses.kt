package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.lensing.ModifyLens
import com.lightningkite.reactive.lensing.ModifyValueLens
import com.lightningkite.reactive.lensing.SetLens
import com.lightningkite.reactive.lensing.SetValueLens

class ValidatedSetLens<T, L>(
    source: MutableValidated<T>,
    get: (T) -> L,
    set: (L) -> T
) : MutableValidated<L>, SetLens<T, L>(source, get, set) {
    override val node: IssueNode = IssueNode(parent = source.node)
    override fun activate() {
        super.activate()
        node.connect()
    }
    override fun deactivate() {
        super.deactivate()
        node.disconnect()
    }
}

class ValidatedModifyLens<T, L>(
    source: MutableValidated<T>,
    get: (T) -> L,
    modify: (T, L) -> T
) : MutableValidated<L>, ModifyLens<T, L>(source, get, modify) {
    override val node: IssueNode = IssueNode(parent = source.node)
    override fun activate() {
        super.activate()
        node.connect()
    }
    override fun deactivate() {
        super.deactivate()
        node.disconnect()
    }
}

class ValidatedSetValueLens<T, L>(
    source: MutableValidatedValue<T>,
    get: (T) -> L,
    set: (L) -> T
) : MutableValidatedValue<L>, SetValueLens<T, L>(source, get, set) {
    override val node: IssueNode = IssueNode(parent = source.node)
    override fun activate() {
        super.activate()
        node.connect()
    }
    override fun deactivate() {
        super.deactivate()
        node.disconnect()
    }
}

class ValidatedModifyValueLens<T, L>(
    source: MutableValidatedValue<T>,
    get: (T) -> L,
    modify: (T, L) -> T
) : MutableValidatedValue<L>, ModifyValueLens<T, L>(source, get, modify) {
    override val node: IssueNode = IssueNode(parent = source.node)
    override fun activate() {
        super.activate()
        node.connect()
    }
    override fun deactivate() {
        super.deactivate()
        node.disconnect()
    }
}
