// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.performance

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PerformanceUnitTest
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.PyCodeInsightCounters
import com.jetbrains.python.codeInsight.PyCodeInsightCounters.Counter
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.TypeEvalContextImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Checks the type inference of a variable that sequential loops redefine:
 *
 * ```python
 * def f(xs: list[int]):
 *     x = 0
 *     for a in xs:
 *         x = x + a
 *     for a in xs:
 *         x = x + a
 *     ...
 *     return x
 * ```
 *
 * The read of `x` in `x + a` has a later definition in the same loop, so its inference runs a fixed point with assumed
 * types. The scenarios evaluate each read of `x`: the read in each loop and the read in `return x`. The gate runs in
 * the normal suite. The benchmark is a [PerformanceUnitTest].
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Performance
@TestFor(classes = [PyCodeInsightCounters::class, TypeEvalContextImpl::class])
class PyLoopFlowPerformanceTest : PyCodeInsightTestCase() {

  @JvmField
  @RegisterExtension
  val perf = PyPerfProbeExtension { myFixture }

  @Test
  fun `variable redefined in sequential loops grows linearly`() {
    for (counter in listOf(Counter.GET_TYPE_EVALUATIONS, Counter.ASSUME_TYPE_CALLS)) {
      assertGrowth("loops/${counter.id}", LOOPS, PyGrowth.LINEAR) { loops ->
        val (file, reads) = configureLoops(loops)
        perf.probe.work { inferAll(file, reads) }[counter]
      }
    }
  }

  @PerformanceUnitTest
  @Test
  fun `variable redefined in sequential loops benchmark`() {
    val (file, reads) = configureLoops(200)
    perf.probe.report(perf.probe.measureScaled("loops/n=200") { inferAll(file, reads) })
  }

  /** The function of the class documentation with [loops] loops, and each read of `x` in it. */
  private fun configureLoops(loops: Int): Pair<PyFile, List<PyReferenceExpression>> {
    val code = buildString {
      appendLine("""
        def f(xs: list[int]):
            x = 0
      """.trimIndent())
      repeat(loops) {
        appendLine("""
          for a in xs:
              x = x + a
        """.trimIndent().prependIndent("    "))
      }
      appendLine("    return x")
    }
    val file = myFixture.configureByText("loops_$loops.py", code) as PyFile
    val reads = runReadActionBlocking {
      PsiTreeUtil.findChildrenOfType(file.findTopLevelFunction("f"), PyReferenceExpression::class.java).filter { it.text == "x" }
    }
    assertEquals(loops + 1, reads.size)
    return file to reads
  }

  private fun inferAll(file: PyFile, reads: List<PyReferenceExpression>) {
    runReadActionBlocking {
      val context = TypeEvalContext.codeAnalysis(file.project, file)
      reads.forEach { context.getType(it) }
    }
  }

  private companion object {
    val LOOPS = listOf(25, 50, 100)
  }
}
