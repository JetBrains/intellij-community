// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTypeAliasStatement
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-93010"], classes = [PythonDocumentationProvider::class])
@Subsystems.QuickDocumentation
@Layers.Functional
class PyTypeAliasStatementQuickDocTest : PyCodeInsightTestCase() {

  @Test
  fun `type alias statement of a class shows the class`() = doTest("""
    type Bar = int
  """, "type Bar = int")

  @Test
  fun `type alias statement of a Callable shows the callable type`() = doTest("""
    from typing import Callable

    type Foo = Callable[[], None]
  """, "type Foo = () -> None")

  @Test
  fun `generic type alias statement shows the parameterized type`() = doTest("""
    type Pair[T] = tuple[T, T]
  """, "type Pair[out T] = tuple[T, T]")

  @Test
  fun `recursive type alias statement terminates`() = doTest(
    "type J = list[J] | int",
    // TODO: type J = list[J] | int
    "type J = list[list[Unknown] | int] | int")

  @Test
  fun `type alias statement of a string forward reference shows the class`() = doTest("""
    type X = "Foo"

    class Foo: ...
  """, "type X = Foo")

  private fun doTest(@Language("Python") text: String, expectedDefinition: String) {
    val file = myFixture.configureByText("a.py", text.trimIndent()) as PyFile
    val (quickDoc, hover) = runReadActionBlocking {
      val typeAlias = PsiTreeUtil.findChildrenOfType(file, PyTypeAliasStatement::class.java).single()
      val provider = PythonDocumentationProvider()
      provider.generateDoc(typeAlias, typeAlias) to provider.getQuickNavigateInfo(typeAlias, typeAlias)
    }
    assertEquals(expectedDefinition, plainText(quickDoc).lines().last())
    assertEquals(expectedDefinition, plainText(hover))
  }

  private fun plainText(html: String?): String =
    StringUtil.unescapeXmlEntities(StringUtil.removeHtmlTags(html!!.replace("<br/>", "\n"))).trim()
}
