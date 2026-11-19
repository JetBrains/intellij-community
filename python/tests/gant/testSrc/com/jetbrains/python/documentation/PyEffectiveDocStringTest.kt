// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.pyi.PyiUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Subsystems.CodeInsight
@Layers.Functional
@TestFor(issues = ["PY-83660"])
class PyEffectiveDocStringTest : PyCodeInsightTestCase() {
  @TestDisposable
  lateinit var testDisposable: Disposable

  @Test
  fun `stub overload takes the docstring of another overload`() {
    var docstring: String? = null
    runInEdtAndWait {
      val file = myFixture.configureByText("mod.pyi", """
        from typing import overload

        @overload
        def f(x: int) -> int:
            ""${'"'}Docstring of the first overload.""${'"'}

        @overload
        def f(x: str) -> str: ...
        """.trimIndent()) as PyFile
      docstring = runReadAction { PyDocumentationBuilder.getEffectiveDocStringExpression(file.topLevelFunctions[1])?.stringValue }
    }
    assertThat(docstring).isEqualTo("Docstring of the first overload.")
  }

  @Test
  fun `implementation docstring wins over the bundled typeshed one`() {
    setAdditionalSdkRoots(LIB, true)
    Disposer.register(testDisposable) { setAdditionalSdkRoots(LIB, false) }
    val docstrings = effectiveDocStringOfFunction("""
      import ipaddress
      ipaddress.ip_address
      """)
    assertThat(docstrings.stub).isNotNull()
    assertThat(docstrings.effective).isEqualTo("Docstring of the interpreter.")
  }

  @Test
  fun `skeleton docstring does not win over the bundled typeshed one`() {
    createFileInSkeletonsDir("zlib.py", """
      def crc32(data, value=0):
          ""${'"'}Initialize self.  See help(type(self)) for accurate signature.""${'"'}
      """.trimIndent(), testDisposable)
    val docstrings = effectiveDocStringOfFunction("""
      import zlib
      zlib.crc32
      """)
    assertThat(docstrings.originalInSkeleton).isTrue()
    assertThat(docstrings.effective).startsWith("Compute a CRC-32 checksum of data.")
  }

  @Test
  fun `bundled typeshed has the runtime docstrings of compiled definitions`() {
    val docstrings = effectiveDocStringOfFunction("""
      import zlib
      zlib.crc32
      """)
    assertThat(docstrings.stub)
      .withFailMessage("Run community/python/tools/typeshed-docstrings/typeshed_docstrings.py after a typeshed update")
      .startsWith("Compute a CRC-32 checksum of data.")
  }

  private class Docstrings(val stub: String?, val effective: String?, val originalInSkeleton: Boolean)

  private fun effectiveDocStringOfFunction(code: String): Docstrings {
    var docstrings: Docstrings? = null
    runInEdtAndWait {
      val file = myFixture.configureByText("a.py", code.trimIndent()) as PyFile
      docstrings = runReadAction {
        val reference = (file.statements.last() as PyExpressionStatement).expression as PyReferenceExpression
        val stubFunction = reference.reference.resolve() as PyFunction
        val original = PyiUtil.getOriginalElement(stubFunction)
        Docstrings(stub = stubFunction.docStringValue,
                   effective = PyDocumentationBuilder.getEffectiveDocStringExpression(stubFunction)?.stringValue,
                   originalInSkeleton = original != null && PyiUtil.isInSkeleton(original))
      }
    }
    return docstrings!!
  }

  private companion object {
    val LIB = arrayOf(SdkRoot("documentation/EffectiveDocString/lib", OrderRootTypeEnum.CLASSES))
  }
}
