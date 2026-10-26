package com.lightningkite.reactive.lensing.validation

import com.lightningkite.reactive.lensing.ModifyLensReactive
import com.lightningkite.reactive.lensing.ModifyLensReactiveValue
import com.lightningkite.reactive.lensing.SetLensReactive
import com.lightningkite.reactive.lensing.SetLensReactiveValue

public class ValidatedSetLens<T, L>(
    source: MutableValidated<T>,
    get: (T) -> L,
    set: (L) -> T
) : MutableValidated<L>, SetLensReactive<T, L>(source, get, set) {
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

public class ValidatedModifyLens<T, L>(
    source: MutableValidated<T>,
    get: (T) -> L,
    modify: (T, L) -> T
) : MutableValidated<L>, ModifyLensReactive<T, L>(source, get, modify) {
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

public class ValidatedSetValueLens<T, L>(
    source: MutableValidatedValue<T>,
    get: (T) -> L,
    set: (L) -> T
) : MutableValidatedValue<L>, SetLensReactiveValue<T, L>(source, get, set) {
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

public class ValidatedModifyValueLens<T, L>(
    source: MutableValidatedValue<T>,
    get: (T) -> L,
    modify: (T, L) -> T
) : MutableValidatedValue<L>, ModifyLensReactiveValue<T, L>(source, get, modify) {
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
