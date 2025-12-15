package com.lightningkite.reactive

import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactiveScope
import com.lightningkite.reactive.core.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests to ensure that using remember within reactiveScope doesn't cause duplicate invocations.
 *
 * These tests verify that remember calculations are only invoked when their dependencies change,
 * and not unnecessarily during reactiveScope re-executions.
 */
class RememberInReactiveScopeTest {

    @Test
    fun testRememberInReactiveScopeNoDuplicateInvocations() {
        val signal = Signal(1)
        var scopeInvocationCount = 0
        val invocationValues = mutableListOf<Int>()

        testContext {
            reactiveScope {
                scopeInvocationCount++
                val remembered = remember { signal() * 2 }
                val value = remembered.invoke()
                invocationValues.add(value)
            }

            // Should only invoke once on initial setup
            assertEquals(1, scopeInvocationCount, "Scope should only be invoked once initially")
            assertEquals(listOf(2), invocationValues)

            // Change the signal
            signal.value = 2

            // Should only invoke once per signal change
            assertEquals(2, scopeInvocationCount, "Scope should only be invoked once per change")
            assertEquals(listOf(2, 4), invocationValues)

            // Another change
            signal.value = 3

            assertEquals(3, scopeInvocationCount, "Scope should only be invoked once per change")
            assertEquals(listOf(2, 4, 6), invocationValues)
        }
    }

    @Test
    fun testNestedRememberInReactiveScopeNoDuplicates() {
        val signal = Signal(1)
        var outerScopeCount = 0
        var innerRememberCalculations = 0
        val scopeValues = mutableListOf<Pair<Int, Int>>()

        testContext {
            reactiveScope {
                outerScopeCount++

                // First remember
                val doubled = remember {
                    innerRememberCalculations++
                    signal() * 2
                }

                // Second remember that depends on the first
                val quadrupled = remember { doubled() * 2 }

                scopeValues.add(doubled() to quadrupled())
            }

            // Initial invocation
            assertEquals(1, outerScopeCount, "Outer scope should only be invoked once initially")
            assertEquals(1, innerRememberCalculations, "Inner remember should calculate once initially")
            assertEquals(listOf(2 to 4), scopeValues)

            // Change signal
            signal.value = 2

            assertEquals(2, outerScopeCount, "Outer scope should only be invoked once per signal change")
            assertEquals(2, innerRememberCalculations, "Inner remember should calculate once per signal change")
            assertEquals(listOf(2 to 4, 4 to 8), scopeValues)
        }
    }

    @Test
    fun testRememberAccessedMultipleTimesInSameScope() {
        val signal = Signal(1)
        var scopeCount = 0
        var rememberCalculations = 0
        val values = mutableListOf<Triple<Int, Int, Int>>()

        testContext {
            reactiveScope {
                scopeCount++

                val remembered = remember {
                    rememberCalculations++
                    signal() * 2
                }

                // Access the remembered value multiple times in the same scope
                val first = remembered()
                val second = remembered()
                val third = remembered()

                values.add(Triple(first, second, third))
            }

            // Should only invoke once initially
            assertEquals(1, scopeCount, "Scope should only be invoked once")
            assertEquals(1, rememberCalculations, "Remember should only calculate once")
            assertEquals(listOf(Triple(2, 2, 2)), values)

            signal.value = 5

            assertEquals(2, scopeCount, "Scope should only be invoked once per change")
            assertEquals(2, rememberCalculations, "Remember should calculate once per change")
            assertEquals(listOf(Triple(2, 2, 2), Triple(10, 10, 10)), values)
        }
    }

