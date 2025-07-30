/**
 * Cleans up the imports and migrates to the new naming conventions.
 */

import java.io.File

fun String.replaceWords(old: String, new: String): String {
    return this.replace(Regex("(?<!\\w)(${Regex.escape(old)})(?!\\w)"), new)
}

File(".")
    .walkTopDown()
    .filter { it.name.endsWith(".kt") }
    .forEach {
        val text = it.readText()
        val imports = text.lineSequence()
            .filter { it.startsWith("import ") }
            .map { it.removePrefix("import ") }
        // File is clean, we can ditch
        if (imports.none { it.startsWith("com.lightningkite.readable.") }) return@forEach
        val fixedImports = imports
            .map {
                if(it == "com.lightningkite.kiteui.models.Action")
                    "com.lightningkite.kiteui.reactive.Action"
                else
                    it
            }
            .filter { !it.startsWith("com.lightningkite.readable.") }
            .plus(
                listOf(
                    "com.lightningkite.readable.*",
                    "com.lightningkite.reactive.core.*",
                    "com.lightningkite.reactive.context.*",
                    "com.lightningkite.reactive.extensions.*",
                    "com.lightningkite.reactive.lensing.*",
                    "com.lightningkite.kiteui.reactive.*",
                )
            )
            .sorted()
        val preImports = text.substringBefore("import ")
        val postImports = text.substringAfterLast("import ").substringAfter('\n')
        val importCorrectedText = preImports + fixedImports.joinToString("\n") { "import $it" } + "\n" + postImports

        val repl = importCorrectedText
            .replaceWords("Readable", "Reactive")
            .replaceWords("Writable", "MutableReactive")
            .replaceWords("SharedReadable", "Remember")
            .replaceWords("ImmediateReadable", "ReactiveValue")
            .replaceWords("ImmediateWritable", "MutableReactiveValue")
            .replaceWords("ReadableWithImmediateWrite", "ReactiveWithMutableValue")
            .replaceWords("ImmediateReadableWithWrite", "MutableWithReactiveValue")
            .replaceWords("Property", "Signal")
            .replaceWords("LazyProperty", "MutableRemember")
            .replaceWords("DebounceReadable", "DebounceReactive")
            .replaceWords("InternalReadableWrapper", "InternalReactiveWrapper")
            .replaceWords("RawReadable", "RawReactive")
            .replaceWords("ReadableState", "ReactiveState")
            .replaceWords("ReadableEmitter", "Emitter")
            .replaceWords("ImmediateWriteOnly", "MutableValue")
            .replaceWords("BaseImmediateReadable", "BaseReactiveValue")
            .replaceWords("BaseReadable", "BaseReactive")
            .replaceWords("BaseWritable", "BaseReactive")
            .replaceWords("BaseReadWrite", "BaseReactiveValue")
            .replaceWords("LateInitProperty", "LateInitSignal")
            .replaceWords("shared", "remember")
            .replaceWords("sharedProcess", "reactiveProcess")
            .replaceWords("readableState", "reactiveState")
            .replaceWords("toReadableState", "toReactiveState")
            .replaceWords("setImmediate", "valueSet")
            .replaceWords("asyncReadable", "asyncReactive")

        println("Fixed $it")
        it.writeText(repl)
    }
