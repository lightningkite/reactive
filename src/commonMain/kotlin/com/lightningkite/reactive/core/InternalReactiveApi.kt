package com.lightningkite.reactive.core

@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This may change, use it at your own risk"
)
annotation class InternalReactiveApi

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is sensitive and needs to be handled with caution. Read all documentation before using."
)
annotation class SensitiveReactiveApi