// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.codeInsight.hint.ShowExpressionTypeHandler
import com.intellij.idea.TestFor
import com.intellij.lang.ExpressionTypeProvider
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A long type in quick documentation and in the type info hint is broken into lines that fit in 80 columns.
 * The type info hint highlights the type, but has no links.
 */
@Subsystems.QuickDocumentation
@Layers.Functional
@TestFor(classes = [PythonDocumentationProvider::class])
class PyMultilineTypeRenderingTest : PyCodeInsightTestCase() {

  @Test
  fun `short type stays on one line`() {
    doTestMultilineTypeName("list[int]", """
      def f(x: list[int]):
          expr = x
      """)
  }

  @Test
  fun `type arguments go on separate lines`() {
    doTestMultilineTypeName("""
      dict[
          tuple[Aaaaaaaaaaaaaaaaaaaa, Bbbbbbbbbbbbbbbbbbbb],
          tuple[Cccccccccccccccccccc, Dddddddddddddddddddd],
      ]
      """, """
      def f(x: dict[tuple[Aaaaaaaaaaaaaaaaaaaa, Bbbbbbbbbbbbbbbbbbbb], tuple[Cccccccccccccccccccc, Dddddddddddddddddddd]]):
          expr = x
      """)
  }

  @Test
  fun `single type argument stays next to the brackets`() {
    doTestMultilineTypeName("""
      list[dict[
          tuple[Aaaaaaaaaaaaaaaaaaaa, Bbbbbbbbbbbbbbbbbbbb],
          tuple[Cccccccccccccccccccc, Dddddddddddddddddddd],
      ]]
      """, """
      def f(x: list[dict[tuple[Aaaaaaaaaaaaaaaaaaaa, Bbbbbbbbbbbbbbbbbbbb], tuple[Cccccccccccccccccccc, Dddddddddddddddddddd]]]):
          expr = x
      """)
  }

  @Test
  fun `union members fill the line`() {
    doTestMultilineTypeName("""
      Aaaaaaaaaaaaaaaaaaaa | Bbbbbbbbbbbbbbbbbbbb | Cccccccccccccccccccc
          | Dddddddddddddddddddd
      """, """
      def f(x: Aaaaaaaaaaaaaaaaaaaa | Bbbbbbbbbbbbbbbbbbbb | Cccccccccccccccccccc | Dddddddddddddddddddd):
          expr = x
      """)
  }

  @Test
  fun `literal values fill the line`() {
    doTestMultilineTypeName("""
      Literal[
          1000, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011,
          1012, 1013, 1014, 1015, 1016, 1017, 1018, 1019,
      ]
      """, """
      from typing import Literal

      def f(x: Literal[1000, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012, 1013, 1014, 1015, 1016, 1017, 1018, 1019]):
          expr = x
      """)
  }

  @Test
  fun `overloads go on separate lines`() {
    doTestMultilineTypeName("""
      Overload[
          (x: int, y: Aaaaaaaaaaaaaaaaaaaa) -> Bbbbbbbbbbbbbbbbbbbb,
          (x: str, y: Cccccccccccccccccccc) -> Dddddddddddddddddddd,
      ]
      """, """
      from typing import overload

      @overload
      def g(x: int, y: Aaaaaaaaaaaaaaaaaaaa) -> Bbbbbbbbbbbbbbbbbbbb: ...
      @overload
      def g(x: str, y: Cccccccccccccccccccc) -> Dddddddddddddddddddd: ...
      def g(x, y): ...

      expr = g
      """)
  }

  @Test
  fun `callable parameters go on separate lines`() {
    doTestMultilineTypeName("""
      (
          first: Aaaaaaaaaaaaaaaaaaaa,
          second: Bbbbbbbbbbbbbbbbbbbb,
          third: Cccccccccccccccccccc,
      ) -> Dddddddddddddddddddd
      """, """
      def g(first: Aaaaaaaaaaaaaaaaaaaa, second: Bbbbbbbbbbbbbbbbbbbb, third: Cccccccccccccccccccc) -> Dddddddddddddddddddd: ...

      expr = g
      """)
  }

  @Test
  fun `type name stays on one line`() {
    configure("""
      def f(x: dict[tuple[Aaaaaaaaaaaaaaaaaaaa, Bbbbbbbbbbbbbbbbbbbb], tuple[Cccccccccccccccccccc, Dddddddddddddddddddd]]):
          expr = x
      """)
    runReadActionBlocking {
      val expr = myFixture.findElementByText("expr", PyExpression::class.java)
      val context = TypeEvalContext.codeAnalysis(expr.project, expr.containingFile)
      assertEquals("dict[tuple[Aaaaaaaaaaaaaaaaaaaa, Bbbbbbbbbbbbbbbbbbbb], tuple[Cccccccccccccccccccc, Dddddddddddddddddddd]]",
                   PythonDocumentationProvider.getTypeName(context.getType(expr), context))
    }
  }

