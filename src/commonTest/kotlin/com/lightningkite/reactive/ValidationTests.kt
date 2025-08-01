package com.lightningkite.reactive

import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.modify
import com.lightningkite.reactive.lensing.lensByElementWithIdentity
import com.lightningkite.reactive.lensing.validation.Issue
import com.lightningkite.reactive.lensing.validation.IssueNode
import com.lightningkite.reactive.lensing.validation.assert
import com.lightningkite.reactive.lensing.validation.issues
import com.lightningkite.reactive.lensing.validation.validate
import com.lightningkite.reactive.lensing.validation.validated
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.Test
import kotlin.test.assertEquals

class ValidationTests {
    data class Data(
        val id: Int = 0,
        val number: Double = 0.0,
        val name: String = ""
    )

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
}