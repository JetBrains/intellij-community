// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.docstings

import com.intellij.idea.TestFor
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.TextRange
import com.intellij.psi.LanguageInjector
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.restructuredtext.psi.RestLine
import com.intellij.restructuredtext.python.PyRestDocstringLanguageInjector
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.documentation.doctest.PyDocstringCodeBlockLanguageDialect
import com.jetbrains.python.documentation.doctest.PyDocstringLanguageInjector
import com.jetbrains.python.documentation.doctest.PyDoctestExamples
import com.jetbrains.python.documentation.doctest.PyDoctestLanguageDialect
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFunction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Subsystems.QuickDocumentation
@Components.Docstrings
@Layers.Functional
@TestFor(
  issues = ["PY-94085"],
  classes = [PyDocstringLanguageInjector::class, PyRestDocstringLanguageInjector::class, PyDoctestExamples::class],
)
class PyDoctestInjectionTest : PyCodeInsightTestCase() {

  @Test
  fun `docstring example with prompted backslash continuations is injected once`() {
    assertEquals(
      listOf(
        """
          >>> styled = df.style.\
          ...     applymap(lambda val: 'red' if val < 0 else 'black').\
          ...     highlight_max()
          >>> styled.to_excel('styled.xlsx')
        """.trimIndent()
      ),
      docstringDoctestTexts("""
        def f():
            '''
            >>> styled = df.style.\
            ...     applymap(lambda val: 'red' if val < 0 else 'black').\
            ...     highlight_max()
            >>> styled.to_excel('styled.xlsx')
            '''
      """.trimIndent())
    )
  }

  @Test
  fun `docstring example with bare backslash continuations is injected once`() {
    assertEquals(
      listOf(
        """
          >>> x = 1 + \
          2 + \
          3
          >>> x
        """.trimIndent()
      ),
      docstringDoctestTexts("""
        def f():
            '''
            >>> x = 1 + \
            2 + \
            3
            >>> x
            6
            '''
      """.trimIndent())
    )
  }

  @Test
  fun `raw docstring example ends before a bare line after a backslash`() {
    assertEquals(
      listOf(""">>> x = 1 + \"""),
      docstringDoctestTexts("""
        def f():
            r'''
            >>> x = 1 + \
            2
            '''
      """.trimIndent())
    )
  }

  @Test
  fun `docstring example ends before a bare line after an escaped backslash`() {
    assertEquals(
      listOf(""">>> x  # C:\\"""),
      docstringDoctestTexts("""
        def f():
            '''
            >>> x  # C:\\
            1
            '''
      """.trimIndent())
    )
  }

  @Test
  fun `docstring example ends before a bare line after a backslash and spaces`() {
    assertEquals(
      listOf(""">>> x = 1 + \  """),
      docstringDoctestTexts("""
        def f():
            '''
            >>> x = 1 + \${"  "}
            2
            '''
      """.trimIndent())
    )
  }

  @Test
  fun `docstring line after a backslash starts no example`() {
    assertEquals(
      emptyList<String>(),
      docstringDoctestTexts("""
        def f():
            'Doc.\
        >>> x = 1 + \
        2'
      """.trimIndent())
    )
  }

  @Test
  fun `backslash joins lines only in the non-raw part of a docstring`() {
    assertEquals(
      listOf(
        """>>> a = 1 + \""",
        """
          >>> x = 1 + \
          2
        """.trimIndent(),
      ),
      docstringDoctestTexts("""
        def f():
            r'''
            >>> a = 1 + \
            3
            ''' '''
            >>> x = 1 + \
            2
            '''
      """.trimIndent())
    )
  }

  @Test
  fun `docstring example after a backslash takes the directive line and keeps the code block apart`() {
    val injections = docstringInjections("""
      def f():
          '''
          >>> x = 1 + \
          .. code-block:: python

              y = 2
          '''
    """.trimIndent())
    assertEquals(
      listOf(
        """
          >>> x = 1 + \
          .. code-block:: python
        """.trimIndent()
      ),
      injections.doctests,
    )
    assertEquals(listOf("y = 2"), injections.codeBlocks)
  }

  @Test
  fun `docstring example after a backslash takes the fence line and keeps the fence content apart`() {
    val injections = docstringInjections("""
      def f():
          '''
          >>> x = 1 + \
          ```py
          y = 2
          ```
          '''
    """.trimIndent())
    assertEquals(
      listOf(
        """
          >>> x = 1 + \
          ```py
        """.trimIndent()
      ),
      injections.doctests,
    )
    assertEquals(listOf("y = 2"), injections.codeBlocks)
  }

  @Test
  fun `rest example with prompted backslash continuations is injected once`() {
    assertEquals(
      listOf(
        """
          >>> styled = df.style.\
          ...     applymap(lambda val: 'red' if val < 0 else 'black').\
          ...     highlight_max()
          >>> styled.to_excel('styled.xlsx')
        """.trimIndent()
      ),
      restDoctestTexts("""
        Title
        =====

        .. code-block:: pycon

           >>> styled = df.style.\
           ...     applymap(lambda val: 'red' if val < 0 else 'black').\
           ...     highlight_max()
           >>> styled.to_excel('styled.xlsx')

        After.
      """.trimIndent())
    )
  }

  @Test
  fun `rest example ends before a bare line after a backslash`() {
    assertEquals(
      listOf(""">>> x = total\"""),
      restDoctestTexts("""
        Title
        =====

        .. code-block:: pycon

           >>> x = total\
           done

        After.
      """.trimIndent())
    )
  }

  private class Injections(val doctests: List<String>, val codeBlocks: List<String>)

  private fun docstringDoctestTexts(text: String): List<String> = docstringInjections(text).doctests

  private fun docstringInjections(text: String): Injections {
    myFixture.configureByText("a.py", text)
    return runReadActionBlocking {
      val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!
      injections(PyDocstringLanguageInjector(), function.docStringExpression!!)
    }
  }

  private fun restDoctestTexts(text: String): List<String> {
    myFixture.configureByText("a.rst", text)
    return runReadActionBlocking {
      PsiTreeUtil.findChildrenOfType(myFixture.file, RestLine::class.java).flatMap {
        injections(PyRestDocstringLanguageInjector(), it).doctests
      }
    }
  }

  /**
   * Records the places of [injector] directly, because the injection registrar drops a window that overlaps another one.
   * Also checks that each file that the registrar makes for [host] is valid.
   */
  private fun injections(injector: LanguageInjector, host: PsiLanguageInjectionHost): Injections {
    val places = mutableListOf<Pair<Language, TextRange>>()
    injector.getLanguagesToInject(host) { language, range, _, _ -> places.add(language to range) }
    for ((a, b) in places.map { it.second }.sortedBy { it.startOffset }.zipWithNext()) {
      assertTrue(a.endOffset <= b.startOffset, "Places $a and $b overlap")
    }
    InjectedLanguageManager.getInstance(host.project).getInjectedPsiFiles(host).orEmpty().forEach {
      assertTrue(it.first.isValid)
    }
    fun texts(language: Language) = places.filter { it.first == language }.map { it.second.substring(host.text).trimIndent() }
    return Injections(texts(PyDoctestLanguageDialect.getInstance()), texts(PyDocstringCodeBlockLanguageDialect.getInstance()))
  }
}
