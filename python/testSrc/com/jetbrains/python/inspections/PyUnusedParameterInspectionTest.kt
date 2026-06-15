// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.idea.TestFor
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.unusedLocal.PyUnusedParameterInspection
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-9687"])
class PyUnusedParameterInspectionTest : PyCodeInsightTestCase() {

  /** Asserts the exact set of unused-parameter warnings (descriptions) reported for [code]. */
  private fun doTest(@Language("Python") code: String, vararg expectedMessages: String) {
    myFixture.configureByText(PythonFileType.INSTANCE, code.trimIndent())
    myFixture.enableInspections(PyUnusedParameterInspection())
    val actual = myFixture.doHighlighting()
      .filter { it.severity == HighlightSeverity.WEAK_WARNING }
      .map { it.description }
      .sorted()
    Assertions.assertEquals(expectedMessages.sorted(), actual)
  }

  @Test
  fun `unused parameter is reported`() {
    // The unused local 'a' belongs to PyUnusedLocal and is not enabled here.
    doTest("""
      def f(x):
          a = 1
    """, "Parameter 'x' value is not used")
  }

  @Test
  fun `self is not reported but a regular parameter is`() {
    doTest("""
      class A:
          def m(self, x):
              return 1
    """, "Parameter 'x' value is not used")
  }

  @Test
  fun `lambda parameter is not reported`() {
    doTest("""
      f = lambda x: 1
      print(f)
    """)
  }

  @Test
  fun `noinspection unused-parameter suppresses the parameter`() {
    doTest("""
      # noinspection unused-parameter
      def f(x):
          return 1
    """)
  }

  @Test
  fun `legacy PyUnusedLocal does not suppress the parameter`() {
    // Clean break: PyUnusedLocal no longer silences parameters.
    doTest("""
      # noinspection PyUnusedLocal
      def f(x):
          return 1
    """, "Parameter 'x' value is not used")
  }
}
