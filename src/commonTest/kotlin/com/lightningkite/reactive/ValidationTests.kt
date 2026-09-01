package com.lightningkite.reactive

import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.modify
import com.lightningkite.reactive.lensing.lensByElementWithIdentity
import com.lightningkite.reactive.lensing.validation.Issue
import com.lightningkite.reactive.lensing.validation.IssueNode
import com.lightningkite.reactive.lensing.validation.MutableValidated
import com.lightningkite.reactive.lensing.validation.assert
import com.lightningkite.reactive.lensing.validation.assertReactive
import com.lightningkite.reactive.lensing.validation.audit
import com.lightningkite.reactive.lensing.validation.auditReactive
import com.lightningkite.reactive.lensing.validation.issues
import com.lightningkite.reactive.lensing.validation.validate
import com.lightningkite.reactive.lensing.validation.validateReactive
import com.lightningkite.reactive.lensing.validation.validated
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidationTests {
    data class Data(
        val id: Int = 0,
        val number: Double = 0.0,
        val name: String = ""
    )

    @Test fun backwardsCompatibleConstructors() {
        Issue.Warning("")
        Issue.Invalid("")
    }

    @Test fun issuesArePropagated() {
        testContext {
            val root = IssueNode(null)

            val issues = ArrayList<Issue>()
            reactive {
                issues.clear()
                issues.addAll(root.issues())
            }

            assertEquals(0, issues.size)

            val child = root.child()

            assertEquals(0, issues.size)

            child.report(Issue.Invalid("Test"))

            assertEquals(1, issues.size)

            child.report(null)

            assertEquals(0, issues.size)

            val grandchild = child.child()

            assertEquals(0, issues.size)

            grandchild.report(Issue.Invalid("Test"))

            assertEquals(1, issues.size)

            grandchild.report(null)

            assertEquals(0, issues.size)
        }
    }

    @Test fun issuesArePropagatedThroughLensing() {
        testContext {
            val root = Signal(Data()).validated()
            val id = root
                .lens(
                    get = { it.id },
                    modify = { o, it -> o.copy(id = it) }
                )
                .assert("Greater than 0") { it > 0 }

            // Make sure that issues only propagate if the lens is being used
            launch {
                assertEquals(0, root.issues().size, "Issues are propagating before the lens is used")
            }

            val context = reactive { rerunOn(id) } // Add dependency

            launch {
                assertEquals(1, root.issues().size)
            }

            id.value = 1

            launch {
                assertEquals(0, root.issues().size)
            }

            id.value = 0

            launch {
                assertEquals(1, root.issues().size)
            }

            context.cancel()

            launch {
                assertEquals(0, root.issues().size)
            }
        }
    }

    @Test fun validationLensingWorksByElement() {
        testContext {
            val listOfNumbers = Signal(listOf<Int>()).validated()

            val validated = listOfNumbers.lensByElementWithIdentity(
                identity = { it }
            ) { e ->
                e.assert("Must be greater than zero") { it > 0 }
            }

            launch {
                assertEquals(0, listOfNumbers.issues().size)
            }

            val loading = reactive {
                println("Starting")
                validated().forEach { element ->
                    rerunOn(element)
                }
                println("Done")
            }

            repeat(10) { len ->
                val list = List(len) { Random.Default.nextInt() }
                println("\nList: $list")
                listOfNumbers.value = list
                launch {
                    loading.await()
                    val issues = listOfNumbers.issues()
                    println("Issues (${issues.size}): $issues")
                    assertEquals(list.count { it <= 0 }, issues.size)
                    validated().forEach {
                        val current = it()
                        if (current <= 0) {
                            println("Changing $current to 1")
                            it.set(1)
                        }
                    }
                    assertEquals(0, listOfNumbers.issues().size)
                }
            }
        }
    }

    @Test fun echosDontClearIssues() {
        val root = Signal(Data()).validated()

        val id = root
            .lens<Double?>(
                get = { it.id.toDouble() },
                modify = { o, it -> if (it != null) o.copy(id = it.toInt()) else o }
            )
            .validate {
                println("Validating value $it")
                if (it == null) null
                else if (it - it.toInt() != 0.0) {
                    println("String: $it")
                    println("Diff: ${it - it.toInt()}")
                    "Cannot be a decimal"
                }
                else null
            }

        testContext {
            launch {
                assertEquals(0, root.issues().size)
            }

            reactive { rerunOn(id) }

            launch {
                assertEquals(0, root.issues().size)
            }

            id.value = 1.5

            launch {
                assertEquals(1, root.issues().size)
                delay(100)
                assertEquals(1, root.issues().size)
            }

            println("Going to 2")

            id.value = 2.0

            launch {
                assertEquals(0, root.issues().size)
            }

            id.value = 1.5

            launch {
                assertEquals(1, root.issues().size)
            }

            root.value = root.value.copy(id = 2)

            launch {
                assertEquals(0, root.issues().size)
            }

            id.value = 1.5
            root.value = root.value.copy(id = 2)

            launch {
                assertEquals(0, root.issues().size)
            }
        }
    }

    @Test fun auditReactiveRejectsSetWhenIssueSuppressesTheWrite() {
        testContext {
            val root = Signal(0).validated()
            val checked = root.auditReactive { value ->
                if (value < 0) Issue("Must be non-negative", setValue = false) else null
            }

            reactive { rerunOn(checked) } // activates the check

            launch {
                checked.set(5)
                assertEquals(5, root.value, "A valid write should reach the source")
            }

            launch {
                checked.set(-1)
                assertEquals(5, root.value, "A rejected write should not reach the source")
            }
        }
    }

    @Test fun auditReactiveWritesThroughWhenIssueAllowsTheWrite() {
        testContext {
            val root = Signal(0).validated()
            val checked = root.auditReactive { value ->
                if (value % 2 != 0) Issue("Odd number", setValue = true) else null
            }

            reactive { rerunOn(checked) }

            launch {
                checked.set(3)
                assertEquals(3, root.value, "setValue = true should still forward the write despite the issue")
            }
        }
    }

    @Test fun auditReactiveReportsTheIssueEvenWhenTheWriteIsRejected() {
        testContext {
            val root = Signal(0).validated()
            val checked = root.auditReactive { value ->
                if (value < 0) Issue("Must be non-negative", setValue = false) else null
            }

            reactive { rerunOn(checked) }

            launch {
                assertEquals(0, root.issues().size)
                checked.set(-1)
                assertEquals(1, root.issues().size, "The rejected value's issue should still be visible")
            }
        }
    }

    @Test fun auditReactiveCanSkipTheGateWithCheckForSetOnIssueFalse() {
        testContext {
            val root = Signal(0).validated()
            val checked = root.auditReactive(checkForSetOnIssue = false) { value ->
                if (value < 0) Issue("Must be non-negative", setValue = false) else null
            }

            reactive { rerunOn(checked) }

            launch {
                checked.set(-1)
                assertEquals(-1, root.value, "With gating disabled, even a setValue = false issue should not block the write")
            }
        }
    }

    @Test fun auditReactiveRevalidatesReactivelyAfterAWrite() {
        testContext {
            val minimum = Signal(0)
            val root = Signal(0).validated()
            val checked = root.auditReactive { value ->
                if (value < minimum()) Issue("Below minimum", setValue = false) else null
            }

            reactive { rerunOn(checked) }

            launch {
                checked.set(5)
                assertEquals(5, root.value)
                assertEquals(0, root.issues().size)
            }

            // No new write happens here - only a dependency the last check read changes.
            minimum.value = 10

            launch {
                assertEquals(
                    1, root.issues().size,
                    "The issue should update reactively when a dependency validate() reads changes, even without a new write"
                )
            }
        }
    }

    @Test fun validateReactiveSetOnIssueFalseRejectsTheWrite() {
        testContext {
            val root = Signal(0).validated()
            val checked = root.validateReactive(setOnIssue = false) { value ->
                if (value < 0) "Must be non-negative" else null
            }

            reactive { rerunOn(checked) }

            launch {
                checked.set(-1)
                assertEquals(0, root.value, "setOnIssue = false should reject the write, not just skip the check")
            }
        }
    }

    @Test fun auditReactiveValidatesTheExistingValueOnActivation() {
        testContext {
            val root = Signal(-1).validated()
            val checked = root.auditReactive { value ->
                if (value < 0) Issue("Must be non-negative", setValue = false) else null
            }

            reactive { rerunOn(checked) }

            launch {
                assertEquals(
                    1, checked.issues().size,
                    "The value already in the source should be flagged without needing a write first"
                )
            }
        }
    }

    @Test fun auditReactiveSetWorksWithoutAPriorListener() {
        testContext {
            val root = Signal(0).validated()
            val checked = root.auditReactive { value ->
                if (value < 0) Issue("Must be non-negative", setValue = false) else null
            }

            // Nothing has subscribed to `checked` (or its issues) yet - `set()` must not deadlock.
            launch {
                checked.set(5)
                assertEquals(5, root.value)
                checked.set(-1)
                assertEquals(5, root.value, "Still rejected even with no active listener")
            }
        }
    }

    @Test fun auditReactiveEchoesRejectedValueLikeAudit() {
        testContext {
            val syncSource = Signal(0).validated()
            val syncChecked = syncSource.audit { value ->
                if (value < 0) Issue("Must be non-negative", setValue = false) else null
            }

            val reactiveSource = Signal(0).validated()
            val reactiveChecked = reactiveSource.auditReactive { value ->
                if (value < 0) Issue("Must be non-negative", setValue = false) else null
            }

            reactive { rerunOn(syncChecked) }
            reactive { rerunOn(reactiveChecked) }

            launch {
                syncChecked.set(-1)
                reactiveChecked.set(-1)

                assertEquals(0, syncSource.value, "audit: rejected write should not reach the source")
                assertEquals(0, reactiveSource.value, "auditReactive: rejected write should not reach the source")

                assertEquals(
                    -1, syncChecked.state.getOrNull(),
                    "audit: the rejected value is still echoed locally"
                )
                assertEquals(
                    -1, reactiveChecked.state.getOrNull(),
                    "auditReactive: the rejected value should be echoed locally too, matching audit"
                )
            }
        }
    }

    // --- Chained validation across graphs of various sizes ---
    //
    // Each level of a chain creates a child IssueNode of the previous level's node (see
    // `ValidatedLens`/`Auditor`), so a chain of N validators forms a path of depth N in the
    // validation tree. `root.issues()` recursively walks that whole tree, so these tests exercise
    // that walk at a range of depths, using both inline (`audit`/`assert`) and reactive
    // (`auditReactive`/`assertReactive`) validation.

    private val chainDepths = listOf(1, 2, 5, 10, 20, 50, 100)

    private fun buildInlineScalarChain(root: MutableValidated<Int>, depth: Int): MutableValidated<Int> {
        var current = root
        repeat(depth) { level ->
            current = current.assert("Level $level must be positive") { it > 0 }
        }
        return current
    }

    private fun buildReactiveScalarChain(root: MutableValidated<Int>, depth: Int): MutableValidated<Int> {
        var current = root
        repeat(depth) { level ->
            current = current.assertReactive("Level $level must be positive") { it > 0 }
        }
        return current
    }

    @Test fun chainedInlineValidationAcrossGraphSizes() {
        for (depth in chainDepths) {
            testContext {
                val root = Signal(1).validated()
                val leaf = buildInlineScalarChain(root, depth)

                val context = reactive { rerunOn(leaf) }

                launch {
                    assertEquals(0, root.issues().size, "depth=$depth: a valid value should report no issues")
                }

                launch {
                    leaf.set(0)
                    assertEquals(
                        depth, root.issues().size,
                        "depth=$depth: every level's check should fail on the same invalid value"
                    )
                }

                launch {
                    leaf.set(1)
                    assertEquals(0, root.issues().size, "depth=$depth: issues should clear once the value is valid again")
                }

                context.cancel()
            }
        }
    }

    @Test fun chainedReactiveValidationAcrossGraphSizes() {
        for (depth in chainDepths) {
            testContext {
                val root = Signal(1).validated()
                val leaf = buildReactiveScalarChain(root, depth)

                val context = reactive { rerunOn(leaf) }

                launch {
                    assertEquals(0, root.issues().size, "depth=$depth: a valid value should report no issues")
                }

                launch {
                    leaf.set(0)
                    assertEquals(
                        depth, root.issues().size,
                        "depth=$depth: every level's check should fail on the same invalid value"
                    )
                }

                launch {
                    leaf.set(1)
                    assertEquals(0, root.issues().size, "depth=$depth: issues should clear once the value is valid again")
                }

                context.cancel()
            }
        }
    }

    data class TestObject(
        val name: String,
        val inner: TestObject? = null,
    )

    private fun MutableValidated<TestObject>.inner(): MutableValidated<TestObject> = lens(
        get = { it.inner ?: TestObject("") },
        modify = { o, new -> o.copy(inner = new) }
    )

    /** Builds a chain of [depth] nested [TestObject]s (levels `0..depth`), with a blank `name` at each level in [blankLevels]. */
    private fun nestedObject(depth: Int, blankLevels: Set<Int> = emptySet()): TestObject {
        fun build(level: Int): TestObject = TestObject(
            name = if (level in blankLevels) "" else "level-$level",
            inner = if (level < depth) build(level + 1) else null
        )
        return build(0)
    }

    private fun buildInlineObjectChain(root: MutableValidated<TestObject>, depth: Int): MutableValidated<TestObject> {
        var current = root.assert("Name required") { it.name.isNotBlank() }
        repeat(depth) {
            current = current.inner().assert("Name required") { it.name.isNotBlank() }
        }
        return current
    }

    private fun buildReactiveObjectChain(root: MutableValidated<TestObject>, depth: Int): MutableValidated<TestObject> {
        var current = root.assertReactive("Name required") { it.name.isNotBlank() }
        repeat(depth) {
            current = current.inner().assertReactive("Name required") { it.name.isNotBlank() }
        }
        return current
    }

    @Test fun chainedInlineValidationOverNestedObjectGraph() {
        for (depth in chainDepths) {
            testContext {
                val root = Signal(nestedObject(depth)).validated()
                val leaf = buildInlineObjectChain(root, depth)

                val context = reactive { rerunOn(leaf) }

                launch {
                    assertEquals(0, root.issues().size, "depth=$depth: a fully valid tree should report no issues")
                }

                launch {
                    root.set(nestedObject(depth, blankLevels = setOf(depth)))
                    assertEquals(
                        1, root.issues().size,
                        "depth=$depth: only the deepest level's issue should propagate"
                    )
                }

                launch {
                    root.set(nestedObject(depth, blankLevels = (0..depth).toSet()))
                    assertEquals(
                        depth + 1, root.issues().size,
                        "depth=$depth: every level's issue should propagate"
                    )
                }

                launch {
                    root.set(nestedObject(depth))
                    assertEquals(0, root.issues().size, "depth=$depth: issues should clear once every level is valid again")
                }

                context.cancel()
            }
        }
    }

    @Test fun chainedReactiveValidationOverNestedObjectGraph() {
        for (depth in chainDepths) {
            testContext {
                val root = Signal(nestedObject(depth)).validated()
                val leaf = buildReactiveObjectChain(root, depth)

                val context = reactive { rerunOn(leaf) }

                launch {
                    assertEquals(0, root.issues().size, "depth=$depth: a fully valid tree should report no issues")
                }

                launch {
                    root.set(nestedObject(depth, blankLevels = setOf(depth)))
                    assertEquals(
                        1, root.issues().size,
                        "depth=$depth: only the deepest level's issue should propagate"
                    )
                }

                launch {
                    root.set(nestedObject(depth, blankLevels = (0..depth).toSet()))
                    assertEquals(
                        depth + 1, root.issues().size,
                        "depth=$depth: every level's issue should propagate"
                    )
                }

                launch {
                    root.set(nestedObject(depth))
                    assertEquals(0, root.issues().size, "depth=$depth: issues should clear once every level is valid again")
                }

                context.cancel()
            }
        }
    }

    // --- Known limitation: very large graphs currently crash on read ---
    //
    // `IssueNode.issues` (IssueNode.kt:104) is a `remember { ... children().flatMap { it.issues() } }`
    // per node, and reading it activates every descendant's `remember` synchronously in the same call
    // stack (`Dispatchers.Unconfined`). A deep enough graph - as built by `buildInlineObjectChain`/
    // `buildReactiveObjectChain`, which add both a `lens()` node and an `assert()` node per level -
    // overflows the stack well before it would report a `ReactiveReentrancyException`. On the JVM this
    // was confirmed to pass at depth 100 and fail (StackOverflowError) at depth 250.
    //
    // These two tests pin down that *some* failure currently happens at a depth deep enough to trigger
    // it, without depending on the exact exception type (which is JVM/JS/Native-specific, and may well
    // be the ReactiveReentrancyException reported instead of a stack overflow, depending on platform
    // and the exact shape of the graph). Once the recursive read in `IssueNode.issues` is fixed to not
    // grow the stack with graph depth, these should start failing (no exception thrown) - at which
    // point `deepGraphDepth` can be folded into `chainDepths` above instead.
    private val deepGraphDepth = 300

    @Test fun deeplyChainedInlineValidationGraphCurrentlyFailsToRead() {
        assertFailsWith<Throwable>("Expected reading issues() on a depth-$deepGraphDepth graph to currently fail") {
            testContext {
                val root = Signal(nestedObject(deepGraphDepth)).validated()
                val leaf = buildInlineObjectChain(root, deepGraphDepth)

                val context = reactive { rerunOn(leaf) }

                launch {
                    root.issues()
                }

                context.cancel()
            }
        }
    }

    @Test fun deeplyChainedReactiveValidationGraphCurrentlyFailsToRead() {
        assertFailsWith<Throwable>("Expected reading issues() on a depth-$deepGraphDepth graph to currently fail") {
            testContext {
                val root = Signal(nestedObject(deepGraphDepth)).validated()
                val leaf = buildReactiveObjectChain(root, deepGraphDepth)

                val context = reactive { rerunOn(leaf) }

                launch {
                    root.issues()
                }

                context.cancel()
            }
        }
    }
}
