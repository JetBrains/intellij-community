// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.JavaValue
import com.intellij.xdebugger.XDebuggerTestUtil
import com.intellij.xdebugger.XTestEvaluationCallback
import com.intellij.xdebugger.XTestValueNode
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueContainer
import com.intellij.xdebugger.frame.XValuePlace
import com.sun.jdi.ArrayReference
import com.sun.jdi.StringReference
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils.findLinesWithPrefixesRemoved
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import org.jetbrains.kotlin.idea.debugger.test.util.KotlinOutputChecker
import org.jetbrains.kotlin.idea.debugger.test.util.iterator

/**
 * Asserts the text returned by [XValue.calculateEvaluationExpression] for a
 * named node in the Variables view — e.g. the text that fills the Evaluate Expression dialog
 * when that node is selected.
 * ```
 * // VARIABLE_PATH: foo.bar
 * // EVAL_EXPRESSION: foo.bar
 *
 * class Foo(val bar: String)
 *
 * fun main() {
 *     val foo = Foo("baz")
 *     //Breakpoint!
 *     println()
 * }
 * ```
 *
 * `VARIABLE_PATH` is a dot-separated chain of variable / field names rooted in the current stack frame
 * (e.g. `foo` or `foo.bar`). `EVAL_EXPRESSION` is the text the IDE is expected to fill the Evaluate Expression dialog with.
 * If `EVAL_RESULT` is present for each path, the test also evaluates each expression and checks its value.
 * An evaluation error is shown as `error: <message>`.
 */
abstract class AbstractKotlinEvaluationExpressionTest : KotlinDescriptorTestCaseWithStepping() {
    private val capturedFailures = mutableListOf<Throwable>()

    override fun throwExceptionsIfAny() {
        if (capturedFailures.isNotEmpty()) {
            if (!isTestIgnored()) {
                throw AssertionError(
                    "Test failed with exceptions:\n${capturedFailures.map { it.message }.joinToString("\n")}"
                )
            } else {
                (checker as? KotlinOutputChecker)?.threwException = true
            }
        }
    }

    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        val expectations = parseExpectations(files.wholeFile.content)
        check(expectations.isNotEmpty()) {
            "No `// VARIABLE_PATH:` / `// EVAL_EXPRESSION:` directives found in ${files.wholeFile.name}"
        }
        for (i in 0..countBreakpointsNumber(files.wholeFile)) {
            doOnBreakpoint {
                processStackFramesOnPooledThread {
                    try {
                        val rootValues = XDebuggerTestUtil.collectChildren(first())
                        for ((path, expected, expectedResult) in expectations) {
                            val target = resolvePath(rootValues, path)
                                ?: error("Could not resolve variable path `$path` from frame children: " +
                                         rootValues.mapNotNull { (it as? XNamedValue)?.name })
                            // The Variables view calculates the value of a node before the user can evaluate the node.
                            XTestValueNode().also { target.computePresentation(it, XValuePlace.TREE) }
                                .waitFor(XDebuggerTestUtil.TIMEOUT_MS.toLong())
                            val expression = checkNotNull(target.calculateEvaluationExpression().blockingGet(XDebuggerTestUtil.TIMEOUT_MS))
                            val actual = expression.expression
                            assertEquals(
                                "Expected evaluation expression for variable path `$path` to look like: `$expected`, " +
                                        "and got: `$actual`",
                                expected, actual
                            )
                            if (expectedResult != null) {
                                var actualResult: String? = null
                                val callback = object : XTestEvaluationCallback() {
                                    override fun evaluated(result: XValue) {
                                        val value = (result as JavaValue).descriptor.value
                                        actualResult = when (value) {
                                            is StringReference -> value.value()
                                            is ArrayReference -> value.values.joinToString(prefix = "[", postfix = "]")
                                            else -> value.toString()
                                        }
                                        super.evaluated(result)
                                    }
                                }
                                checkNotNull(first().evaluator).evaluate(expression, callback, first().sourcePosition)
                                val result = callback.waitFor(XDebuggerTestUtil.TIMEOUT_MS.toLong())
                                val error = result.second
                                assertEquals("Wrong value for `$actual`", expectedResult, if (error != null) "error: $error" else actualResult)
                            }
                        }
                    } catch (t: Throwable) {
                        capturedFailures.add(t)
                    } finally {
                        resume(this@doOnBreakpoint)
                    }
                }
            }
        }
    }

    private fun resolvePath(roots: List<XValue>, path: String): XValue? {
        val variables = path.split('.')
        var current: XValue = roots.firstOrNull { (it as? XNamedValue)?.name == variables[0] } ?: return null
        for (v in variables.drop(1)) {
            current = (current as XValueContainer).iterator().asSequence()
                .filterIsInstance<XValue>()
                .firstOrNull { (it as? XNamedValue)?.name == v } ?: return null
        }
        return current
    }

    private data class Expectation(val path: String, val expression: String, val result: String?)

    private fun parseExpectations(content: String): List<Expectation> {
        val paths = findLinesWithPrefixesRemoved(content, "// VARIABLE_PATH: ")
        val expressions = findLinesWithPrefixesRemoved(content, "// EVAL_EXPRESSION: ")
        val results = findLinesWithPrefixesRemoved(content, "// EVAL_RESULT: ")
        check(paths.size == expressions.size) {
            "Mismatched directive counts: ${paths.size} `// VARIABLE_PATH:` vs " +
            "${expressions.size} `// EVAL_EXPRESSION:` (each path needs its expression on the next line)"
        }
        check(results.isEmpty() || results.size == paths.size) {
            "An `EVAL_RESULT` directive must exist for each variable path"
        }
        return paths.indices.map { Expectation(paths[it], expressions[it], results.getOrNull(it)) }
    }
}
