// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.idea.TestFor
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.unusedLocal.PyUnusedFunctionInspection
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-9687"])
class PyUnusedFunctionInspectionTest : PyCodeInsightTestCase() {

  /** Asserts the exact set of unused-function warnings (descriptions) reported for [code]. */
  private fun doTest(@Language("Python") code: String, vararg expectedMessages: String) {
    myFixture.configureByText(PythonFileType.INSTANCE, code.trimIndent())
    myFixture.enableInspections(PyUnusedFunctionInspection())
    val actual = myFixture.doHighlighting()
      .filter { it.severity == HighlightSeverity.WEAK_WARNING }
      .map { it.description }
      .sorted()
    Assertions.assertEquals(expectedMessages.sorted(), actual)
  }

  @Test
  fun `unused local function is reported`() {
    // The unused local variable 'a' belongs to PyUnusedLocal and is not enabled here.
    doTest("""
      def outer():
          a = 1
          def inner():
              return 1
          return a
    """, "Local function 'inner' is not used")
  }

  @Test
  fun `used local function is not reported`() {
    doTest("""
      def outer():
          def inner():
              return 1
          return inner()
    """)
  }

  @Test
  fun `noinspection unused-function suppresses the function`() {
    doTest("""
      def outer():
          # noinspection unused-function
          def inner():
              return 1
          return 0
    """)
  }
}