    @Test
    fun testConditionalRememberInReactiveScope() {
        val useExpensive = Signal(false)
        val value = Signal(10)
        var scopeCount = 0
        var cheapCalculations = 0
        var expensiveCalculations = 0
        val results = mutableListOf<Int>()

        testContext {
            reactiveScope {
                scopeCount++

                val result = if (useExpensive()) {
                    val expensive = remember {
                        expensiveCalculations++
                        value() * 100
                    }
                    expensive()
                } else {
                    val cheap = remember {
                        cheapCalculations++
                        value() * 2
                    }
                    cheap()
                }

                results.add(result)
            }

            // Initially using cheap path
            assertEquals(1, scopeCount, "Scope should only be invoked once initially")
            assertEquals(1, cheapCalculations, "Cheap calculation should run once")
            assertEquals(0, expensiveCalculations, "Expensive calculation should not run")
            assertEquals(listOf(20), results)

            // Switch to expensive
            useExpensive.value = true

            assertEquals(2, scopeCount, "Scope should be invoked once for the switch")
            assertEquals(1, cheapCalculations, "Cheap calculation count unchanged")
            assertEquals(1, expensiveCalculations, "Expensive calculation should run once")
            assertEquals(listOf(20, 1000), results)

            // Change value while using expensive
            value.value = 5

            assertEquals(3, scopeCount, "Scope should be invoked once for value change")
            assertEquals(2, expensiveCalculations, "Expensive calculation should run once per value change")
            assertEquals(listOf(20, 1000, 500), results)
        }
    }

    @Test
    fun testMultipleIndependentRemembersInScope() {
        val signalA = Signal(1)
        val signalB = Signal(10)
        var scopeCount = 0
        var rememberACalculations = 0
        var rememberBCalculations = 0
        val values = mutableListOf<Pair<Int, Int>>()

        testContext {
            reactiveScope {
                scopeCount++

                val rememberedA = remember {
                    rememberACalculations++
                    signalA() * 2
                }

                val rememberedB = remember {
                    rememberBCalculations++
                    signalB() * 3
                }

                values.add(rememberedA() to rememberedB())
            }

            // Initial
            assertEquals(1, scopeCount, "Scope invoked once initially")
            assertEquals(1, rememberACalculations, "RememberA calculated once")
            assertEquals(1, rememberBCalculations, "RememberB calculated once")
            assertEquals(listOf(2 to 30), values)

            // Change signalA only
            signalA.value = 5

            // Scope re-runs but only rememberA recalculates
            assertEquals(2, scopeCount, "Scope invoked once for signalA change")
            assertEquals(2, rememberACalculations, "RememberA should calculate once per change")
            assertEquals(1, rememberBCalculations, "RememberB should not recalculate when signalA changes")
            assertEquals(listOf(2 to 30, 10 to 30), values)

            // Change signalB only
            signalB.value = 7

            assertEquals(3, scopeCount, "Scope invoked once for signalB change")
            assertEquals(2, rememberACalculations, "RememberA should not recalculate when signalB changes")
            assertEquals(2, rememberBCalculations, "RememberB should calculate once per change")
            assertEquals(listOf(2 to 30, 10 to 30, 10 to 21), values)
        }
    }

    @Test
    fun testRememberInReactiveScopeWithNoActualDependencies() {
        var scopeCount = 0
        var rememberCalculations = 0

        testContext {
            reactiveScope {
                scopeCount++

                // Remember with no reactive dependencies
                val constant = remember {
                    rememberCalculations++
                    42
                }

                constant()
            }

            // Should invoke once
            assertEquals(1, scopeCount, "Scope should only be invoked once")
            assertEquals(1, rememberCalculations, "Remember should calculate once")

            // Since there are no reactive dependencies, the scope shouldn't re-run
            // This test just validates initial behavior is correct
        }
    }

    @Test
    fun testReactiveScopeWithRememberReturningReactiveValue() {
        val signal = Signal(1)
        var scopeCount = 0
        var rememberCreations = 0
        val values = mutableListOf<Int>()

        testContext {
            reactiveScope {
                scopeCount++

                // Remember that creates a computation based on signal
                val remembered = remember {
                    rememberCreations++
                    signal() * 2
                }

                // Access the remembered value
                values.add(remembered())
            }

            assertEquals(1, scopeCount, "Scope should only be invoked once initially")
            assertEquals(1, rememberCreations, "Remember should only create once initially")
            assertEquals(listOf(2), values)

            signal.value = 3

            assertEquals(2, scopeCount, "Scope should only be invoked once per signal change")
            assertEquals(2, rememberCreations, "Remember should calculate once per signal change")
            assertEquals(listOf(2, 6), values)
        }
    }
}
