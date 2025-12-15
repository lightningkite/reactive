package com.lightningkite.reactive

import com.lightningkite.reactive.context.onRemove
import kotlin.test.Test

class OnRemoveTest {
    @Test
    fun test() {
        testContext {
            onRemove { println("Removal A") }
            onRemove { println("Removal B") }
            onRemove { println("Removal C"); throw IllegalStateException() }
            onRemove { println("Removal D") }
            onRemove { println("Removal E") }
        }
    }
}