// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.xdebugger.XDebuggerTestUtil
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueContainer
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
                        for ((path, expected) in expectations) {
                            val target = resolvePath(rootValues, path)
                                ?: error("Could not resolve variable path `$path` from frame children: " +
                                         rootValues.mapNotNull { (it as? XNamedValue)?.name })
                            val actual = target.calculateEvaluationExpression()
                                .blockingGet(XDebuggerTestUtil.TIMEOUT_MS)?.expression
                            assertEquals(
                                "Expected evaluation expression for variable path `$path` to look like: `$expected`, " +
                                        "and got: `$actual`",
                                expected, actual
                            )
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

    private fun parseExpectations(content: String): List<Pair<String, String>> {
        val paths = findLinesWithPrefixesRemoved(content, "// VARIABLE_PATH: ")
        val expressions = findLinesWithPrefixesRemoved(content, "// EVAL_EXPRESSION: ")
        check(paths.size == expressions.size) {
            "Mismatched directive counts: ${paths.size} `// VARIABLE_PATH:` vs " +
            "${expressions.size} `// EVAL_EXPRESSION:` (each path needs its expression on the next line)"
        }
        return paths.zip(expressions)
    }
}
