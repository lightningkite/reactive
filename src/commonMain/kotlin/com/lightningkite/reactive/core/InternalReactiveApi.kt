package com.lightningkite.reactive.core

@Target(AnnotationTarget.CLASS)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This may change, use it at your own risk"
)
annotation class InternalReactiveApi