// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.performance

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.PyCodeInsightCounters
import com.jetbrains.python.codeInsight.PyCodeInsightCounters.Counter
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
@TestFor(classes = [PyCodeInsightCounters::class])
class PyCodeInsightCountersTest : PyCodeInsightTestCase() {

  @Test
  fun `counters record type evaluations, cache hits and control flow only while enabled`() {
    val file = myFixture.configureByText("a.py", """
      def f(x: int) -> list[int]:
          return [x]

      for i in range(3):
          y = f(i)
      y
    """.trimIndent())
    val disposable = Disposer.newDisposable()
    try {
      PyCodeInsightCounters.enable(disposable)
      assertTrue(PyCodeInsightCounters.isEnabled)
      val before = PyCodeInsightCounters.snapshot()
      runReadActionBlocking {
        val context = TypeEvalContext.codeAnalysis(myFixture.project, file)
        val y = PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java).last { it.text == "y" }
        assertEquals("list[int]", PythonDocumentationProvider.getTypeName(context.getType(y), context))
        context.getType(y)
      }
      val delta = PyCodeInsightCounters.snapshot() - before
      assertTrue(delta[Counter.GET_TYPE_EVALUATIONS] > 0, "evaluations: $delta")
      assertTrue(delta[Counter.GET_TYPE_CACHE_HITS] >= 1, "cache hits: $delta")
      assertTrue(delta[Counter.CFG_BUILDS] >= 1, "control flow builds: $delta")
      assertTrue(delta[Counter.CFG_INSTRUCTIONS] > delta[Counter.CFG_BUILDS], "control flow instructions: $delta")
    }
    finally {
      Disposer.dispose(disposable)
    }
    assertFalse(PyCodeInsightCounters.isEnabled)

    val idle = PyCodeInsightCounters.snapshot()
    runReadActionBlocking {
      val context = TypeEvalContext.codeAnalysis(myFixture.project, file)
      PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java).forEach { context.getType(it) }
    }
    assertEquals(idle, PyCodeInsightCounters.snapshot())
  }

  @Test
  fun `nested enablers keep the counting on until the outer one is disposed`() {
    val outer = Disposer.newDisposable("outer enabler")
    try {
      PyCodeInsightCounters.enable(outer)
      PyCodeInsightCounters.countOnCurrentThread { assertTrue(PyCodeInsightCounters.isEnabled) }
      assertTrue(PyCodeInsightCounters.isEnabled)
      val inner = Disposer.newDisposable("inner enabler")
      PyCodeInsightCounters.enable(inner)
      Disposer.dispose(inner)
      assertTrue(PyCodeInsightCounters.isEnabled)
    }
    finally {
      Disposer.dispose(outer)
    }
    assertFalse(PyCodeInsightCounters.isEnabled)
  }
}