  @Test
  fun `type info hint has colours and no links`() {
    configure("""
      def f(x: list[int | None]):
          expr = x
      """)
    runReadActionBlocking {
      assertEquals("<span style=\"color:#000080;\">list</span><span style=\"\">[</span><span style=\"color:#000080;\">int</span>" +
                   "<span style=\"\"> | </span><span style=\"color:#000080;font-weight:bold;\">None</span><span style=\"\">]</span>",
                   typeInfoHint())
    }
  }

  @Test
  fun `quick documentation of a parameter shows the lines`() {
    configure("""
      def f(parameter: dict[tuple[Aaaaaaaaaaaaaaaaaaaa, Bbbbbbbbbbbbbbbbbbbb], tuple[Cccccccccccccccccccc, Dddddddddddddddddddd]]):
          pass
      """)
    runReadActionBlocking {
      val parameter = myFixture.findElementByText("parameter", PyNamedParameter::class.java)
      assertEquals("""
        Parameter parameter of mod.f
        parameter: dict[
            tuple[Aaaaaaaaaaaaaaaaaaaa, Bbbbbbbbbbbbbbbbbbbb],
            tuple[Cccccccccccccccccccc, Dddddddddddddddddddd],
        ]
        """.trimIndent(), quickDocumentationDefinition(PythonDocumentationProvider().generateDoc(parameter, parameter)!!))
    }
  }

  @Test
  fun `type after a prefix past the width stays on one line`() {
    val name = "parameter_with_a_name_that_takes_the_whole_width_of_the_quick_documentation_popup"
    configure("""
      def f($name: dict[str, int]):
          pass
      """)
    runReadActionBlocking {
      val parameter = myFixture.findElementByText(name, PyNamedParameter::class.java)
      assertEquals("""
        Parameter $name of mod.f
        $name: dict[str, int]
        """.trimIndent(), quickDocumentationDefinition(PythonDocumentationProvider().generateDoc(parameter, parameter)!!))
    }
  }

  @Test
  fun `quick documentation of a function aligns the lines with the parameters`() {
    configure("""
      def function(first: int, second: dict[tuple[Aaaaaaaaaaaaaaaaaaaa, Bbbbbbbbbbbbbbbbbbbb], tuple[Cccccccccccccccccccc, Dddddddddddddddddddd]]) -> None:
          pass
      """)
    runReadActionBlocking {
      val function = myFixture.findElementByText("function", PyFunction::class.java)
      assertEquals("""
        def function(first: int,
                     second: dict[
                         tuple[Aaaaaaaaaaaaaaaaaaaa, Bbbbbbbbbbbbbbbbbbbb],
                         tuple[Cccccccccccccccccccc, Dddddddddddddddddddd],
                     ]) -> None
        """.trimIndent(), quickDocumentationDefinition(PythonDocumentationProvider().generateDoc(function, function)!!))
    }
  }

  private fun doTestMultilineTypeName(expected: String, text: String) {
    configure(text)
    runReadActionBlocking {
      val hint = typeInfoHint().replace("<br/>", "\n").replace("&nbsp;", " ")
      assertEquals(expected.trimIndent(), StringUtil.unescapeXmlEntities(StringUtil.stripHtml(hint, false)))
    }
  }

  private fun typeInfoHint(): String {
    val expr = myFixture.findElementByText("expr", PyExpression::class.java)
    @Suppress("UNCHECKED_CAST")
    val provider = ShowExpressionTypeHandler.getHandlers(myFixture.project, PythonLanguage.INSTANCE).single() as ExpressionTypeProvider<PyExpression>
    return provider.getInformationHint(expr)
  }

  private fun configure(text: String) {
    myFixture.configureByText("mod.py", CLASSES + "\n\n" + text.trimIndent())
  }

  private fun quickDocumentationDefinition(html: String): String {
    val definition = html.substringAfter("<pre>").substringBefore("</pre>").replace("<br/>", "\n")
    return StringUtil.unescapeXmlEntities(StringUtil.stripHtml(definition, false))
  }

  private companion object {
    val CLASSES = """
      class Aaaaaaaaaaaaaaaaaaaa: ...
      class Bbbbbbbbbbbbbbbbbbbb: ...
      class Cccccccccccccccccccc: ...
      class Dddddddddddddddddddd: ...
      """.trimIndent()
  }
}
