// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.showcase

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.junit5.framework.annotations.InjectCodeInsightTestFixture
import com.jetbrains.python.junit5.framework.annotations.PyCodeInsightTestApplication
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Test

@TestClassInfo(Repository.PY_COMMUNITY)
@PyCodeInsightTestApplication
class PyJUnit5TypeEvalExampleTest {

  @InjectCodeInsightTestFixture
  lateinit var codeInsightFixture: CodeInsightTestFixture

  @Test
  fun testBytesLiteral() {
    doTest("bytes", "expr = b'foo'")
  }

  @Test
  fun testBuiltinRound() {
    doTest("int", "expr = round(1)")
  }

  @Test
  fun testMethodCallReturnType() {
    doTest("str",
      """
      class MyClass:
          def foo(self) -> str:
              return "hello"

      expr = MyClass().foo()
      """.trimIndent()
    )
  }

  private fun doTest(expectedType: String, text: String) {
    codeInsightFixture.configureByText(PythonFileType.INSTANCE, text)
    val expr = runReadActionBlocking {
      codeInsightFixture.findElementByText("expr", PyExpression::class.java)
    }
    assertExpressionType(expectedType, expr)
  }

  private fun assertExpressionType(expectedType: String, expr: PyExpression) {
    runReadActionBlocking {
      val project = codeInsightFixture.project
      val containingFile = expr.containingFile
      PyTestCase.assertType(expectedType, expr, TypeEvalContext.codeAnalysis(project, containingFile))
      PyTestCase.assertType(expectedType, expr, TypeEvalContext.userInitiated(project, containingFile))
    }
  }
}
